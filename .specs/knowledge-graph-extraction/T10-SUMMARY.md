# T10-SUMMARY: 单元测试 — AC-3 + AC-5 + AC-6

- **Task ID**: T10
- **Change ID**: `knowledge-graph-extraction`
- **日期**: 2026-06-13

---

## 做了什么

创建 3 个单元测试文件，覆盖 14 个用例：

| 文件 | AC | 用例数 | 覆盖内容 |
|------|----|--------|----------|
| ExtractionValidatorTest | AC-3 | 7 | 合法JSON通过、缺字段、entityType越界、索引越界、relationType非法、null、空数组 |
| GraphServiceTest | AC-6 | 4 | 文档不存在→A0006、未就绪→A0009、空文本→A0008、空白文本→A0008 |
| GraphNodeAbstractionTest | AC-5 | 3 | 子类继承字段+自动ID、NodeType.fromLabel()、4种注册类型 |

## verify 输出

```
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 越界检查

TASK write_files: 3 项 | diff: 3 项 | 越界: 0 ✅

## 完成判定

14 个单元测试用例全部通过；AC-3/AC-5/AC-6 覆盖。