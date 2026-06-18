# T04-SUMMARY: 拆分 L2 document 模块

- **Task ID**: T04
- **Change ID**: refine-package-structure
- **完成时间**: 2026-06-13

---

## 做了什么

将 L2 `application/document/service/` 摊大饼包拆分为 4 个子包：

| 子包 | 内容 | 文件数 |
|------|------|:--:|
| `service/` | 接口：`DocumentService.java` | 1 |
| `service/impl/` | 实现：`DocumentServiceImpl.java` | 1 |
| `model/` | BO + 领域模型：`DocumentBO.java`、`UpdateDocumentBO.java`、`ParseResult.java` | 3 |
| `parser/` | 解析器策略：`DocumentParser.java`（接口）、`PdfBoxDocumentParser.java`（实现） | 2 |

关键操作：
- git mv 6 个文件到新子包
- 更新 6 个文件的 package 声明
- 补充 5 个文件的 import 语句（同包引用变为跨包子包引用）：
  - `DocumentServiceImpl`：+5 import（DocumentBO, UpdateDocumentBO, ParseResult, DocumentParser, DocumentService）
  - `DocumentService`：+3 import（DocumentBO, UpdateDocumentBO, ParseResult）
  - `PdfBoxDocumentParser`：+1 import（ParseResult）
  - `DocumentParser`：+1 import（ParseResult）
  - `DocumentController`：3 import 路径修正
  - `DocumentVO`：1 import 路径修正
  - `ParseResultVO`：1 import 路径修正

## 改动文件

10 files changed: 6 renamed + 4 modified.

## verify 输出

```
mvn clean compile → BUILD SUCCESS (76 source files compiled)
```

## 6 维自查

- **R1 认知过载**：✅ 拆分后每个子包 ≤ 3 个文件，职责单一
- **R2 变更传播**：✅ 仅修改 T04 write_files 范围内文件，零越界
- **R3 知识重复**：N/A
- **R4 偶然复杂**：✅ parser/ 仅对 document 模块有意义，其他模块不创建
- **R5 依赖混乱**：✅ L2 service/ → model/ + parser/ 均为同层内部依赖；L1 → L2 依赖方向正确
- **R6 领域扭曲**：✅ 包名 model/service/parser 均为领域术语

## 沿用既有抽象 grep（R6.4）

- 子包命名：沿用 CONTEXT 四层架构 + POJO 后缀约定（DO/BO/VO/DTO）
- 接口/Impl 分离：沿用 init-platform DESIGN § D1（依赖倒置原则）

## 越界检查（R6.5）

- TASK write_files：16 项
- 实际 diff 涉及：10 files（6 rename + 4 modify）
- 越界：0 ✅

## 提交

`feat(refine-package-structure): T04 拆分L2 document模块为service/impl/model/parser四子包` (`96f37ea`)