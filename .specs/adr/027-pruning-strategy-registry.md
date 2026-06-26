# ADR-027: PruningStrategyRegistry Spring 自动发现（supersede ADR-010 switch-case）

## Context

ADR-010（`SubgraphPruningStrategy` 接口设计）采用 `QueryServiceImpl` 内部 switch-case 做策略路由：

```java
// ADR-010 注定的临时方案（v1 仅 1 个 case）
switch (intent) {
    case STUDENT_DIAGNOSIS:
        subgraph = diagnosisStrategy.prune(request);
        break;
    default:
        throw new BusinessException(A0019);
}
```

ADR-010 明确预留："意图 > 10 种后会变得臃肿，届时需重构为策略工厂 + Map 自动注入"。

`llm-intent-recognition` CHANGE 的 AC-12 要求 `QueryServiceImpl` 不直接注入具体策略实现（`StudentDiagnosisStrategy`），改为通过注册中心路由。虽 v1 仍仅 1 种策略，但本 change 正在改造意图识别链路（LLM-first + 新意图类型即将在 v2 解锁），此时引入注册机制时机恰当。

核心问题：
1. 如何在 Spring 容器中自动发现所有 `SubgraphPruningStrategy` 实现？
2. 策略如何声明自己处理的意图类型？
3. 如何与现有 `StudentDiagnosisStrategy` 兼容？

## Decision

### D1: Spring Map 自动注入

```java
// 伪代码
@Component
public class PruningStrategyRegistry {
    private final Map<String, SubgraphPruningStrategy> strategyMap;

    // Spring 自动注入所有 SubgraphPruningStrategy 实现
    // Map key = bean name, value = strategy instance
    public PruningStrategyRegistry(Map<String, SubgraphPruningStrategy> strategyMap) {
        this.strategyMap = strategyMap;
    }

    public SubgraphPruningStrategy get(String intentName) {
        SubgraphPruningStrategy strategy = strategyMap.get(intentName);
        if (strategy == null) {
            throw new BusinessException(A0019, "不支持的查询意图: " + intentName);
        }
        return strategy;
    }
}
```

Spring 的 `Map<String, T>` 注入机制：
- 当 Spring 容器中存在多个 `T` 类型的 bean 时，自动注入一个 Map，key = bean name，value = bean instance
- Bean name 由 `@Component("beanName")` 或默认类名首字母小写决定

### D2: Bean 命名约定 = 意图名

策略 bean 命名与 `QueryIntent` 枚举名一致：

```java
@Component("STUDENT_DIAGNOSIS")  // ← bean name = intent name
public class StudentDiagnosisStrategy implements SubgraphPruningStrategy {
    @Override
    public PrunedSubgraph prune(PruningRequest request) {
        // ... 现有逻辑不变
    }
}
```

Registry 使用时：

```java
SubgraphPruningStrategy strategy = registry.get(intent.name()); // "STUDENT_DIAGNOSIS"
PrunedSubgraph subgraph = strategy.prune(request);
```

选择 bean name = intent name 而非在策略接口上新增 `getIntent()` 方法的理由：
- 不修改 `SubgraphPruningStrategy` 接口（零接口变更，对既有实现透明）
- Bean name 在 Spring 容器启动时即可验证（无 bean name 冲突 = 无意图重复注册）
- 避免策略类返回错误的 intent 值（编译时约定 > 运行时方法返回值）

### D3: QueryServiceImpl 变更

```java
// BEFORE（ADR-010 switch-case）
private final StudentDiagnosisStrategy diagnosisStrategy; // 直接注入

// AFTER（ADR-027 registry）
private final PruningStrategyRegistry pruningStrategyRegistry; // 注入 Registry

// 使用
SubgraphPruningStrategy strategy = pruningStrategyRegistry.get(intent.name());
PrunedSubgraph subgraph = strategy.prune(pruningRequest);
```

### D4: 扩展方式（v2 使用指南）

新增意图只需两步，不改核心调用代码：

```java
// Step 1: 添加意图枚举
public enum QueryIntent {
    STUDENT_DIAGNOSIS(...),
    KP_ANALYSIS(...), // NEW
}

// Step 2: 实现策略 + 用意图名注册
@Component("KP_ANALYSIS") // bean name = intent name
public class KpAnalysisStrategy implements SubgraphPruningStrategy {
    @Override
    public PrunedSubgraph prune(PruningRequest request) {
        // 新策略的 Cypher 查询逻辑
    }
}
// Done. PruningStrategyRegistry 自动发现，QueryServiceImpl 零改动。
```

## Consequences

### 正面
- `QueryServiceImpl` 不再直接依赖具体策略类（AC-12），新增意图仅需添加枚举值 + 策略类，不修改核心调用代码
- Spring 自动发现（`@Component` 扫描），无手动注册表、无 XML 配置、无工厂代码
- Bean name 冲突在容器启动时即报错（fail-fast），防止意图-策略多对一
- 策略可独立进行单元测试（mock Registry 即可），不依赖 `QueryServiceImpl`

### 负面
- 对仅 1 个策略的场景显得过度设计（Map 只有 1 个 entry），但 10 行代码的代价换取 v2 零改动扩展
- Bean name = intent name 是约定而非编译器强制（如果命名拼写错误，运行时 get(intentName) 返回 null → BusinessException），优于 switch-case 的编译时穷举检查
- `Map<String, SubgraphPruningStrategy>` 注入要求 Spring 容器中至少存在 1 个该类型 bean，否则注入空 Map → Registry.get() 抛异常（当前 `StudentDiagnosisStrategy` 总是存在，无此问题）

### 风险缓解
- `PruningStrategyRegistry` 构造时 log.info 打印已注册策略清单（bean names），启动时即可发现注册遗漏
- 单元测试：`PruningStrategyRegistryTest` 验证 "STUDENT_DIAGNOSIS" → 返回非空策略，"UNKNOWN_INTENT" → 抛异常
- v2 若出现"同一意图名对应 2 个 bean"（Spring 启动失败 → BeanDefinitionOverrideException），通过 `@Component` value 唯一性保证

### Supersede 声明

本 ADR **supersede** ADR-010 中关于策略注册机制的 D1（switch-case）。ADR-010 的 D2（StudentDiagnosisStrategy 分步 Cypher）不受影响，策略内部 Cypher 查询逻辑不变。