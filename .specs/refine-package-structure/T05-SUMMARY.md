# T05-SUMMARY: 测试文件对齐

- **Task ID**: T05
- **Change ID**: refine-package-structure
- **完成时间**: 2026-06-13

---

## 做了什么

将 2 个测试文件移动到正确包路径 + 修正 2 个遗留测试文件的 import：

1. `PdfBoxDocumentParserTest.java`：`application.document.service` → `application.document.parser`
2. `DocumentStatusTest.java`：`application.document.service` → `infrastructure.mysql.document`（纠错——原路径与被测类不一致）
3. `DocumentServiceTest.java`：补充 import（DocumentBO, ParseResult, DocumentParser, DocumentServiceImpl → 从 service/ 同包引用变为跨包引用）
4. `DocumentProcessingIntegrationTest.java`：补充 import（DocumentBO, ParseResult, UpdateDocumentBO → 同上）

## 改动文件

4 files: 2 renamed + 2 modified.

## verify 输出

```
mvn test-compile → BUILD SUCCESS
```

## 越界检查

- TASK write_files：4 项（含追加的 DocumentServiceTest + DocumentProcessingIntegrationTest）
- 实际 diff：4 项
- 越界：0 ✅

## 提交

`test(refine-package-structure): T05 测试文件对齐到正确包路径` (`4745ab6`)