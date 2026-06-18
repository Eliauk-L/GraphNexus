# ADR-007: MASTERS 权重计算策略接口 + 时间衰减加权公式

- **日期**: 2026-06-15
- **状态**: accepted
- **来源**: `wide-graph-fusion` DESIGN

---

## Context

MASTERS 边 `(:Student)-[:MASTERS]->(:KnowledgePoint)` 的 `weight` 需要从学生多次考试的 TESTED 路径聚合计算。相同学生对相同 KP 可能在不同考试中被多次考查（如 9 月月考和 10 月月考都考了"对称轴"），每次得分率不同。

用户选择 Q2→B：时间衰减加权平均——最近考试更反映当前掌握水平。

同时 REQUIREMENT 要求权重计算策略**可扩展替换**，后续可接入 EWMA、贝叶斯推断等算法。

## Decision

### 接口契约

```java
public interface WeightCalculationStrategy {
    /**
     * @param records  该学生对某 KP 的所有考试成绩记录（含 examDate, rawScore, maxScore）
     * @return weight (0~1) + 摘要 JSON
     */
    WeightResult calculate(List<TestedRecord> records);

    String getName();
}
```

### v1 实现：TimeDecayStrategy — 时间衰减加权平均

```
同考试多题考同一 KP 时先取平均: avgScoreRate = Σ(rawScore_i / maxScore_i) / n

衰减权重: decayWeight_i = d^(monthsAgo_i)
  d = 0.9（月衰减因子，yml 可配置）
  monthsAgo = (当前日期 - examDate) / 30（取整月）

最终 weight = Σ(avgScoreRate_i × decayWeight_i) / Σ(decayWeight_i)

缺考（rawScore = null 或 "-/-"）: 跳过，不计入分子也不计入分母
```

**计算示例**（AC-4）：

```
S2 对 KP"对称轴"：
  EXAM-01（2024-09-15，距今 3 月）: raw=3, max=8 → 得分率 0.375, decay=0.9³=0.729
  EXAM-02（2024-11-15，距今 1 月）: raw=7, max=10 → 得分率 0.700, decay=0.9¹=0.900

weight = (0.375×0.729 + 0.700×0.900) / (0.729 + 0.900)
       = (0.273 + 0.630) / 1.629
       = 0.554
```

**摘要 JSON**（存入 MASTERS.description）：

```json
{
  "examCount": 2,
  "lastExamDate": "2024-11-15",
  "details": [
    {"examDate": "2024-09-15", "scoreRate": 0.375, "decayWeight": 0.729},
    {"examDate": "2024-11-15", "scoreRate": 0.700, "decayWeight": 0.900}
  ]
}
```

### yml 配置

```yaml
fusion:
  weight:
    strategy: time-decay      # v1 默认；可选 simple-average（测试桩）
    time-decay:
      factor: 0.9             # 月衰减因子
```

### 备选实现（测试桩）

- **SimpleAverageStrategy**：`weight = Σ(scoreRate_i) / n`，无视时间，用于验证策略可替换性（AC-12）

## Consequences

### 优点

- **时间敏感性**：最近的考试反映当前水平，避免半年前的高分掩盖近期的退步
- **缺考安全**：缺考不拉低权重，避免"-/-"当 0 分处理
- **同考试内先平均**：同一考试多题考同一 KP 时（如 Q3 和 Q7 都考"对称轴"），先取平均再参与时间衰减，避免同一考试重复计数放大影响
- **配置化**：衰减因子可调（低频考试场景下调低因子或切换为 simple-average）

### 缺点

- **低频考试场景衰减过快**：若学生仅有一次考试（6 个月前），decay=0.9⁶=0.53，weight 会被过度压低。此时实际计算：weight = scoreRate × 0.53 / 0.53 = scoreRate（衰减因子在分子分母中各出现一次，实际抵消！单次考试不受衰减影响）
- **当前日期依赖**：weight 随时间变化（不触发融合时 weight 不变，但"当前日期"来自融合执行时的 `LocalDate.now()`）。这意味着同一数据在不同日期融合结果不同——这是预期行为（掌握度本就随时间衰减）
- **同考试多 KP 仅取平均**：不做按题号加权，假设每题权重相同

### 升级路径

1. **EWMA**：`weight_new = α × scoreRate + (1-α) × weight_old`（更平滑但需存储上期 weight）
2. **贝叶斯推断**：先验知识（如年级平均掌握度）+ 似然（考试成绩）→ 后验 weight
3. 均只需实现 `WeightCalculationStrategy` 接口 + 改 yml 一行

---

> 本 ADR 在学生考试频次分布数据积累后需校准衰减因子。