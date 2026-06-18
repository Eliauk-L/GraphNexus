# T01-SUMMARY: ErrorCode 新增 A0011~A0015

- **Task ID**: T01
- **Change ID**: csv-grade-import
- **状态**: ✅ done
- **时间**: 2026-06-15

---

## 做了什么

在 ErrorCode 枚举中新增 5 个错误码：
- A0011 (BAD_REQUEST): CSV 格式错误
- A0012 (BAD_REQUEST): CSV 缺少必要列
- A0013 (BAD_REQUEST): CSV 编码异常
- A0014 (CONFLICT): 考试编号不存在
- A0015 (NOT_FOUND): 待删除的考试编号不存在

## 改动了哪些文件

| 文件 | 操作 |
|---|---|
| `common/exception/ErrorCode.java` | 新增 5 个枚举常量（+15 行） |

## verify 输出

```
$ mvn compile -pl . -q 2>&1 | tail -5
(编译成功，无错误输出)
```

✅ 编译通过。

## 6 维自查

- **R1 认知过载**：不适用（纯枚举扩展）
- **R2 变更传播**：仅 ErrorCode.java，无越界
- **R3 知识重复**：不适用
- **R4 偶然复杂**：不适用
- **R5 依赖混乱**：不适用
- **R6 领域扭曲**：不适用

✅ 沿用既有抽象 grep（R6.4）：
- ErrorCode 模式：grep 确认既有模式为 `(errorCode, HttpStatus, defaultUserTip)` 三参数构造 → 沿用

## 越界检查（R6.5）

```
✅ TASK write_files：1 项（common/exception/ErrorCode.java）
✅ 实际 diff 涉及：1 项（ErrorCode.java）
✅ 越界：0
```