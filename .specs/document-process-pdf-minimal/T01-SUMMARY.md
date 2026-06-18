# T01-SUMMARY: ErrorCode 新增文档相关枚举值

- **任务**: T01 — ErrorCode 新增 A0004/A0005/A0006/A0007
- **状态**: ✅ done
- **提交**: `098c854`

---

## 做了什么

在 `ErrorCode.java` 中新增 4 个 A 类（用户端）错误码，覆盖文档处理模块的错误场景：

| 枚举值 | HTTP 状态码 | 含义 | 触发场景 |
|:--|:--|:--|:--|
| A0004 | 400 BAD_REQUEST | 仅支持 PDF 格式文件 | 上传非 PDF 文件 |
| A0005 | 413 PAYLOAD_TOO_LARGE | 文件大小不能超过 50MB | 上传超过 50MB 文件 |
| A0006 | 404 NOT_FOUND | 文档记录不存在或已被删除 | 查/改/删不存在的文档 |
| A0007 | 409 CONFLICT | 该学科下已存在相同内容的文档 | 同一 PDF 同一学科重复上传 |

## 改动文件

- `src/main/java/com/graphnexus/common/exception/ErrorCode.java` (+12 行)

## verify 输出

```
[INFO] BUILD SUCCESS
[INFO] Total time:  0.763 s
```

## 6 维自查

- R1 认知过载：无函数改动，仅新增枚举常量
- R2 变更传播：仅 ErrorCode.java，符合 TASK write_files
- R3 知识重复：无
- R4 偶然复杂：无
- R5 依赖混乱：无
- R6 领域扭曲：errorCode/userTip 语义明确对应 AC 场景

## 越界检查

```
✅ TASK write_files：1 项 (ErrorCode.java)
✅ 实际 diff 涉及：1 项
✅ 越界：0
```

## LESSONS 扫描

`.specs/LESSONS.md` 无相关条目。

## TDD 豁免

纯枚举新增，无业务逻辑可测试。后续 T07/T09 会间接覆盖这些错误码的使用场景。