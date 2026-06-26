# T01-SUMMARY: 新建 GraphConstructedEvent 类

- **Task ID**: T01
- **Change ID**: fusion-to-analysis-event-driven
- **状态**: ✅ 完成

---

## 做了什么

在 `application/graph/construction/event/` 下新建 `GraphConstructedEvent`（extends `ApplicationEvent`），作为图谱构建完成的统一同步事件契约。

载荷字段（DESIGN D3/D10）：
- `source`（String）：DOCUMENT / CSV（public static final 常量）
- `mode`（String）：FULL / INCREMENTAL（public static final 常量）
- `subject`（String）
- `kpNames`（List\<String\>）
- `documentId`（Long，nullable，CSV 时为 null）
- `examNo`（String，nullable，DOCUMENT 时为 null）

使用 Lombok `@Getter` + `extends ApplicationEvent`，对齐 `GradeUploadedEvent` 载荷模式。Source/mode 用 String 常量替代 enum，对齐 `GradeUploadedEvent` 风格。

---

## 改动文件

| 文件 | 操作 |
|------|------|
| `application/graph/construction/event/GraphConstructedEvent.java` | 新建 |

---

## verify 输出

```
mvn test-compile: EXIT 0 ✅
grep GraphConstructedEvent: 确认文件存在 ✅
```

---

## 6 维自查（R6.4）

- ✅ 沿用既有抽象 grep：找到 3 个 `extends ApplicationEvent`（`GraphChangedEvent`/`GradeUploadedEvent`/`GradeDeletedEvent`），本类沿用此模式 + `GradeUploadedEvent` 的 String 常量风格
- R1 认知过载：单个类 ~50 行，字段 6 个 + 4 个常量，无嵌套逻辑 ✅
- R2 变更传播：仅新建 1 个文件，无越界 ✅
- R3 知识重复：无重复逻辑 ✅
- R4 偶然复杂：无扩展点预留 ✅
- R5 依赖混乱：依赖仅 `lombok.Getter` + `spring.ApplicationEvent` ✅
- R6 领域扭曲：字段名均为领域词（source/mode/subject/kpNames）✅

---

## 越界检查（R6.5）

- TASK write_files：1 项（`GraphConstructedEvent.java`）
- 实际 diff 涉及：1 项（新建）
- 越界：0 ✅

---

## 破坏性变更

未命中（纯新建，无删除/改签名）。

---

## 数据库迁移

不涉及。
