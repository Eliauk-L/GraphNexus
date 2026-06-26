# T06-SUMMARY: 重构 GradeGraphEventListener

- **Task ID**: T06
- **Change ID**: fusion-to-analysis-event-driven
- **状态**: ✅ 完成

---

## 做了什么

重构 `GradeGraphEventListener`（DESIGN D5 + §2.2 成绩路径数据流）：

1. **新增 `ApplicationEventPublisher` 注入**（`@RequiredArgsConstructor` 自动注入）
2. **在 `onGradeUploaded` 方法中，KP + TESTED 循环完成后，新增 `GraphConstructedEvent` 发布**：
   ```java
   eventPublisher.publishEvent(new GraphConstructedEvent(
       this, SOURCE_CSV, MODE_FULL,
       event.getSubject(), event.getKnowledgePoints(), null, event.getExamNo()));
   ```
   放在 `log.info("图谱构建完成...")` 之前，确认 ④ KP+TestedEdge 写入全部完成后发布（D5 时机保证）

3. **移除 `@Order(1)` 注解** — `GradeUploadedEventListener`(@Order(2)) 已在 T03 删除，`GradeUploadedEvent` 仅剩本监听器唯一消费者，`@Order(1)` 成 vestigial
4. **移除 `import org.springframework.core.annotation.Order`**

### 保留不变

- `onGradeDeleted` 方法不变
- `ExamRecordRepository`/`ConstructionGraphRepository` 注入不变

---

## 改动文件

| 文件 | 操作 |
|------|------|
| `application/graph/construction/listener/GradeGraphEventListener.java` | 修改 |

---

## verify 输出

```
mvn compile: EXIT 0 ✅
GraphConstructedEvent publish: present at line 84 ✅
@Order: absent ✅
```

---

## 6 维自查（R6.4）

- ✅ 沿用既有抽象 grep：`ApplicationEventPublisher.publishEvent` 沿用既有 Spring 事件机制；`@EventListener` 同步模式对齐 `MetricsCacheInvalidator`
- R1 认知过载：新增 ~5 行（事件发布），方法体清晰 ✅
- R2 变更传播：仅修改 1 个文件，无越界 ✅
- R3 知识重复：无重复逻辑 ✅
- R4 偶然复杂：无新增扩展点 ✅
- R5 依赖混乱：graph 监听器发布 GraphConstructedEvent 到 analysis 消费方，符合 ADR-024 方向 ✅
- R6 领域扭曲：`GraphConstructedEvent.SOURCE_CSV/MODE_FULL` 常量表达领域语义 ✅

---

## 越界检查（R6.5）

- TASK write_files：1 项
- 实际 diff 涉及：1 项
- 越界：0 ✅

---

## 破坏性变更

命中（移除 `@Order(1)` 公共注解），但：
- `@Order(1)` 仅影响同事件多消费者排序
- `GradeUploadedEventListener`(@Order(2)) 已在 T03 删除
- `GradeUploadedEvent` 现仅剩 `GradeGraphEventListener` 单一消费者
- 移除 `@Order` 无功能影响

## 数据库迁移

不涉及。
