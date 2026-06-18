# T01-SUMMARY: ErrorCode 新增图谱抽取相关枚举值

- **Task ID**: T01
- **Change ID**: `knowledge-graph-extraction`
- **日期**: 2026-06-13

---

## 做了什么

在 `ErrorCode.java` 枚举中新增 3 个图谱抽取相关错误码：

- `A0008`：文档文本为空 → 400 BAD_REQUEST
- `A0009`：文档状态不允许抽取 → 400 BAD_REQUEST
- `A0010`：LLM 抽取结果校验失败 → 400 BAD_REQUEST

新增位置：A 区末尾（A0007 之后），不影响既有枚举常量。

## 改动文件

- `src/main/java/com/graphnexus/common/exception/ErrorCode.java`（+12 行）

## verify 输出

```
$ mvn compile -q
（无错误输出，编译通过）
```

## 6 维自查

- **R1 认知过载**：不适用（仅新增枚举值）
- **R2 变更传播**：无越界改动，仅 ErrorCode.java
- **R3 知识重复**：无
- **R4 偶然复杂**：无
- **R5 依赖混乱**：无（ErrorCode 是 common 层，无依赖倒置风险）
- **R6 领域扭曲**：命名使用中文教辅领域词（文档文本/抽取/校验）

✅ 沿用既有抽象 grep（R6.4）：
- BusinessException 异常体系：已确认 ErrorCode 枚举 + BusinessException(ErrorCode) 构造器模式 → 新增枚举值沿用
- GlobalExceptionHandler：已确认自动按 ErrorCode.getHttpStatus() 映射响应 → 无需改动

## 越界检查（R6.5）

- TASK write_files：`src/main/java/com/graphnexus/common/exception/ErrorCode.java`（1 项）
- 实际 diff 涉及：同 1 项
- 越界：0 ✅

## 完成判定

ErrorCode 枚举新增 A0008/A0009/A0010 三个值，编译通过。