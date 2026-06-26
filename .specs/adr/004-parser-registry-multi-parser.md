# ADR-004: FileParserRegistry 多解析器 per extension 增强

- **状态**: accepted
- **日期**: 2026-06-18
- **Change**: `extensible-file`
- **决策者**: AI Architect + 人工 review

---

## Context

当前 `FileParserRegistry` 使用 `Map<String, FileParser> parserByExtension`，每个文件扩展名只能注册一个解析器。`MinerUDocumentParser` 和 `PdfBoxDocumentParser` 都返回 `Set.of(".pdf")`，在 Registry 初始化时后者静默覆盖前者，仅打印 WARN 日志。结果：Registry 中只剩一个 PDF 解析器，无法表达"MinerU 优先 + PDFBox 兜底"的解析策略。

当前方案绕过 Registry 直接注入两个解析器到 `FileServiceImpl`，在 `process()` 方法中手工编排 try-catch fallback（line 161-201）。这种做法：
1. 解析器选择逻辑硬编码在 Service 中，无法复用
2. 新增文档类型（TXT）时需修改 Service
3. Registry 形同虚设，只对 CSV 路由有效

需求要求 `DocumentProcessingPipeline` 从 Registry 获取解析器列表，遍历执行（主解析器优先，失败则尝试下一个）。

## Decision

将 `FileParserRegistry` 内部存储从 `Map<String, FileParser>` 改为 `Map<String, List<FileParser>>`，支持同一扩展名注册多个解析器。新增优先级机制：

```java
// FileParser 接口新增 default 方法
public interface FileParser {
    FileParseType supportedType();
    Set<String> supportedExtensions();
    <T> FileParseResult<T> parse(FileParseRequest request);
    
    /** 解析优先级，数值越小优先级越高。默认 0（最高）。 */
    default int priority() { return 0; }
}

// FileParserRegistry 增强
public class FileParserRegistry {
    // 旧：Map<String, FileParser>
    // 新：Map<String, List<FileParser>>（按 priority 排序）
    private final Map<String, List<FileParser>> parserByExtension;

    public FileParserRegistry(List<FileParser> allParsers) {
        // 按扩展名分组 → 同扩展名内按 priority 排序
        this.parserByExtension = allParsers.stream()
            .flatMap(p -> p.supportedExtensions().stream()
                .map(ext -> Map.entry(ext.toLowerCase(), p)))
            .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.mapping(Map.Entry::getValue,
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> list.stream()
                            .sorted(Comparator.comparingInt(FileParser::priority))
                            .toList()))));
    }

    /** 返回有序解析器列表（主解析器在前，兜底在后）。 */
    public List<FileParser> getParsers(String filename) { ... }

    /** 保持兼容：返回第一个（主）解析器。 */
    public Optional<FileParser> getParser(String filename) { ... }
}
```

### 解析器优先级配置

| 解析器 | 扩展名 | priority | 说明 |
|--------|--------|----------|------|
| `MinerUDocumentParser` | `.pdf` | 0（最高） | 主解析器 |
| `PdfBoxDocumentParser` | `.pdf` | 1（兜底） | MinerU 失败后使用 |
| `CsvGradeParser` | `.csv` | 0（默认） | 唯一 CSV 解析器 |
| `TxtFileParser`（新增） | `.txt` | 0（默认） | 唯一 TXT 解析器 |

### DocumentProcessingPipeline 使用方式

```
List<FileParser> parsers = registry.getParsers(filename);
for (FileParser parser : parsers) {
    try {
        result = parser.parse(request);
        break; // 成功则跳出
    } catch (Exception e) {
        lastError = e;
        // 继续尝试下一个
    }
}
if (result == null) {
    markFailed(lastError);
}
```

## Consequences

- **正面**：Registry 成为真正的解析器路由中心。Pipeline 不需要知道具体有哪些解析器，只需遍历列表
- **正面**：新增文件类型 + 多级兜底只需注册 Bean + 设 priority，无需修改 Pipeline 代码
- **正面**：`FileServiceImpl.process()` 中 ~60 行的 try-catch fallback 代码被 Pipeline 的 for 循环替代
- **正面**：`getParser()` 方法签名不变，现有 CSV 调用方（`GradeServiceImpl`、`FileController.upload()`）无需改动
- **负面**：`FileParser` 接口新增 `priority()` 方法，4 个实现类中 `PdfBoxDocumentParser` 需显式 override 为 1，其余 3 个用 default 0
- **负面**：优先级是解析器的固有属性，但两个 PDF 解析器不"感知"彼此——它们的 priority 值是独立设置的。如果未来有人新增第三个 PDF 解析器且设 priority=0，会与 MinerU 冲突。缓解：在 Registry 初始化时检测同 priority 冲突并 WARN
- **负面**：Registry 的初始化逻辑从简单的 Map.put 循环变为 stream + groupingBy + sorting，复杂度上升。但启动时只执行一次，性能影响可忽略