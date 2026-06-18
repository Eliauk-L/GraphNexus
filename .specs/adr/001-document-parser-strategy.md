# ADR-001: DocumentParser 接口归属 L2 application 层

- **状态**: accepted
- **日期**: 2026-06-11
- **关联**: `document-process-pdf-minimal` DESIGN § D1

---

## Context

`document-process-pdf-minimal` 需要实现 PDF 解析能力。当前使用 Apache PDFBox，但未来计划切换为主力方案 MinerU（`docs/tech-stack-java.md` §1.3 已标注）。需要设计一个可扩展的解析器接口，使得切换实现类时调用方（DocumentService）无需修改代码。

核心争议：这个接口应该定义在哪一层？

- **方案 A**：L2 `application/document/service/DocumentParser.java`
- **方案 B**：L3 `infrastructure/storage/DocumentParser.java`
- **方案 C**：`common/` 公共层

## Decision

**选择方案 A**：`DocumentParser` 接口定义在 L2 `application/document/service/` 包下。

```
com.graphnexus.application.document.service
    ├── DocumentParser.java           ← 接口（L2）
    └── PdfBoxDocumentParser.java    ← 实现类（L2，@Service）
```

## Consequences

**正向**：
- 解析策略选择是**业务决策**，由 L2 控制符合分层语义。未来从 PDFBox 切到 MinerU 是业务需求驱动（MinerU 版面分析更优），不是纯技术替换
- 接口与实现同包，开发时心智负担最低——不需要跨层找实现
- 调用方 `DocumentServiceImpl` 只依赖接口，符合依赖倒置原则
- ArchUnit 校验通过：L2 内定义接口 + 实现，不存在跨层违规

**负向**：
- `PdfBoxDocumentParser` 需要 import `org.apache.pdfbox.*`（第三方库），理论上 L2 的类不应该直接依赖第三方库的具体 API——但接口本身不依赖 PDFBox，只有实现类依赖。实现类本质上是"适配器"，即使放在 L3 也一样依赖 PDFBox jar
- 如果未来需要多种解析器（PDFBox, MinerU, Tika...），L2 包内的实现类会增多——可以通过子包 `parser/` 组织

**被否决的方案**：

| 方案 | 否决理由 |
|:--|:--|
| B: 接口放 L3 | 解析策略选择是业务决策，L3 不应定义业务接口。L3 只知道"怎么存文件"，不知道"为什么用 PDFBox 而不是 MinerU" |
| C: 接口放 common | common 是技术横切层（异常、日志、响应体），不应放业务领域接口。把 DocumentParser 放 common 会暗示所有层都可以调用它，但实际上只有 L2 Service 需要解析 PDF |

---

> 推翻本 ADR 的触发条件：① MinerU 接入时发现接口签名需要大改（如需要传入文件路径而非 byte[]）；② 需要引入多种文件类型解析器（不只 PDF），接口需要泛化为 `DocumentParser<T extends DocumentType>`。