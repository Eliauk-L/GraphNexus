# SUMMARY: T02 - ErrorCode 枚举 + BusinessException 异常类

- **Change ID**: `init-platform`
- **Task ID**: `T02`
- **完成时间**: 2026-06-11
- **AI 角色**: Dev

---

## 做了什么

创建 ErrorCode 枚举（6 个值：A0001/A0002/A0003/B0001/B0002/C0001）和 BusinessException 异常类（3 个构造方法重载）。严格对齐 CONTEXT 中「错误码」术语定义的 5 位编码规则和「分层异常传递」规则。

## 改动文件

| 文件 | 性质 | 说明 |
|---|---|---|
| `common/exception/ErrorCode.java` | 新增 | 6 个枚举值，含 errorCode + httpStatus + defaultUserTip |
| `common/exception/BusinessException.java` | 新增 | 3 个构造方法：仅 errorCode / +errorMessage / +errorMessage+userTip |

## verify 输出

```text
$ mvn compile
[INFO] BUILD SUCCESS
```

## 6 维自查

> 此任务仅创建 2 个基础类，无复杂逻辑。按内置 R1~R6 快查：
> - R1 认知过载：无，单类 < 60 行
> - R2 变更传播：无越界
> - R3 知识重复：无重复
> - R4 偶然复杂：无过度抽象
> - R5 依赖混乱：依赖方向正确（自包含，仅依赖 JDK + Spring HttpStatus）
> - R6 领域扭曲：命名使用业务域语言（BusinessException / ErrorCode）

## 数据库迁移

N/A

## 越界检查

```
✅ 越界检查（R6.5）：
  - TASK write_files：2 项
  - 实际 diff 涉及：2 项
  - 越界：0
```

## 破坏性变更

N/A — 全部新增。

## 决策与偏离

无偏离。

## 是否触发新工作

无。

## 完成判定

- TASK.md 中对应任务已勾选：是
- 提交 hash：待提交