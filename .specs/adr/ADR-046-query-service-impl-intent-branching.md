# ADR-046: QueryServiceImpl 意图分支 — if-else vs 策略接口

## Context

`class-weakness-overview` 需要在 `QueryServiceImpl` 中同时支持两种意图的查询流程：

- `STUDENT_DIAGNOSIS`：resolveStudent → StudentDiagnosisStrategy.prune → serializeSubgraph（学生视图）
- `CLASS_WEAKNESS_OVERVIEW`：resolveClass → ClassWeaknessOverviewStrategy.prune → serializeClassSubgraph（班级聚合视图）

两种流程在以下步骤存在差异：
1. 实体解析（resolveStudent vs resolveClass）
2. 子图序列化（学生视图 vs 班级聚合视图）
3. 模板变量组装（student 变量 vs class 变量）

核心问题：这些差异应该通过策略接口抽象，还是通过 if-else 分支处理？

## Decision

### D0 · if-else 分支，不引入策略接口

**选择**：在 `chat()` 和 `ask()` 方法内按 intent 类型使用 if-else 分支，选择不同的内部处理方法。

**理由**：
- 与 `llm-intent-recognition` DESIGN D2 逻辑一致：D2 对"仅 2 种输出格式"选择 yml 配置 + if-else 而非 `OutputFormatStrategy` 接口，理由是"仅 2 种格式，Strategy 接口是过度工程"
- 仅 2 种意图，if-else 约 15 行分支代码；引入策略接口至少新增 3 个文件（接口 + 2 个实现）
- 意图分支集中在 3 个决策点（实体解析、序列化、模板变量），每个决策点约 5 行代码
- 所有分支走相同的 LLM 调用、校验、重试、持久化逻辑，仅中间数据处理不同

**备选方案（被拒）**：

**A) `EntityResolutionStrategy` 接口 + `StudentEntityResolver` / `ClassEntityResolver`**：
- 新增 3 个文件，对 2 种实体类型过度设计
- 无法覆盖序列化和模板变量的差异（仍需额外抽象）

**B) 仅通过 `chat()` 支持班级概览，`ask()` 不改**：
- `ask()` 端点失去班级概览能力，API 不完整
- 未来教师可能想通过 API 直接调用班级概览（不经过自然语言）

### D1 · 分支点设计

**chat() 分支**（主入口，Q2=C）：

```
chat(question):
  1. intent = recognizeIntent(question)
  2. entities = extractEntities(question)  // 统一提取，返回 studentName/className/subject
  3. if intent == STUDENT_DIAGNOSIS:
       validate entities has studentName or studentNo
       return askWithIntent(question, studentName, studentNo, subject, intent)
  4. if intent == CLASS_WEAKNESS_OVERVIEW:
       validate entities has className
       return askClassWithIntent(question, className, subject, intent)
```

**ask() 分支**（显式参数入口）：

```
ask(question, studentName, studentNo, className, subject):
  1. intent = recognizeIntent(question)
  2. if className != null || intent == CLASS_WEAKNESS_OVERVIEW:
       classInfo = resolveClass(className)
       return askClassInternal(taskId, question, className, subject, intent)
  3. else:
       student = resolveStudent(studentName, studentNo)
       return askInternal(taskId, question, studentName, studentNo, subject, intent)
```

### D2 · 新增私有方法

| 方法 | 职责 | 对应学生版 |
|------|------|-----------|
| `resolveClass(className)` | 从 MySQL 验证班级存在 + 获取学生列表 | `resolveStudent(name, no)` |
| `serializeClassSubgraph(subgraph, className, classSize)` | 班级聚合视图序列化 | `serializeSubgraph(subgraph, student)` |
| `buildClassTemplateVars(...)` | 班级模板变量组装（className/classSize/subject 等） | `buildTemplateVars(...)` |
| `askClassInternal(taskId, question, className, subject, intent)` | 班级概览完整执行流程 | `askInternal(taskId, question, studentName, studentNo, subject, intent)` |
| `askClassWithIntent(question, className, subject, intent)` | 带预识别意图的班级概览入口 | `askWithIntent(question, studentName, studentNo, subject, intent)` |

### D3 · 何时重构为策略接口

触发条件（满足任一）：
- 意图数量 ≥ 3 种
- 某个分支点代码 > 20 行
- 新增意图导致 if-else 链超过 3 层

满足时重构为：
- `EntityResolutionStrategy` 接口（resolve → EntityInfo）
- `SubgraphSerializationStrategy` 接口（serialize → String）
- 或在 L2 层独立 Service 类（`StudentDiagnosisService` / `ClassOverviewService`），`QueryServiceImpl` 仅做路由

## Consequences

- **正向**：最小化改动量（约 80 行新增代码），`QueryServiceImpl` 结构清晰可读
- **正向**：所有分支共享 LLM 调用、校验、重试、持久化代码（约 200 行），避免重复
- **正向**：重构触发条件明确，不会永远停留在 if-else
- **负向**：`QueryServiceImpl` 行数继续膨胀（当前约 950 行，本次预计增至 ~1050 行）。但职责内聚（都是"问答流程编排"），暂不需要拆类
- **负向**：if-else 分支在 3 个决策点重复出现（chat/ask/askAsync），存在一定代码重复。但每个决策点的分支逻辑不同（chat 侧重实体提取校验，ask 侧重参数校验），强行合并反而增加复杂度