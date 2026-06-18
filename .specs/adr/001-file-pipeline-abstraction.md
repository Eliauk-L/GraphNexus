# ADR-001: FileProcessingPipeline 抽象

- **状态**: accepted
- **日期**: 2026-06-18
- **Change**: `extensible-file`
- **决策者**: AI Architect + 人工 review

---

## Context

当前文件上传链路硬编码在 Controller 和 Service 中：Controller 通过 `if (CSV) → gradeService else → documentService` 分支路由；Service 的 `upload()` 方法硬编码 `application/pdf` MIME 校验；`process()` 方法硬编码 MinerU → PDFBox 解析回退链。新增文件类型需修改 Controller + Service，违反开闭原则。

需求要求：
1. 新增文件类型只需添加解析器并注册，核心代码零改动
2. 文档类文件（PDF/TXT）走同步全链路（上传→解析→抽取→融合）
3. 成绩 CSV 走独立链路
4. 保留未来新增第三种处理链路的扩展能力

## Decision

定义 `FileProcessingPipeline` 接口，作为文件处理全链路的抽象契约：

```java
public interface FileProcessingPipeline {
    /**
     * 支持的文件类型。
     */
    FileParseType supportedType();

    /**
     * 执行全链路同步处理：上传 → 解析 → 入库 → 图谱 → 融合。
     * 返回时文件已处理完成或停留在中间失败状态。
     */
    Object process(MultipartFile file, String subject);

    /**
     * 从当前状态断点续跑至 COMPLETED。
     */
    Object retry(Long documentId);
}
```

Spring 自动注入所有 `FileProcessingPipeline` 实现，通过 `supportedType()` 路由。

### 实现类

| 实现 | supportedType | 职责 |
|------|-------------|------|
| `DocumentProcessingPipeline` | `DOCUMENT`（对应 PDF/TXT 及未来文档格式） | 同步串联 解析→LLM 抽取→融合，管理状态机 |
| `GradeProcessingPipeline` | `CSV_GRADE` | 封装既有 `GradeService.uploadGradeCsv()` 逻辑 |

### 可扩展性验证

未来新增文件类型（如 `.docx` + 全新处理链路）：

```
① 创建 DocxProcessingPipeline implements FileProcessingPipeline
② @Component 注册为 Spring Bean
③ FilePipelineRegistry 自动发现 → 无需改 Controller/Service
```

## Consequences

- **正面**：新增文件类型只需实现接口 + 注册，Controller 和 Service 核心代码零改动，满足开闭原则
- **正面**：Pipeline 封装了完整处理逻辑，Controller 只需一行 `pipelineRegistry.get(type).process(file, subject)`，职责清晰
- **正面**：与项目既有策略模式（`KpMatchingStrategy`、`SubgraphPruningStrategy`、`WeightCalculationStrategy`）风格一致，团队学习成本低
- **负面**：`process()` 返回 `Object`（Document Pipeline 返回 `FileVO`，Grade Pipeline 返回 `GradeUploadResultVO`），丢失编译期类型安全。可接受——Controller 层已知调用哪个 Pipeline，可做强制转型
- **负面**：Pipeline 内部是多步骤长方法，单元测试困难。缓解：各子步骤（parse/extract/fuse）已有独立 Service 且可单独测试；Pipeline 本身通过集成测试覆盖
- **负面**：当前仅 2 个实现，抽象层级可能过度。但需求明确要求"保留未来新增第三种链路的扩展能力"，引入接口是正确的预见性设计