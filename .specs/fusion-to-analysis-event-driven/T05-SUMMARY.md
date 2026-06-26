# T05-SUMMARY: 重构 ConstructionServiceImpl

- **Task ID**: T05
- **Change ID**: fusion-to-analysis-event-driven
- **状态**: ✅ 完成

---

## 做了什么

重构 `ConstructionServiceImpl.extract()` 方法，移除 `FusionService` 直接依赖，改为发布 `GraphConstructedEvent` 事件驱动融合。

### 3 处变更（DESIGN D5/D11 + §2.1）

1. **移除 `FusionService` 注入**（D11）：
   - 删除 `private final FusionService fusionService;` 字段
   - 删除 import（T03 已更新路径为 `analysis.fusion.service.FusionService`）

2. **`extract()` 方法重写阶段二**：
   - 移除 `phase2_fuse()` 调用 + `GraphChangedEvent` 发布（D6 冗余移除）
   - 改为：发布 `GraphConstructedEvent(source=DOCUMENT, mode=INCREMENTAL)` + 重读文档状态回填 `fusionWarning`（D3）

3. **删除 `phase2_fuse` 方法**：
   - 整个 private 方法体删除（~25 行），无残留调用方

### 保留不变

- `phase1_build` 方法不变
- `extract()` 其余逻辑不变
- `@Transactional` 不变（D2 · 监听器 join 同一 tx）
- `ApplicationEventPublisher` 注入保留（用于发布 `GraphConstructedEvent`）

---

## 改动文件

| 文件 | 操作 |
|------|------|
| `application/graph/construction/service/impl/ConstructionServiceImpl.java` | 修改 |

---

## verify 输出

```
mvn compile: EXIT 0 ✅
AC-2: 无 FusionService / fuseIncremental / phase2_fuse 残留 ✅
GraphConstructedEvent publish: 存在 ✅
GraphChangedEvent 发布: 已移除 ✅
```

---

## 6 维自查（R6.4）

- ✅ 沿用既有抽象 grep：`ApplicationEventPublisher.publishEvent` 沿用既有 Spring 事件机制；`GraphConstructedEvent` 使用 T01 新建的事件契约
- R1 认知过载：`extract()` 方法 ~30 行业务逻辑 + 事件发布 + 状态回读，清晰 ✅
- R2 变更传播：仅修改 `ConstructionServiceImpl.java`，无越界 ✅
- R3 知识重复：无重复逻辑 ✅
- R4 偶然复杂：无新增扩展点 ✅
- R5 依赖混乱：construction 不再注入 FusionService（D11），仅经事件通信 ✅
- R6 领域扭曲：变量名 `affectedKpNames`/`fusionWarning` 表达领域语义 ✅

---

## 越界检查（R6.5）

- TASK write_files：1 项（`ConstructionServiceImpl.java`）
- 实际 diff 涉及：1 项
- 越界：0 ✅

---

## 破坏性变更（1.8 协议）

### 1.8.1 grep 引用图

```
phase2_fuse: 仅 ConstructionServiceImpl.java:85(调用) + :139(定义)
FusionService(ConstructionServiceImpl 内): 仅 phase2_fuse 使用
GraphChangedEvent(ConstructionServiceImpl 内): 仅 旧 line 87 发布点
→ 全部为内部实现，无外部引用
```

### 1.8.2 影响清单

- `FusionService` 字段删除：private，仅 `phase2_fuse` 内部使用 → 0 外部影响
- `phase2_fuse` 方法删除：private → 0 外部影响
- `GraphChangedEvent` 发布移除：FusionServiceImpl 内部保留发布（D6），缓存失效无回归

### 1.8.3 用户决策

用户通过 DESIGN/TASK 预批准（D11 明确禁止 construction 注入 FusionService）。

### 1.8.4 回归测试

T08 将更新 `ConstructionServiceTest`：`@MockBean FusionService` → `@MockBean ApplicationEventPublisher`，verify `publishEvent(any(GraphConstructedEvent.class))`。

---

## 数据库迁移

不涉及。
