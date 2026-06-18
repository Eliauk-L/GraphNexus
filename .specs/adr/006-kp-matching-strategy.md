# ADR-006: KP 匹配策略接口 + 模糊匹配多度量组合算法

- **日期**: 2026-06-15
- **状态**: accepted
- **来源**: `wide-graph-fusion` DESIGN

---

## Context

宽图谱融合需要判断两个 KnowledgePoint 是否指向同一知识点。KP 来自两个不同来源：

- **文档抽取**：LLM 从教辅 PDF 中识别，名称较完整规范（如"二次函数顶点坐标"）
- **CSV 导入**：教师手工填写，名称可能简化或用别名（如"顶点坐标公式"）

v1 不做 LLM 语义匹配或向量相似度，但 REQUIREMENT 要求匹配策略**可扩展替换**，后续可接入更精准的算法。

## Decision

### 接口契约

```java
public interface KpMatchingStrategy {
    /**
     * @return 0~1 相似度，1=完全匹配，0=完全不相关
     */
    double match(KpCandidate a, KpCandidate b);

    /** 策略标识，对应 yml 中 fusion.kp-matching.strategy 配置值 */
    String getName();
}
```

### v1 实现：FuzzyMatchStrategy — 多度量组合

```
combinedScore = α × charJaccard + β × bigramJaccard + γ × (1 - normLevenshtein)
```

- **charJaccard**：字符集交/并（如"二次函数顶点坐标" ∩ "顶点坐标公式" = {顶,点,坐,标} → 4/10 = 0.40）
- **bigramJaccard**：二字滑动窗口交/并（捕获局部词序，如"顶点"+"点坐"+"坐标"作为公共 bigram）
- **normLevenshtein**：1 - (编辑距离 / max(lenA, lenB))（如"一次函数图像"→"二次函数图像" = 1/6 → 0.833）
- **默认权重**：α=0.3, β=0.5, γ=0.2（BigramJaccard 权重最高，因为词序信息对中文语义区分更关键）
- **阈值**：0.85（combinedScore ≥ 0.85 → 同一融合组）
- **前置过滤**：subject 必须相同（跨学科不匹配，第一分组键）
- **名称归一化**：全角转半角、trim、去多余空格、去标点符号

### yml 配置

```yaml
fusion:
  kp-matching:
    strategy: fuzzy           # v1 默认；可选 exact（精确匹配测试桩）
    threshold: 0.85           # 融合阈值
    fuzzy:
      alpha: 0.3              # 字符 Jaccard 权重
      beta: 0.5               # Bigram Jaccard 权重
      gamma: 0.2              # 归一化编辑距离权重
```

### 备选实现（测试桩）

- **ExactMatchStrategy**：`name.equalsIgnoreCase() && subject.equals()`，用于验证策略可替换性（AC-11）

## Consequences

### 优点

- **多度量互补**：charJaccard 捕获字面重叠（防漏合）、bigramJaccard 捕获词序（防误合）、编辑距离平滑极端情况
- **全配置化**：α/β/γ/阈值均可通过 yml 实时调参，无需重新编译
- **接口可替换**：v2 接入向量/LLM 匹配只需新增实现类 + 改 yml 一行

### 缺点

- **Bigram 粒度局限**：对长度差异大的 KP 对（"二次函数顶点坐标" 8 字 vs "顶点坐标公式" 6 字），bigramJaccard ≈ 0.33，仍可能漏合。但这是字符级方法的固有限制，需 v2 LLM 语义匹配解决
- **三度量组合计算成本**：每组 KP 对需计算 3 种度量 → 做加权。KP 对数量 = C(n,2)，100 KP = 4950 对，全在内存计算，成本可忽略（每对微秒级）
- **阈值 0.85 是经验值**：需真实数据校准。已知边界 case："一次函数定义" vs "二次函数定义" combinedScore ≈ 0.83（略低于阈值，不合并——正确；但仅差 0.02，如果噪声数据中阈值偏移可能误判）

### 升级路径

当需要更精准匹配时：
1. 实现 `VectorSimilarityStrategy`（调用 embedding 服务 → 余弦相似度）
2. 实现 `LlmSemanticStrategy`（调 LLM 判断："以下两个知识点是否等价？"）
3. 都不需修改 `FusionService` 核心逻辑——只改 yml 一行

---

> 本 ADR 在引入向量数据库或 LLM 语义匹配实现时需重审阈值和度量组合的取舍。