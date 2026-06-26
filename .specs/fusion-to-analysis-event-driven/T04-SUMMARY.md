# T04-SUMMARY: 新建 GraphConstructedEventListener

- **Task ID**: T04
- **Change ID**: fusion-to-analysis-event-driven
- **状态**: ✅ 完成

---

## 做了什么

在 `application/analysis/fusion/event/` 下新建 `GraphConstructedEventListener`（`@Slf4j @Component @RequiredArgsConstructor`），消费 `GraphConstructedEvent`，同步触发融合。

注入：`FusionService` + `TextbookRepository`。不注入 `ApplicationEventPublisher`（D6 · FusionServiceImpl 内部发布 GraphChangedEvent）。

单一 `@EventListener` 方法 `onGraphConstructed`（无 `@Order`，唯一消费者 · D5）：

- **DOCUMENT 路径**（mode=INCREMENTAL）：
  1. `findById` 获取文档托管实例（D3 · JPA 一级缓存与 extract() 共享）
  2. `status EXTRACTED→FUSING`; `save(doc)`
  3. `fuseIncremental(kpNames, subject)`
  4. 成功 → `status→COMPLETED`; `save(doc)`
  5. 失败 → `status→EXTRACTED + failReason`; `save(doc)`（D4 · 修复 latent bug）
  6. 吞异常，不回滚构建（AC-9）

- **CSV 路径**（mode=FULL）：
  1. `fuseFull()`
  2. 失败吞异常（AC-9）

---

## 改动文件

| 文件 | 操作 |
|------|------|
| `application/analysis/fusion/event/GraphConstructedEventListener.java` | 新建 |

---

## verify 输出

```
mvn compile: EXIT 0 ✅
grep FUSING: 5 ✅
grep @EventListener: 1 ✅
grep @Order: 0 (无 @Order 注解) ✅
```

---

## 6 维自查（R6.4）

- ✅ 沿用既有抽象 grep：`@EventListener` 模式对齐 `MetricsCacheInvalidator.onGraphChanged`（plain @EventListener + @Component + @RequiredArgsConstructor）；`TextbookRepository.findById` 对齐既有 JPA 模式；`FusionService.fuseIncremental/fuseFull` 沿用搬迁后的分析模块服务
- R1 认知过载：单类 ~100 行，按 source 分支两个私有方法，清晰 ✅
- R2 变更传播：仅新建 1 个文件，无越界 ✅
- R3 知识重复：无重复逻辑 ✅
- R4 偶然复杂：无扩展点预留 ✅
- R5 依赖混乱：analysis 监听器 import graph.construction.event（事件类型，符合 ADR-024 编译时依赖倒置）；注入 TextbookRepository 为跨域写（DESIGN D4 显式标注，R3 风险已接受）✅
- R6 领域扭曲：方法名 handleDocumentPath/handleCsvPath 表达领域语义 ✅

---

## 越界检查（R6.5）

- TASK write_files：1 项
- 实际 diff 涉及：1 项
- 越界：0 ✅

---

## 破坏性变更

未命中（纯新建文件）。

---

## 数据库迁移

不涉及。
