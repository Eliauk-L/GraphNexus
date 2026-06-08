# GraphNexus 逻辑视图设计文档

> 基于图谱技术的 AI 上下文处理与精准问答系统 — 逻辑视图
>
> 版本：v1.1 | 创建日期：2026-06-05 | 修订日期：2026-06-05

---

## 一、抽象实体清单

### 1.1 核心图谱实体（Wide Graph 节点）

构成宽图谱的基本节点，是系统的核心领域对象。

| # | 实体 | 标识 | 说明 |
|---|------|------|------|
| 1 | **学生 (Student)** | 学号 | 宽图谱的核心"图钉"节点。属性：姓名、学号、班级、入学年份、状态（在读/休学/转学/毕业）。离校学生数据保留但标记为归档状态，不再参与权重计算 |
| 2 | **知识点 (KnowledgePoint)** | 知识点 ID | 宽图谱的另一个核心"图钉"节点。属性：名称、描述、学科归属、来源（自动抽取/人工创建/CSV 导入）、状态（正常/废弃） |
| 3 | **文档 (Document)** | 文档 ID | PDF 教辅资料实体。属性：文件名、上传时间、学科分类、处理状态、版本、上传人（管理员/教师） |
| 4 | **事件 (Event)** | 事件 ID | **多态抽象基类**，将结构化数据行转化为图谱节点。根据事件类型分为以下三个子类型 |
| 5 | ├─ **考试事件 (ExamEvent)** | 事件 ID | 由成绩 CSV 导入生成，关联学生与知识点 |
| 6 | ├─ **作业事件 (AssignmentEvent)** | 事件 ID | 属性：作业类型（日常练习/专项训练/章节测验）、批改结果（正确/部分正确/错误/未提交）、错题原因分类（计算错误/概念混淆/方法缺失/粗心） |
| 7 | └─ **课堂测验事件 (QuizEvent)** | 事件 ID | 教师随堂测验，更高频更轻量，支持模板复用 |
| 8 | **题目 (Question)** | 题目 ID | 试卷/作业中的具体题目，可关联一个或多个知识点（多对多） |
| 9 | **试卷 (ExamPaper)** | 试卷 ID | 考试/测验的容器，层级结构：考试 → 大题 → 小题 |
| 10 | **教师 (Teacher)** | 教师 ID | 任教班级关联，拥有个人知识库空间 |
| 11 | **班级 (Class)** | 班级 ID | 学生分组的基本单元，教师任教关系的关联对象 |
| 12 | **学科 (Subject)** | 学科 ID | **图谱节点**。宽图谱的顶层分类锚点。属性：学科名称（数学/物理/化学/...）、学科编码、描述、状态（启用/停用）。知识点通过 BELONGS_TO 边指向学科节点，支持图遍历查询"某学科下所有学生的掌握情况" |

### 1.2 图谱关系实体（Wide Graph 边）

连接图谱节点的边，本身具有独立属性。

| # | 实体 | 连接方向 | 说明 |
|---|------|---------|------|
| 13 | **掌握关系 (MasteryRelation)** | 学生 → 知识点 | 系统最重要的边类型。核心属性：**权重（0-1）**，动态变化，反映学生对该知识点的掌握程度 |
| 14 | **前置依赖关系 (PrerequisiteRelation)** | 知识点 → 知识点 | 有向边，表示"学 A 之前必须先掌握 B"，构成知识依赖有向图 |
| 15 | **知识关联关系 (KnowledgeRelation)** | 知识点 ↔ 知识点 | 关系类型包括：引用、推导、包含、同知识点 |
| 16 | **文档-知识点抽取关系 (ExtractionRelation)** | 文档 → 知识点 | 标注该知识点从哪份文档中抽取而来，附带置信度 |
| 17 | **学科归属关系 (BelongsToRelation)** | 知识点 → 学科 | 表示知识点属于某学科，支持图遍历查询"某学科下所有学生掌握情况"、学科级薄弱概览、跨学科关联分析 |

### 1.3 知识体系管理实体

管理知识点组织结构的管理型实体。

| # | 实体 | 说明 |
|---|------|------|
| 18 | **知识分类节点 (KnowledgeCategory)** | 多级分类树的节点：学科 → 一级模块 → 二级章节 → 具体知识点。支持拖拽调整层级 |
| 19 | **实体对齐候选对 (EntityAlignmentPair)** | 两个疑似相同实体的匹配对。属性：置信度（高 >0.9 / 中 0.6-0.9 / 低 <0.6）、审核状态（待审/已合并/已驳回）、来源文档/CSV |
| 20 | **测验模板 (QuizTemplate)** | 教师保存的常用测验结构，用于一键复用。属性：测验名称、关联知识点列表、题目结构、创建教师 |

### 1.4 规则与配置实体

可配置的业务规则，将硬编码逻辑提升为可管理的实体。

| # | 实体 | 说明 |
|---|------|------|
| 21 | **剪枝策略 (PruningStrategy)** | 按任务类型配置：最大跳数（深度）、每跳最大邻居数（广度）、关系权重阈值、关系类型过滤。支持版本管理与 A/B 对比测试 |
| 22 | **权重更新规则 (WeightUpdateRule)** | 包含两类子规则 ↓ |
| 23 | ├─ **时间衰减规则 (TimeDecayRule)** | 衰减曲线类型（指数/线性/阶梯）、半衰期、计算周期 |
| 24 | └─ **行为事件权重规则 (BehaviorWeightRule)** | 事件类型 → 权重调整幅度（+Δ / -Δ），生效范围（仅当前知识点 / 影响前置依赖链） |
| 25 | **得分分配规则 (ScoreAllocationRule)** | 一道题关联多个知识点时，得分如何分配：平均分配 / 按权重比例分配 / 全部计入 |
| 26 | **关联模型定义 (AssociationModel)** | 考试-题目-知识点-得分链条的统一数据结构定义，定义得分粒度（总分/每题/每知识点） |

### 1.5 AI/LLM 相关实体

AI 推理链路上的输入输出实体。

| # | 实体 | 说明 |
|---|------|------|
| 27 | **Prompt 模板 (PromptTemplate)** | 按任务场景维护，支持变量占位符（`{{student_name}}`、`{{subgraph_json}}` 等），含版本历史与回滚 |
| 28 | **LLM 配置 (LLMConfig)** | 模型选择（按任务场景路由）、调用配额（日/周/月上限）、降级策略 |
| 29 | **LLM 调用记录 (LLMCallLog)** | 每次调用的完整信息：时间、任务类型、模型名称、Prompt 模板版本、输入/输出 Token 数、响应延迟、调用结果 |
| 30 | **归因报告 (AttributionReport)** | 输出内容：薄弱根因列表（按影响程度降序）、多维证据链、置信度标注（高/中/低）、原文引用 |
| 31 | **教学建议 (TeachingSuggestion)** | 补救路径（分步）+ 配套教辅资源匹配（标注页码/题号/难度）+ 时间估算 |
| 32 | **复习路径 (ReviewPath)** | 个性化复习优先级排序 + 时间预估 + 资源推荐 + 复习效果自检 |
| 33 | **子图 (Subgraph)** | 剪枝策略的输出，作为 LLM 的上下文输入。属性：节点集、关系集、任务类型、生成策略版本 |
| 34 | **分享链接 (ShareLink)** | 归因报告的加密分享链接。属性：报告ID、权限级别（本校教师/指定家长/公开）、有效期、访问密码、访问统计 |
| 35 | **图谱快照 (GraphSnapshot)** | 保存的学生知识图谱视图快照，用于与历史快照对比。属性：学生ID、快照名称、快照时间、节点集、关系集 |

### 1.6 运维与系统实体

保障系统运行的支撑实体。

| # | 实体 | 说明 |
|---|------|------|
| 36 | **定时任务 (ScheduledTask)** | Cron 表达式、执行状态（运行中/已调度/已暂停）、依赖关系（DAG 编排） |
| 37 | **任务执行记录 (TaskExecutionRecord)** | 开始/结束时间、执行耗时、处理数据量、执行日志、失败诊断 |
| 38 | **数据库备份 (GraphBackup)** | 备份文件、备份类型（全量/增量）、校验和、完整性状态 |
| 39 | **系统日志 (SystemLog)** | Trace ID、服务名称、日志级别、时间戳，支持多维组合查询 |
| 40 | **权重变更日志 (WeightChangeLog)** | 每次权重变化的前后值、触发原因（衰减规则 ID / 事件 ID）、操作来源（系统自动/手动修正） |
| 41 | **搜索快照 (SearchSnapshot)** | 保存的常用日志搜索条件，支持一键加载复用。属性：快照名称、搜索查询条件、创建时间、创建人 |
| 42 | **公开审核申请 (PublicApplication)** | 教师申请将个人教辅资料公开到全校的审核流程。属性：文档ID、申请教师、审核状态（待审/已通过/已驳回）、审核意见 |
| 43 | **文档访问记录 (DocumentAccessRecord)** | 记录 PDF 教辅被教师浏览或引用的事件，用于运营统计（文档利用效率、僵尸文档识别）。属性：文档ID、访问者ID、访问类型（浏览/引用）、访问时间 |
| 44 | **通知 (Notification)** | 系统发送的通知消息。属性：接收人ID、通知类型（邮件/站内消息/Webhook）、标题、内容、优先级（高/中/低）、发送状态、发送时间 |
| 45 | **通知偏好 (NotificationPreference)** | 用户的通知接收偏好设置。属性：用户ID、接收渠道、免打扰时段、订阅的通知类型 |
| 46 | **通知模板 (NotificationTemplate)** | 系统预定义的通知模板。属性：模板名称、模板内容（支持变量占位符）、适用场景（如"PDF 解析完成""公开审核待处理""备份失败告警"） |

### 1.7 用户角色实体

| # | 角色 | 一句话定位 | 核心诉求 |
|---|------|----------|---------|
| 47 | **管理员 (Administrator)** | 数据资产的管理者 | 高效完成教辅资料与成绩数据的入库与管理 |
| 48 | **教师 (Teacher)** | 价值消费者与数据生产者 | 精准定位学生薄弱点，获取可行动的辅导建议；上传班级作业/测验数据 |
| 49 | **学生 (Student)** | 数据的来源与受益者 | 了解自身学习状态与知识薄弱点 |
| 50 | **运维人员 (OperationsStaff)** | 系统稳定性的保障者 | 保障服务的高可用运行 |
| 51 | **运营人员 (OperationsManager)** | 系统效果的度量者 | 量化系统使用情况与业务价值 |

### 实体总览

```
                    ┌──────────────────────────────┐
                    │       用户角色 (5)             │
                    │ 管理员/教师/学生/运维/运营      │
                    └──────────────┬───────────────┘
                                   │ 操作
                    ┌──────────────▼───────────────┐
                    │      核心图谱实体 (12)         │
                    │                              │
                    │  学生 ──掌握关系──▶ 知识点     │
                    │   │        ▲          │       │
                    │   │    事件(3子类)     │ BELONGS│
                    │   │        │          │ _TO    │
                    │   │    试卷→题目       │       │
                    │   │                   ▼       │
                    │  班级   文档──抽取──▶ 学科     │
                    │         教师                  │
                    │              前置依赖关系       │
                    └──────────────┬───────────────┘
                                   │ 受控于
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                    ▼
      ┌──────────────┐   ┌──────────────┐   ┌────────────────┐
      │ 规则配置 (6)  │   │ AI/LLM (9)   │   │ 运维系统 (11)   │
      │ 剪枝策略      │   │ Prompt模板    │   │ 定时任务        │
      │ 权重更新规则   │   │ LLM配置      │   │ 执行记录        │
      │ 得分分配规则   │   │ 调用记录      │   │ 数据库备份      │
      │ 关联模型定义   │   │ 归因报告      │   │ 系统日志        │
      │              │   │ 教学建议      │   │ 权重变更日志     │
      │ 知识体系 (3)  │   │ 复习路径      │   │ 搜索快照        │
      │ 分类节点      │   │ 子图         │   │ 公开审核申请     │
      │ 对齐候选对    │   │ 分享链接      │   │ 文档访问记录     │
      │ 测验模板      │   │ 图谱快照      │   │ 通知/偏好/模板   │
      └──────────────┘   └──────────────┘   └────────────────┘
```

> 共计 **8 大类、51 个抽象实体**（46 个领域实体 + 5 个用户角色）。

---

## 二、模块划分

### 2.1 模块总览

| # | 模块 | 一句话职责 |
|---|------|----------|
| **M1** | 主数据管理 (Master Data) | 管理系统中所有"人"和"组织"的生命周期，为宽图谱提供稳定的主体节点 |
| **M2** | 知识体系管理 (Knowledge System) | 构建和维护知识点分类树、前置依赖关系，以及跨来源的实体对齐 |
| **M3** | 数据入库引擎 (Data Ingestion Engine) | 将异构的外部数据源（PDF、CSV）转化为图谱中的结构化节点和关系 |
| **M4** | 图谱核心引擎 (Graph Core Engine) | 管理宽图谱的融合构建、任务驱动的动态剪枝、以及权重的动态更新 |
| **M5** | AI 分析引擎 (AI Analysis Engine) | 将剪枝子图转化为 LLM 可理解的上下文，驱动 LLM 生成结构化的分析报告与行动建议 |
| **M6** | 应用服务 (Application Service) | 面向教师、学生和运营人员，将底层分析能力封装为可直接使用的应用场景功能 |
| **M7** | 系统运维 (System Operations) | 保障系统稳定性，提供日志查询、数据备份、定时任务管理和监控能力 |
| **M8** | 运营分析 (Operations Analytics) | 量化系统使用情况与业务价值，提供文档处理产能、知识覆盖范围、文档利用效率和数据处理时效的统计分析 |

---

### 2.2 M1 · 主数据管理 (Master Data)

| 维度 | 说明 |
|------|------|
| **一句话职责** | 管理系统中所有"人"和"组织"的生命周期，为宽图谱提供稳定的主体节点 |
| **包含实体** | 学生、教师、班级、管理员、运维人员、运营人员 |
| **核心能力** | 学生 CRUD 与状态标记（在读/休学/转学/毕业）、班级分配与调整、教师-班级任教关系绑定、用户角色与权限管理 |
| **独立原因** | 主数据是宽图谱的"图钉"，变化频率低但准确性要求极高，与业务知识逻辑无关，需要独立维护其生命周期 |

---

### 2.3 M2 · 知识体系管理 (Knowledge System)

| 维度 | 说明 |
|------|------|
| **一句话职责** | 构建和维护知识点分类树、前置依赖关系，以及跨来源的实体对齐 |
| **包含实体** | 学科、知识点、知识分类节点、前置依赖关系、知识关联关系、实体对齐候选对 |
| **核心能力** | 多级分类树管理（学科→模块→章节→知识点）、前置依赖关系定义与循环检测、实体对齐审核（自动匹配 + 人工确认 + 批量操作 + 回滚）、知识体系完整性检查（空节点/孤立知识点/循环依赖） |
| **独立原因** | 知识体系是所有分析的基础骨架，归因分析的"上溯前置依赖链"、剪枝策略的"沿依赖链追溯"、复习路径的"依赖链底优先"都依赖它，必须作为独立的基础设施模块 |

---

### 2.4 M3 · 数据入库引擎 (Data Ingestion Engine)

| 维度 | 说明 |
|------|------|
| **一句话职责** | 将异构的外部数据源（PDF、CSV）转化为图谱中的结构化节点和关系 |
| **包含实体** | 文档、考试事件、作业事件、课堂测验事件、题目、试卷、得分分配规则、关联模型定义 |
| **核心能力** | **两条入库流水线**：① PDF 流水线——版面分析 → 实体抽取（NER）→ 关系抽取（RE）→ 图谱导入；② CSV 流水线——格式校验 → 关联模型匹配 → 事件节点生成 → 图谱导入；试卷结构定义与得分分配策略；导入结果反馈（成功/失败/异常明细） |
| **独立原因** | 入库是数据从"外部"到"图谱内"的唯一入口，涉及复杂的解析和校验逻辑，与图谱的查询、分析逻辑解耦。入库失败不应影响已有图谱的可用性 |

**内部子模块**：

```
M3 · 数据入库引擎
├── M3-a · 文档解析子模块
│   ├── PDF 上传与版本管理
│   ├── 版面分析（正文/标题/表格/公式区域识别）
│   ├── NER/RE 抽取（概念、公式、定理、定义）
│   └── 图谱导入（创建实体节点和关系边）
│
├── M3-b · 事件导入子模块
│   ├── 成绩 CSV 导入 → 考试事件节点
│   ├── 作业 CSV/手动录入 → 作业事件节点
│   └── 课堂测验录入 → 课堂测验事件节点
│
└── M3-c · 模型定义子模块
    ├── 试卷-题目-知识点关联模型定义
    ├── 得分分配规则配置
    └── 导入一致性校验
```

---

### 2.5 M4 · 图谱核心引擎 (Graph Core Engine)

| 维度 | 说明 |
|------|------|
| **一句话职责** | 管理宽图谱的融合构建、任务驱动的动态剪枝、以及权重的动态更新 |
| **包含实体** | 掌握关系、子图、剪枝策略、权重更新规则（时间衰减规则 + 行为事件权重规则）、权重变更日志 |
| **核心能力** | 以"学生"和"知识点"为图钉融合文档图谱与事件图谱为宽图谱、按任务类型执行剪枝策略（跳数/广度/阈值/关系过滤）、时间衰减计算（定时任务）与行为驱动权重调整（事件触发）、策略对比测试与版本管理、权重变更全链路审计 |
| **独立原因** | 这是系统的技术核心——"宽图谱融合 → 任务驱动剪枝 → 动态更新"三大支柱全部在此模块中。它依赖 M1/M2/M3 产出的数据，但自身不包含业务语义，只处理图谱层面的操作 |

**内部子模块**：

```
M4 · 图谱核心引擎
├── M4-a · 宽图谱融合
│   ├── 文档图谱 + 事件图谱 → 宽图谱合并
│   ├── 图算法计算（PageRank、度中心性）
│   └── 图谱健康度度量（连通分量、孤立节点、关系密度）
│
├── M4-b · 剪枝引擎
│   ├── 剪枝策略模板加载（按任务类型）
│   ├── 子图生成（跳数控制、邻居筛选、权重过滤、关系类型过滤）
│   ├── 策略 A/B 对比测试
│   └── 策略版本管理与回滚
│
└── M4-c · 权重引擎
    ├── 时间衰减计算（定时执行：指数/线性/阶梯衰减）
    ├── 行为事件驱动权重调整（正确作答 +Δ / 错误作答 -Δ）
    ├── 规则模拟与影响预估
    └── 权重变更日志记录
```

---

### 2.6 M5 · AI 分析引擎 (AI Analysis Engine)

| 维度 | 说明 |
|------|------|
| **一句话职责** | 将剪枝子图转化为 LLM 可理解的上下文，驱动 LLM 生成结构化的分析报告与行动建议 |
| **包含实体** | Prompt 模板、LLM 配置、LLM 调用记录、归因报告、教学建议、复习路径 |
| **核心能力** | 子图 → 上下文的序列化与组装、Prompt 模板管理与变量填充、LLM 模型选择/切换/配额控制/降级策略、归因分析（薄弱根因 + 多维证据链 + 置信度）、教学建议生成（补救路径 + 配套资源）、复习路径推荐（优先级排序 + 时间预估） |
| **独立原因** | LLM 的调用成本、响应延迟、模型切换是独立的技术关注点，与图谱操作逻辑解耦。将 AI 能力封装为独立模块，未来替换模型或增加新分析任务时只需扩展本模块 |

**内部子模块**：

```
M5 · AI 分析引擎
├── M5-a · 上下文构建
│   ├── 子图序列化（图谱 → LLM 可理解的文本/JSON）
│   ├── 上下文裁剪（Token 预算控制）
│   └── 多源信息融合（图谱数据 + 教辅引用 + 历史趋势）
│
├── M5-b · LLM 网关
│   ├── 模型选择与切换（按任务场景路由到不同模型）
│   ├── API 连通性校验
│   ├── 调用配额与成本控制（日/周/月上限、降级策略）
│   └── 调用日志记录（Token 消耗、延迟、结果）
│
└── M5-c · 分析生成器
    ├── 归因分析器 → 归因报告（根因 + 证据链 + 置信度）
    ├── 教学建议器 → 补救路径 + 配套教辅资源匹配
    └── 复习规划器 → 优先级排序 + 时间预估 + 资源推荐
```

---

### 2.7 M6 · 应用服务 (Application Service)

| 维度 | 说明 |
|------|------|
| **一句话职责** | 面向教师、学生和运营人员，将底层分析能力封装为可直接使用的应用场景功能 |
| **包含实体** | 教师个人知识库、归因报告导出、趋势曲线、跨学科关联路径、复习效果跟踪、分享链接、图谱快照、公开审核申请 |
| **核心能力** | 单学生归因查询与交互式追问、班级薄弱点概览与排名、学生个体知识图谱可视化、成绩趋势追踪与对比、跨学科关联分析与"假性薄弱"识别、归因报告导出/分享、教师个人教辅管理与公开审核、复习效果自检与投入产出分析 |
| **独立原因** | 这是系统的**应用层**，自身不包含核心算法，而是编排调用 M2/M4/M5 的能力，组合成面向用户的具体功能。将应用逻辑与引擎逻辑分离，使同一引擎可支撑多种上层应用 |

---

### 2.8 M7 · 系统运维 (System Operations)

| 维度 | 说明 |
|------|------|
| **一句话职责** | 保障系统稳定性，提供日志查询、数据备份、定时任务管理、通知和监控能力 |
| **包含实体** | 定时任务、任务执行记录、数据库备份、系统日志、搜索快照、通知、通知偏好、通知模板 |
| **核心能力** | 统一日志搜索（多维组合查询 + Trace ID 链路追踪）、图数据库备份/恢复/完整性验证、定时任务调度与管理（Cron 配置 + 依赖编排）、慢查询/大子图性能告警、系统通知（邮件/站内消息/Webhook） |
| **独立原因** | 运维关注点（可用性、可恢复性、可观测性）与业务逻辑完全正交，独立模块可被独立升级和替换 |

---

### 2.9 M8 · 运营分析 (Operations Analytics)

| 维度 | 说明 |
|------|------|
| **一句话职责** | 量化系统使用情况与业务价值，提供文档处理产能、知识覆盖范围、文档利用效率和数据处理时效的统计分析 |
| **包含实体** | 文档访问记录 |
| **核心能力** | 文档产能统计（PDF 上传数量/解析成功率/月均增量）、知识点覆盖分析（各学科知识点覆盖率/缺口识别）、文档利用效率（热门文档排行/僵尸文档识别）、数据处理时效（PDF 解析耗时/CSV 导入吞吐量/图谱融合批处理耗时趋势） |
| **独立原因** | 运营分析面向运营人员（非教学场景），其关注点（系统效果度量、ROI 量化）与教学应用逻辑正交。将运营统计独立为模块，避免污染 M6 应用服务的单一职责，同时便于独立演进和数据看板定制 |

---

### 2.10 模块依赖关系

```
                        ┌──────────┐
                        │  M7 运维  │  ← 横切所有模块的监控、保障与通知
                        └────┬─────┘
                             │ 监控
    ┌────────────────────────┼────────────────────────┐
    │                        │                        │
    ▼                        ▼                        ▼
┌────────┐     ┌──────────┐                  ┌────────────┐
│ M1 主数据│◀────│ M6 应用   │                  │ M5 AI 分析  │
└───┬────┘ 权限  └──┬──┬────┘                  └─────┬──────┘
    │               │  │                              │
    │      编排调用   │  │ 调用                          │ 调用
    │               │  ▼                              ▼
    │               │ ┌──────────┐       ┌────────────────────────┐
    │               │ │ M8 运营   │       │   M4 · 图谱核心引擎      │
    │               │ └────┬─────┘       │  (融合 ← 剪枝 ← 权重更新) │
    │               │      │             └──────────┬─────────────┘
    │               │      │ 依赖                    │ 依赖
    │               │      │                        │
    │          ┌────┼──────┼────────────┐           │
    │          ▼    ▼      ▼            ▼           │
    │     ┌────────┐  ┌────────┐                   │
    └────▶│ M2 知识 │  │ M3 入库 │◀──────────────────┘
          └────────┘  └───┬────┘
                          │ 产出数据注入
                          ▼
                ┌────────────────┐
                │ M2 知识体系(更新) │
                │ M1 主数据(关联)   │
                └────────────────┘
```

**依赖方向总结**：

| 依赖链 | 说明 |
|--------|------|
| M3 → M1, M2 | 入库引擎依赖主数据（学生学号匹配）和知识体系（知识点名称匹配） |
| M4 → M1, M2, M3 | 图谱核心引擎消费三个数据模块的产出，融合为宽图谱；M4 权重引擎还需读取 M3 的得分分配规则来计算知识点级权重 |
| M5 → M4 | AI 分析引擎从图谱核心引擎获取剪枝子图 |
| M5 → M2 | AI 分析引擎需要知识体系（前置依赖链）生成补救路径 |
| M5 → M3 | AI 分析引擎的教学建议器需要从 M3 检索教辅中的例题和练习题 |
| M6 → M1, M4, M5, M8 | 应用服务编排调用图谱引擎和 AI 引擎的能力，同时依赖 M1 进行权限校验（教师只能操作自己班级，学生只能查看自己数据） |
| M6 → M8 | 应用服务的运营看板编排（IOperationsDashboardService）调用运营分析引擎获取统计数据 |
| M7 → 所有模块 | 运维横切监控所有模块的日志、任务、备份 |
| M8 → M1, M2, M3, M4 | 运营分析依赖主数据（学生统计）、知识体系（知识点基准）、入库引擎的文档处理数据和图谱核心引擎的统计数据 |

---

### 2.11 与技术架构支柱的映射

| 系统技术支柱 | 对应模块 |
|-------------|---------|
| 多维数据图谱化 | **M3** 数据入库引擎 |
| 宽图谱融合 | **M4-a** 宽图谱融合 + **M2** 知识体系（实体对齐） |
| 任务驱动剪枝 | **M4-b** 剪枝引擎 + **M5-a** 上下文构建 |
| 动态更新 | **M4-c** 权重引擎 |
| 端到端问答集成 | **M5** AI 分析引擎 + **M6** 应用服务 |
| 运营效果度量 | **M8** 运营分析 |

---

## 三、模块接口定义

> 接口是模块间的**契约**。以下定义每个模块对外暴露的服务接口，包括方法签名（输入/输出）和约束条件。
> 内部实现可独立变化，只要不改变接口契约。

### 3.1 M1 · 主数据管理服务 (IMasterDataService)

> **消费者**：M3 数据入库引擎、M4 图谱核心引擎、M6 应用服务、M8 运营分析

#### 3.1.1 学生管理 (IStudentService)

```
IStudentService
│
├── createStudent(StudentCreateRequest): Student
│   输入: {姓名, 学号, 班级ID, 入学年份}
│   输出: 创建成功的学生实体
│   约束: 学号全局唯一，重复时返回冲突错误
│
├── getStudent(studentId): Student
│   输入: 学生ID或学号
│   输出: 学生实体（含当前状态、班级信息）
│
├── searchStudents(query: StudentSearchQuery): List<Student>
│   输入: {关键词, 班级ID, 状态, 分页参数}
│   输出: 匹配的学生列表（支持按姓名/学号模糊搜索）
│
├── batchUpdateClass(studentIds: List<ID>, targetClassId: ID): BatchResult
│   输入: 学生ID列表 + 目标班级ID
│   输出: {成功数, 失败数, 失败明细}
│   约束: 目标班级必须存在
│
├── updateStatus(studentId: ID, status: StudentStatus): Student
│   输入: 学生ID + 新状态（在读/休学/转学/毕业）
│   输出: 更新后的学生实体
│   约束: 毕业/转学后标记为归档，不再参与权重计算
│
├── getStudentsByClass(classId: ID): List<Student>
│   输入: 班级ID
│   输出: 该班级下所有在读学生列表
│
└── importStudentsFromCSV(csvFile: File): ImportResult
    输入: CSV 文件
    输出: {成功数, 失败数, 异常记录及原因}
    约束: 自动校验字段格式，学号重复时标记冲突
```

#### 3.1.2 教师管理 (ITeacherService)

```
ITeacherService
│
├── createTeacher(TeacherCreateRequest): Teacher
│   输入: {姓名, 工号, 学科}
│   输出: 创建成功的教师实体
│
├── getTeacher(teacherId): Teacher
│   输入: 教师ID
│   输出: 教师实体（含任教科目、任教班级列表）
│
├── assignClasses(teacherId: ID, classIds: List<ID>): Teacher
│   输入: 教师ID + 班级ID列表
│   输出: 更新后的教师实体
│   约束: 教师只能操作自己任教班级的数据
│
├── getTeacherClasses(teacherId: ID): List<Class>
│   输入: 教师ID
│   输出: 该教师任教的所有班级
│
└── verifyClassOwnership(teacherId: ID, classId: ID): Boolean
    输入: 教师ID + 班级ID
    输出: 该教师是否任教该班级
    说明: M3/M6 调用此接口校验数据操作权限
```

#### 3.1.3 班级管理 (IClassService)

```
IClassService
│
├── createClass(ClassCreateRequest): Class
│   输入: {班级名称, 年级, 入学年份}
│
├── getClass(classId): Class
│   输出: 班级信息（含在读学生数）
│
├── listClasses(filter: ClassFilter): List<Class>
│   输入: {年级, 入学年份} 可选过滤
│
└── getStudentsByClass(classId: ID): List<Student>
    输出: 班级下所有在读学生
```

#### 3.1.4 授权服务 (IAuthorizationService)

```
IAuthorizationService
│
├── checkPermission(userId: ID, resourceType: ResourceType, resourceId: ID, action: Action): Boolean
│   输入: 用户ID + 资源类型(学生/班级/文档/报告) + 资源ID + 操作类型(读/写/删除/分享)
│   输出: 是否有权执行该操作
│   说明: 统一的权限校验入口，M6 应用层每个用例调用
│
├── getAccessibleScope(userId: ID, resourceType: ResourceType): AccessScope
│   输入: 用户ID + 资源类型
│   输出: 该用户可访问的资源范围（如教师只能访问自己任教班级的学生数据）
│   说明: 用于列表查询时自动过滤无权限数据
│
├── verifyShareAccess(linkId: ID, userId?: ID): Boolean
│   输入: 分享链接ID + 可选的访问者用户ID
│   输出: 该链接是否允许当前访问者查看
│   说明: 校验分享链接的权限级别、有效期和密码
│
└── grantPermission(userId: ID, resourceType: ResourceType, resourceId: ID, action: Action, expiry?: Date): Permission
    输入: 用户ID + 资源类型 + 资源ID + 操作类型 + 可选有效期
    输出: 创建的权限记录
    约束: 仅管理员可授予权限
```

---

### 3.2 M2 · 知识体系管理服务 (IKnowledgeSystemService)

> **消费者**：M3 数据入库引擎、M4 图谱核心引擎、M5 AI 分析引擎、M6 应用服务

#### 3.2.1 知识分类树 (IKnowledgeCategoryService)

```
IKnowledgeCategoryService
│
├── createCategory(CategoryCreateRequest): KnowledgeCategory
│   输入: {名称, 父分类ID, 层级(学科/模块/章节/知识点)}
│   输出: 创建的分类节点
│
├── getCategoryTree(subjectId?: ID): CategoryTreeNode
│   输入: 可选的学科ID（不传则返回全部分类树）
│   输出: 多级树形结构（学科 → 模块 → 章节 → 知识点）
│
├── moveCategory(categoryId: ID, newParentId: ID): KnowledgeCategory
│   输入: 分类ID + 新父分类ID
│   输出: 移动后的分类节点
│   约束: 不允许移动到自身的子分类下（防循环）
│
├── importCategoryTemplate(templateFile: File): ImportResult
│   输入: 分类模板文件
│   输出: {成功数, 失败数, 失败明细}
│
├── deleteCategory(categoryId: ID): DeleteResult
│   约束: 非叶子节点需先处理子节点
│
└── checkCompleteness(): CompletenessReport
    输出: {空节点列表, 孤立知识点列表, 循环依赖列表}
```

#### 3.2.2 知识点管理 (IKnowledgePointService)

```
IKnowledgePointService
│
├── createKnowledgePoint(KPCreateRequest): KnowledgePoint
│   输入: {名称, 描述, 学科归属, 所属分类ID, 来源标记}
│   输出: 创建的知识点实体
│
├── getKnowledgePoint(kpId): KnowledgePoint
│   输出: 知识点详情（含分类路径、前置依赖、关联关系）
│
├── searchKnowledgePoints(query: KPSearchQuery): List<KnowledgePoint>
│   输入: {关键词, 学科, 分类ID, 状态, 来源}
│   输出: 匹配的知识点列表（支持名称/描述模糊搜索）
│
├── updateKnowledgePoint(kpId, KPUpdateRequest): KnowledgePoint
│   输入: {名称, 描述} 可编辑字段
│
├── mergeKnowledgePoints(primaryId: ID, secondaryId: ID, mergedName?: String): KnowledgePoint
│   输入: 主实体ID + 副实体ID + 可选的合并后名称
│   输出: 合并后的知识点（副实体的关系迁移至主实体后删除副实体）
│   约束: 合并操作记录到日志，支持回滚
│
├── deprecateKnowledgePoint(kpId: ID): KnowledgePoint
│   输出: 标记为废弃的知识点（不再纳入剪枝和权重计算）
│
└── listKnowledgePoints(filter: KPFilter): List<KnowledgePoint>
    输入: {学科, 分类ID, 状态, 来源, 分页}
    输出: 知识点列表
```

#### 3.2.3 前置依赖管理 (IPrerequisiteService)

```
IPrerequisiteService
│
├── addPrerequisite(kpId: ID, prerequisiteKpId: ID): PrerequisiteRelation
│   输入: 知识点ID + 其前置依赖知识点ID
│   输出: 创建的前置依赖关系
│   约束: 自动检测循环依赖，存在循环时拒绝创建并返回循环路径
│
├── removePrerequisite(kpId: ID, prerequisiteKpId: ID): Boolean
│
├── getPrerequisites(kpId: ID, depth?: Int): List<KnowledgePoint>
│   输入: 知识点ID + 可选追溯深度（默认全部）
│   输出: 该知识点的所有前置依赖知识点（沿依赖链递归）
│   说明: M5 归因分析的核心依赖接口
│
├── getDependents(kpId: ID): List<KnowledgePoint>
│   输入: 知识点ID
│   输出: 所有以该知识点为前置依赖的后续知识点
│   说明: M6 复习路径"掌握后能解锁更多上层知识"的计算依据
│
├── getDependencyChain(kpId: ID): DependencyChain
│   输入: 知识点ID
│   输出: 完整的依赖有向图（节点集 + 边集），用于可视化和路径分析
│
└── detectCycles(): List<CyclePath>
    输出: 所有检测到的循环依赖路径
```

#### 3.2.4 实体对齐 (IEntityAlignmentService)

```
IEntityAlignmentService
│
├── getCandidates(confidenceRange?: Range, status?: AlignmentStatus): List<EntityAlignmentPair>
│   输入: 可选的置信度区间（高 >0.9 / 中 0.6-0.9 / 低 <0.6）+ 审核状态过滤
│   输出: 候选匹配对列表（系统基于名称相似度 + 属性重叠度自动生成）
│
├── confirmMerge(pairId: ID, mergeStrategy: MergeStrategy): KnowledgePoint
│   输入: 候选对ID + 合并策略（保留主实体名称 / 保留副实体名称 / 自定义名称）
│   输出: 合并后的实体
│   约束: 副实体的关系迁移至主实体后删除，操作记入日志
│
├── rejectPair(pairId: ID): EntityAlignmentPair
│   约束: 加入白名单，避免再次建议合并
│
├── batchConfirm(pairIds: List<ID>): BatchResult
│   输入: 候选对ID列表
│   输出: {成功数, 失败数}
│   约束: 执行前返回影响面摘要（将合并多少对、影响多少条关系）
│
├── batchReject(pairIds: List<ID>): BatchResult
│
└── rollbackMerge(mergeLogId: ID): RollbackResult
    输入: 合并操作日志ID
    输出: 恢复被删除的副实体、还原被迁移的关系
```

---

### 3.3 M3 · 数据入库引擎服务 (IDataIngestionService)

> **消费者**：M4 图谱核心引擎（消费入库产出的事件节点和文档图谱）
> **依赖**：M1 主数据服务（学生学号匹配）、M2 知识体系服务（知识点名称匹配）

#### 3.3.1 文档解析 (IDocumentIngestionService)

```
IDocumentIngestionService
│
├── uploadDocument(DocumentUploadRequest): Document
│   输入: {PDF文件, 学科分类, 上传人(管理员/教师)}
│   输出: 创建文档实体，状态为"待处理"
│   约束: 自动检测文件格式和大小，超限提示压缩
│
├── batchUpload(files: List<File>, subjectId: ID): List<Document>
│   输入: 多个PDF文件 + 学科分类
│   输出: 文档列表，各自进入处理流水线
│
├── getDocument(docId): Document
│   输出: 文档详情（含处理状态、解析结果摘要）
│
├── listDocuments(filter: DocumentFilter): List<Document>
│   输入: {上传时间, 学科分类, 处理状态, 排序方式}
│
├── getParseResult(docId): ParseResult
│   输出: {抽取实体数量与类型分布, 关系数量与类型分布, 处理耗时, 置信度分布, 低置信度实体列表}
│
├── reUpload(docId: ID, newFile: File): Document
│   约束: 覆盖旧版本文档，级联清除旧图谱数据后重新解析
│
├── deleteDocument(docId: ID): DeleteResult
│   约束: 级联清除该文档的所有图谱数据（抽取的实体和关系）
│
└── getDocumentGraph(docId): DocumentSubgraph
    输出: 从该文档中抽取的知识子图（节点集 + 关系集），供 M2/M6 浏览
```

#### 3.3.2 事件导入 (IEventIngestionService)

```
IEventIngestionService
│
├── importGradesCSV(csvFile: File, importConfig: ImportConfig): ImportResult
│   输入: CSV文件 + {得分粒度, 得分分配规则ID, 冲突策略(自动创建学生/跳过)}
│   输出: {成功条数, 失败条数, 异常记录及原因}
│   处理链: 校验字段格式 → 匹配学生(M1) → 匹配知识点(M2) → 生成考试事件节点 → 关联图谱
│   约束: 学号不存在时按冲突策略处理；知识点不匹配时标记为"待确认"
│
├── importAssignments(AssignmentImportRequest): ImportResult
│   输入: {CSV文件或手动录入数据, 班级ID, 作业名称, 教师ID}
│   输出: {成功条数, 失败条数, 异常记录}
│   约束: 调用 M1.verifyClassOwnership 校验教师-班级权限
│
├── appendAssignmentData(eventId: ID, additionalData: AssignmentAppend): UpdateResult
│   输入: 已有作业事件ID + 补充数据（批改结果/补交记录）
│   输出: 更新后的事件
│   约束: 增量更新，匹配已有节点，不产生重复
│
├── importQuizResults(QuizImportRequest): ImportResult
│   输入: {班级ID, 测验名称, 关联知识点列表, 学生得分列表, 教师ID}
│   输出: {成功数, 失败数}
│   约束: 教师只能录入自己任教班级
│
├── saveQuizTemplate(template: QuizTemplate): QuizTemplate
│   输入: {测验名称, 关联知识点, 题目结构}
│   输出: 保存的模板（下次一键调用）
│
└── getQuizTemplates(teacherId: ID): List<QuizTemplate>
    输出: 教师保存的测验模板列表
```

#### 3.3.3 关联模型定义 (IAssociationModelService)

```
IAssociationModelService
│
├── defineExamStructure(ExamStructureRequest): ExamPaper
│   输入: {考试名称, 大题列表[{大题名, 小题列表[{小题名, 分值, 关联知识点IDs}]}]}
│   输出: 试卷结构实体
│   约束: 知识点从 M2 知识体系中选择，不匹配的标记为"待确认"
│
├── configureScoreGranularity(config: ScoreGranularityConfig): Void
│   输入: 得分粒度选项（仅总分 / 每题得分 / 每知识点得分）
│   约束: 全局生效或按学科/考试类型分别设置
│
├── configureScoreAllocation(config: ScoreAllocationConfig): Void
│   输入: 得分分配策略（平均分配 / 按权重比例 / 全部计入）
│   约束: 全局生效或按学科/考试类型分别设置
│
└── validateImportData(csvFile: File, modelId: ID): ValidationResult
    输入: CSV 文件 + 关联模型ID
    输出: {题目数量是否匹配, 知识点名称是否可映射, 得分格式是否合法, 异常明细}
    说明: 导入前调用，预检数据结构合规性
```

---

### 3.4 M4 · 图谱核心引擎服务 (IGraphCoreService)

> **消费者**：M5 AI 分析引擎、M6 应用服务、M8 运营分析
> **依赖**：M1 主数据、M2 知识体系、M3 数据入库引擎

#### 3.4.1 宽图谱融合 (IGraphFusionService)

```
IGraphFusionService
│
├── buildWideGraph(scope?: FusionScope): FusionResult
│   输入: 可选的融合范围（全量/增量/指定数据源）
│   输出: {新增节点数, 新增关系数, 合并节点数, 融合耗时}
│   说明: 以"学生"和"知识点"为图钉，融合文档图谱与事件图谱
│
├── getGraphOverview(): GraphOverview
│   输出: {总节点数, 总关系数, 平均度, 连通分量数, 按实体类型统计}
│
├── getGraphHealthMetrics(): HealthMetrics
│   输出: {孤立节点列表, 关系密度热力图数据, 各学科区域边密度}
│
├── computePageRank(entityType: EntityType, topK: Int): List<RankedEntity>
│   输入: 实体类型 + Top-K 数量
│   输出: 按 PageRank 排序的实体列表（含分值）
│   说明: M6 复习路径"知识重要度"的计算依据
│
├── getCommunityDetection(subjectId?: ID): List<Community>
│   输入: 可选的学科过滤
│   输出: 社区/聚类结构列表（每个社区包含成员节点和内部密度）
│
└── getNodeNeighborhood(nodeId: ID, hops: Int, filter?: NodeFilter): Subgraph
    输入: 起始节点ID + 跳数 + 可选过滤条件
    输出: 该节点的邻域子图
    说明: M6 图谱可视化下钻的核心接口
```

#### 3.4.2 剪枝引擎 (IPruningService)

```
IPruningService
│
├── prune(targetNodeId: ID, taskType: TaskType, strategyId?: ID): Subgraph
│   输入: 目标节点ID + 任务类型（归因分析/复习推荐/班级概览）+ 可选策略ID覆盖
│   输出: 剪枝后的子图（节点集 + 关系集 + 元数据）
│   处理链: 加载策略模板 → 按跳数展开 → 按邻居数截断 → 按权重阈值过滤 → 按关系类型过滤
│   说明: M5 上下文构建的核心输入
│
├── createStrategy(StrategyCreateRequest): PruningStrategy
│   输入: {任务类型, 最大跳数, 每跳最大邻居数, 权重阈值, 关系类型白名单}
│
├── updateStrategy(strategyId: ID, StrategyUpdateRequest): PruningStrategy
│   约束: 自动保存历史版本
│
├── getStrategy(taskType: TaskType): PruningStrategy
│   输出: 该任务类型的当前生效策略
│
├── getStrategyHistory(strategyId: ID): List<StrategyVersion>
│   输出: 该策略的所有历史版本（含变更 Diff）
│
├── rollbackStrategy(strategyId: ID, versionId: ID): PruningStrategy
│   约束: 回滚到指定版本
│
├── compareStrategy(targetNodeId: ID, strategyIdA: ID, strategyIdB: ID): CompareResult
│   输入: 同一目标节点 + 两个策略ID
│   输出: {策略A节点数, 策略A关系数, 策略A预估Token消耗, 策略B同指标, 差异分析}
│   说明: 辅助管理员判断策略优劣
│
└── listStrategies(): List<PruningStrategy>
    输出: 所有剪枝策略列表（按任务类型分组）
```

#### 3.4.3 权重引擎 (IWeightService)

```
IWeightService
│
├── getMasteryWeight(studentId: ID, kpId: ID): MasteryRelation
│   输入: 学生ID + 知识点ID
│   输出: 当前掌握关系（含权重值、最后更新时间、最后触发原因）
│
├── adjustWeightByEvent(eventId: ID): WeightChangeResult
│   输入: 事件ID（考试/作业/测验事件）
│   输出: {变更的掌握关系列表, 每条的权重前后值}
│   处理链: 读取事件数据 → 匹配权重规则 → 计算权重调整 → 更新图谱 → 记录日志
│   说明: 每次事件入库后由 M3 触发调用
│
├── executeTimeDecay(scope: DecayScope): DecayResult
│   输入: 衰减范围（全局/指定学科/指定班级）
│   输出: {受影响的关系数, 权重变化分布}
│   约束: 由定时任务调用，按配置的衰减曲线和半衰期执行
│
├── configureDecayRule(DecayRuleConfig): TimeDecayRule
│   输入: {衰减曲线类型, 半衰期, 计算周期, 适用范围(全局/按学科)}
│
├── configureBehaviorRule(BehaviorRuleConfig): BehaviorWeightRule
│   输入: {事件类型, 调整幅度(+Δ/-Δ), 生效范围(仅当前/影响前置依赖链)}
│
├── simulateRule(ruleId: ID, scope: SimulateScope): SimulationResult
│   输入: 规则ID + 模拟范围（指定学生/班级/全校）
│   输出: {受影响关系数, 增强比例, 削弱比例, 预估影响摘要}
│   说明: 规则变更前评估影响范围，避免衰减过激
│
├── getWeightHistory(studentId: ID, kpId: ID, timeRange: TimeRange): List<WeightChangeLog>
│   输入: 学生ID + 知识点ID + 时间范围
│   输出: 权重变更历史列表（含时间、前后值、触发原因、操作来源）
│   说明: M6 趋势曲线的核心数据源
│
└── manualOverride(studentId: ID, kpId: ID, newWeight: Float, reason: String): MasteryRelation
    输入: 学生ID + 知识点ID + 手动设定的权重值 + 修正原因
    约束: 记录操作来源为"管理员手动修正"
```

---

### 3.5 M5 · AI 分析引擎服务 (IAIAnalysisService)

> **消费者**：M6 应用服务
> **依赖**：M4 图谱核心引擎（剪枝子图、权重数据）、M2 知识体系（前置依赖链）

#### 3.5.1 上下文构建 (IContextBuilderService)

```
IContextBuilderService
│
├── buildContext(subgraph: Subgraph, taskType: TaskType, extraInfo?: ContextExtra): LLMContext
│   输入: 剪枝子图 + 任务类型 + 可选附加信息（历史趋势、教辅引用等）
│   输出: LLM 可用的结构化上下文（文本/JSON 格式，控制在 Token 预算内）
│   处理链: 子图序列化 → 上下文裁剪(Token预算) → 多源信息融合 → 格式化
│
├── estimateTokenCount(subgraph: Subgraph, taskType: TaskType): Int
│   输入: 子图 + 任务类型
│   输出: 预估 Token 消耗量
│   说明: 辅助 M4 剪枝策略的 A/B 对比测试
│
└── getContextTemplate(taskType: TaskType): ContextTemplate
    输出: 该任务类型的上下文模板结构说明
```

#### 3.5.2 LLM 网关 (ILLMGatewayService)

```
ILLMGatewayService
│
├── callLLM(LLMCallRequest): LLMCallResult
│   输入: {任务类型, Prompt模板ID, 变量值, 模型覆盖(可选)}
│   输出: {LLM 响应文本, Token 消耗(输入/输出), 响应延迟, 调用结果}
│   处理链: 加载模板 → 填充变量 → 路由到目标模型 → 调用 API → 记录日志
│   约束: 超配额时触发降级策略
│
├── switchModel(taskType: TaskType, modelId: String): ModelConfig
│   输入: 任务类型 + 新模型标识
│   输出: 更新后的模型配置
│   约束: 切换时自动校验新模型 API 连通性，失败则拒绝切换
│
├── getModelConfig(taskType: TaskType): ModelConfig
│   输出: 该任务类型的当前模型配置（模型名称、配额、降级策略）
│
├── getQuotaStatus(modelId: String): QuotaStatus
│   输出: {当前周期调用量, Token 消耗量, 配额上限, 预估费用, 剩余配额}
│
├── getCallLogs(filter: CallLogFilter): List<LLMCallLog>
│   输入: {时间范围, 任务类型, 模型名称, 调用结果, 分页}
│   输出: 调用记录列表
│
├── replayCall(callLogId: ID): LLMCallResult
│   输入: 历史调用记录ID
│   输出: 使用相同参数重新调用的结果
│   说明: 失败调用的重放调试
│
├── updatePromptTemplate(taskType: TaskType, template: PromptTemplate): PromptTemplate
│   输入: 任务类型 + 新模板内容（支持变量占位符）
│   约束: 自动保存版本历史，支持回滚
│
├── getPromptTemplate(taskType: TaskType): PromptTemplate
│   输出: 当前生效的 Prompt 模板（含版本信息）
│
└── previewPrompt(templateId: ID, testVariables: Map): String
    输入: 模板ID + 测试变量值
    输出: 渲染后的完整 Prompt 文本（预览，不实际调用 LLM）
```

#### 3.5.3 分析生成器 (IAnalysisGeneratorService)

```
IAnalysisGeneratorService
│
├── generateAttribution(AttributionRequest): AttributionReport
│   输入: {学生ID, 知识点ID, 分析深度, 任务类型(归因分析/薄弱溯源/知识缺口诊断)}
│   输出: 归因报告 {
│     根因列表: [{根因描述, 影响程度, 证据链: [{维度, 数据, 引用来源}], 置信度}],
│     追溯路径: [{当前知识点 → 前置知识点1 → 前置知识点2 → ...}]
│   }
│   处理链: 调用 M4 剪枝(归因策略) → 上下文构建 → LLM 调用 → 结果结构化
│
├── drillDown(attributionId: ID, targetKpId: ID): AttributionReport
│   输入: 已有归因报告ID + 需下钻的前置知识点ID
│   输出: 更深层的链式归因分析
│   说明: 支持交互式追问（如"为什么配方法也薄弱？"）
│
├── generateTeachingSuggestion(AttributionReport, studentId: ID): TeachingSuggestion
│   输入: 归因报告 + 学生ID
│   输出: {
│     补救路径: [{步骤, 知识点, 教辅引用(页码/题号), 难度, 建议题型}],
│     补救强度: (轻度3步/中度5步/重度全链重建),
│     配套资源: [{资源类型, 来源文档, 页码, 难度等级}]
│   }
│   处理链: 读取前置依赖链(M2) → 匹配教辅资源(M3) → LLM 生成教学步骤
│
├── generateReviewPath(studentId: ID, availableTime?: Int): ReviewPath
│   输入: 学生ID + 可选的可用复习时间（小时）
│   输出: {
│     排序列表: [{知识点, 优先级得分, 推荐理由, 预估时间, 配套资源}],
│     总预估时长,
│     截取范围(若指定了可用时间)
│   }
│   处理链: 读取所有薄弱知识点(M4) → 计算优先级(薄弱度+PageRank+依赖位置) → 时间预估 → 资源匹配
│
└── predictTrend(studentId: ID, kpIds: List<ID>, horizonMonths: Int): List<TrendPrediction>
    输入: 学生ID + 知识点列表 + 预测月数（默认1-2月）
    输出: [{知识点, 预测趋势线, 置信区间, 高风险标记}]
    说明: 基于历史权重曲线的时间序列预测
```

---

### 3.6 M6 · 应用服务 (IApplicationService)

> **消费者**：终端用户（教师、学生）
> **依赖**：M1 主数据管理（权限校验）、M2 知识体系、M4 图谱核心引擎、M5 AI 分析引擎、M8 运营分析（运营看板编排）

#### 3.6.1 归因查询 (IAttributionQueryService)

```
IAttributionQueryService
│
├── queryAttribution(query: AttributionQuery): AttributionReport
│   输入: {学生ID或自然语言描述, 知识点关键词, 任务类型}
│   输出: 归因报告（复用 M5 的输出）
│   处理链: 意图识别 → 路由到对应剪枝策略(M4) → 调用 M5 分析生成 → 返回报告
│
├── autoComplete(input: String): List<Suggestion>
│   输入: 用户输入的关键词片段
│   输出: 匹配的学生姓名和知识点名称列表（实时自动补全）
│
└── getDrillDown(attributionId: ID, kpId: ID): AttributionReport
    说明: 透传 M5 的 drillDown 接口
```

#### 3.6.2 班级分析 (IClassAnalysisService)

```
IClassAnalysisService
│
├── getClassWeaknessOverview(scope: ClassScope): WeaknessOverview
│   输入: {班级ID/年级/自定义分组}
│   输出: {
│     薄弱知识点TopN: [{知识点, 薄弱指数, 薄弱学生数, 占比}],
│     班级vs年级对比数据,
│     当前vs上学期同期变化
│   }
│   处理链: 读取范围内所有学生的掌握关系(M4) → 计算薄弱指数 → 排名 → 对比分析
│
├── drillDownToStudents(kpId: ID, scope: ClassScope): List<StudentWeakness>
│   输入: 知识点ID + 范围
│   输出: 该知识点下薄弱学生排序列表（含掌握权重）
│   说明: 教师可从此列表一键跳转至单学生归因分析
│
└── getWeaknessDistribution(classId: ID, subjectId: ID): WeaknessDistribution
    输出: 薄弱知识点分布的雷达图/柱状图数据
```

#### 3.6.3 可视化与趋势 (IVisualizationService)

```
IVisualizationService
│
├── getStudentKnowledgeGraph(studentId: ID, filter?: GraphFilter): StudentSubgraph
│   输入: 学生ID + 可选过滤（学科, 掌握程度区间, 关系类型）
│   输出: 学生个体知识图谱 {
│     节点: [{知识点, 掌握权重, 颜色(绿/红/灰), 大小(PageRank)}],
│     边: [{关系类型, 强度}]
│   }
│
├── saveGraphSnapshot(studentId: ID, snapshotName: String): GraphSnapshot
│   输出: 保存的图谱视图快照
│
├── compareSnapshots(snapshotIdA: ID, snapshotIdB: ID): SnapshotCompareResult
│   输出: 两个时间点的图谱对比（权重变化、新增/消失节点）
│
├── getWeightTrend(studentId: ID, kpIds: List<ID>, timeRange: TimeRange): TrendData
│   输入: 学生ID + 知识点列表 + 时间范围
│   输出: {
│     趋势曲线: [{时间, 权重值, 关联事件标注}],
│     趋势标签: (持续上升/持续下降/波动/平台期),
│     班级平均参照线
│   }
│   处理链: 读取权重变更历史(M4) → 识别趋势模式 → 叠加班级平均线
│
└── getCrossSubjectAnalysis(studentId: ID): CrossSubjectResult
    输出: {
│     关联路径: [{学科A知识点, 学科B知识点, 关联方向, 关联系数}],
│     假性薄弱标记: [{表面薄弱知识点, 实际根因知识点, 根因学科}]
│   }
    说明: 自动检测跨学科薄弱但存在内容关联的知识点对
```

#### 3.6.4 报告导出与分享 (IReportExportService)

```
IReportExportService
│
├── exportReport(reportId: ID, format: ExportFormat, sections: List<Section>): ExportFile
│   输入: 报告ID + 格式(PDF/HTML/Markdown) + 包含章节选择
│   输出: 导出的文件
│   可选章节: 根因分析 / 证据链 / 图谱可视化 / 补救建议 / 趋势曲线
│
├── generateShareLink(reportId: ID, permission: SharePermission, expiry?: Int): ShareLink
│   输入: 报告ID + 权限(本校教师/指定家长/公开) + 有效期(天)
│   输出: 加密分享链接
│
├── batchExportReports(studentIds: List<ID>, format: ExportFormat): BatchExportResult
│   输入: 学生ID列表 + 格式
│   输出: 打包下载文件
│   约束: 自动去除敏感内部信息（其他学生对比数据）
│
└── getShareStats(linkId: ID): ShareStats
    输出: {访问次数, 访问者列表, 家长已查看/未查看状态}
```

#### 3.6.5 教师个人知识库 (IPersonalKBService)

```
IPersonalKBService
│
├── uploadPersonalDoc(teacherId: ID, pdfFile: File): Document
│   约束: 生成的图谱初始仅归入教师个人空间，不影响全校图谱
│
├── listPersonalDocs(teacherId: ID): List<Document>
│   输出: 教师个人知识库中的文档列表
│
├── applyForPublic(docId: ID): PublicApplication
│   输出: 公开申请（提交后通知 M2 管理员审核）
│
├── deletePersonalDoc(docId: ID): DeleteResult
│   约束: 教师只能管理自己上传的文档
│
└── getPersonalDocGraph(docId: ID): DocumentSubgraph
    输出: 个人文档的知识图谱（复用 M3 的文档图谱浏览能力）
```

#### 3.6.6 学生自助服务 (IStudentSelfService)

```
IStudentSelfService
│
├── getMasteryRadar(studentId: ID, subjectId?: ID): RadarData
│   输出: 各知识模块的掌握评分雷达图（0-100），红色高亮核心薄弱点
│
├── getWeaknessList(studentId: ID, subjectId?: ID): List<WeaknessItem>
│   输出: [{知识点, 掌握评分, 班级平均分对比(±差值), 趋势箭头(↑↓→)}]
│
├── getClassComparison(studentId: ID): ClassComparisonResult
│   输出: {各知识模块的排名百分位, 优势模块(前25%), 需加强模块(后25%)}
│   约束: 保护隐私，不显示其他学生具体排名
│
├── getLearningTimeline(studentId: ID, filter: TimelineFilter): TimelineData
│   输入: {学科, 时间段(本学期/本学期+上学期/全部)}
│   输出: {知识点掌握权重变化时间轴, 关键考试节点标注}
│
├── getProgressSummary(studentId: ID): ProgressSummary
│   输出: {
│     持续进步: [{知识点, 变化幅度}],
│     仍需加强: [{知识点, 当前权重, 无上升趋势}],
│     稳定掌握: [{知识点, 权重>0.7}]
│   }
│
├── markReviewed(studentId: ID, kpId: ID): Void
│   说明: 学生标记"已复习"，系统在下次成绩导入后对比权重变化
│
└── getReviewEffect(studentId: ID): ReviewEffectReport
    输出: [{知识点, 复习前权重, 复习后权重, 提升量, 复习投入产出比}]
```

#### 3.6.7 运营看板编排 (IOperationsDashboardService)

```
IOperationsDashboardService
│
├── getDashboardOverview(timeRange: TimeRange): DashboardOverview
│   输入: 时间范围
│   输出: 运营看板首页聚合数据 {
│     文档产能摘要, 知识点覆盖摘要, 文档利用效率摘要, 处理时效摘要,
│     系统规模概览(总学生数/总知识点数/总事件数/总节点数)
│   }
│   处理链: 编排 M8 的多个统计接口 → 聚合为看板首页数据 → 组装 VO
│   说明: 运营看板首页的一站式数据入口
│
├── getDocumentThroughputView(timeRange: TimeRange): ThroughputView
│   输入: 时间范围
│   输出: 文档产能统计视图数据 {
│     累计上传数量, 解析成功率, 累计解析页数, 月均增量,
│     堆叠柱状图数据(每月新增vs累计), CSV导入总行数与覆盖学生数
│   }
│   处理链: 调用 M8.getDocumentThroughput → 格式化图表数据
│
├── getKnowledgeCoverageView(subjectId?: ID): CoverageView
│   输入: 可选学科过滤
│   输出: 知识点覆盖视图数据 {
│     树形图数据(各学科知识点覆盖数量),
│     覆盖率百分比, 知识点缺口列表
│   }
│   处理链: 调用 M8.getKnowledgeCoverage → 组装树形图/缺口列表
│
├── getDocumentUtilizationView(): UtilizationView
│   输出: 文档利用效率视图数据 {
│     热门文档排行(含浏览/引用次数),
│     僵尸文档列表(入库超90天未被访问)
│   }
│   处理链: 调用 M8.getDocumentUtilization → 排序/标记/格式化
│
└── getProcessingLatencyView(timeRange: TimeRange): LatencyView
    输入: 时间范围
    输出: 数据处理时效视图数据 {
      PDF解析平均耗时趋势, CSV导入吞吐量(行/秒),
      图谱融合批处理耗时趋势, 性能瓶颈环节标注
    }
    处理链: 调用 M8.getProcessingLatency → 格式化趋势图数据
```

> **分层意义**：此接口作为 L2 应用层对 P5 运营看板的编排入口，确保 P5（L1 表示层）通过 L2 应用层访问 M8（L3 领域层），维持 L1 → L2 → L3 的单向依赖规则，与 P2/P3 → M6 → M4/M5 的分层模式一致。

---

### 3.7 M7 · 系统运维服务 (ISystemOpsService)

> **消费者**：运维人员（直接操作）
> **横切依赖**：监控所有模块的日志、任务、备份

#### 3.7.1 日志服务 (ILogService)

```
ILogService
│
├── searchLogs(query: LogSearchQuery): List<SystemLog>
│   输入: {时间范围, 服务名称, 日志级别, TraceID, 关键字(支持AND/OR/NOT/正则)}
│   输出: 匹配的日志列表（时间倒序，高亮命中词）
│
├── getLogContext(logId: ID, contextLines: Int): LogContext
│   输入: 日志ID + 上下文行数（默认50）
│   输出: 该条日志前后的日志上下文
│
├── getTraceChain(traceId: ID): TraceChain
│   输入: Trace ID
│   输出: 该请求的完整调用链路 [{服务, 操作, 耗时, 状态}]
│   说明: API → 剪枝查询 → Neo4j Cypher → LLM Prompt → 响应组装
│
├── getSlowQueries(threshold?: Duration): List<SlowQuery>
│   输入: 可选阈值（默认 Cypher>1s, LLM>10s, 子图>500节点）
│   输出: 慢查询列表（含执行计划/调用参数）
│
├── exportLogs(query: LogSearchQuery, format: ExportFormat): ExportFile
│   输出: JSON 或 CSV 格式的日志文件
│
└── saveSearchSnapshot(name: String, query: LogSearchQuery): SearchSnapshot
    说明: 保存常用搜索条件，一键加载复用
```

#### 3.7.2 备份服务 (IBackupService)

```
IBackupService
│
├── configureBackupPolicy(policy: BackupPolicyConfig): BackupPolicy
│   输入: {备份周期, 备份类型(全量/增量), 保留策略(最近N份), 环境(生产/测试)}
│
├── triggerManualBackup(label?: String): BackupTask
│   输入: 可选的备份标签（如"实体对齐操作前"）
│   输出: 备份任务（含进度、预计完成时间）
│
├── getBackupList(filter?: BackupFilter): List<GraphBackup>
│   输入: 可选过滤（时间范围、备份类型、状态）
│   输出: 备份清单 [{时间, 数据量, 文件大小, 校验和, 完整性状态}]
│
├── verifyBackupIntegrity(backupId: ID): IntegrityResult
│   输出: {节点计数对比, 关系计数对比, 校验和比对, 是否通过}
│
├── restoreBackup(backupId: ID, target: RestoreTarget): RestoreResult
│   输入: 备份ID + 恢复目标（覆盖当前库 / 恢复到新库）
│   输出: 恢复报告 {恢复节点数, 恢复关系数, 数据一致性校验}
│   约束: 恢复前展示预览，需二次确认
│
└── deleteBackup(backupId: ID): DeleteResult
    约束: 保留策略内的备份不允许删除
```

#### 3.7.3 定时任务服务 (IScheduledTaskService)

```
IScheduledTaskService
│
├── listTasks(filter?: TaskFilter): List<ScheduledTask>
│   输入: 可选过滤（状态、服务分组）
│   输出: [{任务名, Cron表达式, 下次执行时间, 最近5次执行状态, 当前状态}]
│
├── triggerManually(taskId: ID): TaskExecutionRecord
│   输出: 手动触发的执行记录
│   约束: 无视 Cron 表达式，立即执行
│
├── pauseTask(taskId: ID): ScheduledTask
│   约束: 暂停后不再按 Cron 触发，但保留配置
│
├── resumeTask(taskId: ID): ScheduledTask
│
├── updateCron(taskId: ID, cronExpression: String): ScheduledTask
│
├── getExecutionHistory(taskId: ID, limit?: Int): List<TaskExecutionRecord>
│   输出: [{开始时间, 结束时间, 耗时, 处理数据量, 日志, 失败诊断}]
│
├── getTaskDependencyDAG(): TaskDAG
│   输出: 任务依赖拓扑图（DAG 结构）
│   说明: 上游任务失败时自动跳过下游，记录跳过原因
│
└── configureDependency(upstreamTaskId: ID, downstreamTaskId: ID): Void
    约束: 不允许形成循环依赖
```

#### 3.7.4 通知服务 (INotificationService)

```
INotificationService
│
├── sendNotification(NotificationRequest): NotificationResult
│   输入: {接收人ID, 通知类型(邮件/站内消息/Webhook), 标题, 内容, 优先级(高/中/低)}
│   输出: {发送状态, 发送时间, 失败原因(如有)}
│   说明: 统一通知发送入口，各模块通过此接口触发通知
│
├── getNotifications(userId: ID, filter?: NotificationFilter): List<Notification>
│   输入: 用户ID + 可选过滤（未读/已读/类型/时间范围）
│   输出: 通知列表
│
├── markAsRead(notificationId: ID): Void
│
├── configureNotificationPreference(userId: ID, preference: NotificationPreference): Void
│   输入: 用户ID + 偏好设置（接收渠道、免打扰时段、订阅的通知类型）
│
└── getNotificationTemplates(): List<NotificationTemplate>
    输出: 系统预定义的通知模板列表（如"PDF 解析完成""公开审核待处理""备份失败告警"）
    说明: L4 基础设施层的通知适配器（邮件/站内消息/Webhook）负责实际发送
```

---

### 3.8 M8 · 运营分析服务 (IOperationsAnalyticsService)

> **消费者**：M6 应用服务（IOperationsDashboardService 编排调用，面向运营人员）
> **依赖**：M1 主数据管理（学生统计）、M2 知识体系（知识点基准）、M3 数据入库引擎（文档处理数据）、M4 图谱核心引擎（图谱统计数据）

```
IOperationsAnalyticsService
│
├── getDocumentThroughput(timeRange: TimeRange): DocumentThroughputStats
│   输入: 时间范围
│   输出: {累计PDF上传数量, 解析成功率, 累计解析页数, 月均增量, 每月新增vs累计增长数据}
│
├── getKnowledgeCoverage(subjectId?: ID): CoverageReport
│   输入: 可选的学科ID过滤
│   输出: {
│     各学科知识点覆盖数量,
│     覆盖率(已覆盖/应有),
│     知识点缺口(已定义但未被任何文档覆盖的模块),
│     累计抽取实体和关系总数
│   }
│   说明: 依赖 M2 知识体系获取"应有知识点"基准
│
├── getDocumentUtilization(): UtilizationReport
│   输出: {
│     热门文档排行: [{文档, 浏览/引用次数}],
│     僵尸文档列表: [{文档, 入库时间, 已入库天数}],
│     筛选条件: 已入库超90天且从未被浏览或引用
│   }
│   处理链: 读取文档访问记录(M8) → 聚合统计 → 标记僵尸文档
│
├── getProcessingLatency(timeRange: TimeRange): LatencyReport
│   输入: 时间范围
│   输出: {
│     PDF上传到解析完成的平均耗时,
│     CSV导入到事件节点生成的吞吐量(行/秒),
│     图谱融合批处理耗时趋势,
│     性能瓶颈环节识别
│   }
│
├── getImportSummary(timeRange: TimeRange): ImportSummary
│   输入: 时间范围
│   输出: {CSV成绩记录导入总行数, 覆盖学生数, 各事件类型导入统计}
│
└── getSystemOverview(): SystemOverviewReport
    输出: 全局系统概览 {总文档数, 总学生数, 总知识点数, 总事件数, 图谱节点/关系总数}
    说明: 综合 M1/M2/M3/M4 的统计数据
```

---

### 3.9 模块间接口调用矩阵

> 行 = 提供方（暴露接口的模块），列 = 消费方（调用接口的模块），单元格 = 被调用的接口

| 提供方 ↓ \ 消费方 → | M3 入库 | M4 图谱 | M5 AI | M6 应用 | M8 运营 |
|:--|:--|:--|:--|:--|:--|
| **M1 主数据** | IStudentService.searchStudents / IAuthorizationService.checkPermission | IStudentService.getStudent (批量) | — | IStudentService / ITeacherService / IClassService / IAuthorizationService | IStudentService（学生统计） |
| **M2 知识体系** | IKnowledgePointService.searchKnowledgePoints | IPrerequisiteService.getDependencyChain | IPrerequisiteService.getPrerequisites | IKnowledgePointService / IKnowledgeCategoryService | IKnowledgeCategoryService.getCategoryTree |
| **M3 入库引擎** | — | IDocumentIngestionService.getDocumentGraph / IEventIngestionService.* / IAssociationModelService.configureScoreAllocation | — | IDocumentIngestionService / IEventIngestionService / IAssociationModelService | IDocumentIngestionService.listDocuments / IEventIngestionService（导入统计） |
| **M4 图谱核心** | — | — | IPruningService.prune / IWeightService.getMasteryWeight | IGraphFusionService / IPruningService / IWeightService | IGraphFusionService.getGraphOverview |
| **M5 AI 分析** | — | — | — | IAnalysisGeneratorService / ILLMGatewayService / IContextBuilderService | — |
| **M6 应用服务** | — | — | — | （终端用户直接消费） | IOperationsAnalyticsService.* (由 M6.IOperationsDashboardService 编排调用) |
| **M7 运维** | — | — | — | — （运维人员直接操作） | — |

---

## 四、分层架构

### 4.1 分层总览

基于 8 个模块的职责特征和依赖方向，系统采用 **四层架构 + 横切关注点** 的分层模式：

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                  L1 · 表示层 (Presentation)                  │    │
│  │                                                             │    │
│  │  面向 5 类用户角色的 UI 界面与交互                             │    │
│  │  管理后台 │ 教师工作台 │ 学生自助端 │ 运维控制台 │ 运营看板    │    │
│  └──────────────────────────┬──────────────────────────────────┘    │
│                             │ 用户操作                               │
│  ┌──────────────────────────▼──────────────────────────────────┐    │
│  │                  L2 · 应用层 (Application)                   │    │
│  │                                                             │    │
│  │  M6 · 应用服务                                              │    │
│  │  用例编排 · 权限校验 · 输入校验 · 结果组装                    │    │
│  └──────────────────────────┬──────────────────────────────────┘    │
│                             │ 编排调用                               │
│  ┌──────────────────────────▼──────────────────────────────────┐    │
│  │                  L3 · 领域层 (Domain)                        │    │
│  │                                                             │    │
│  │  ┌─ 领域核心 ──────────────┐  ┌─ 领域支撑 ──────────────┐   │    │
│  │  │  M4 · 图谱核心引擎       │  │  M1 · 主数据管理        │   │    │
│  │  │  M5 · AI 分析引擎        │  │  M2 · 知识体系管理      │   │    │
│  │  │  (融合/剪枝/权重/AI推理)  │  │  M3 · 数据入库引擎      │   │    │
│  │  │                         │  │  M8 · 运营分析           │   │    │
│  │  └─────────────────────────┘  └─────────────────────────┘   │    │
│  └──────────────────────────┬──────────────────────────────────┘    │
│                             │ 持久化 / 外部集成                      │
│  ┌──────────────────────────▼──────────────────────────────────┐    │
│  │                  L4 · 基础设施层 (Infrastructure)             │    │
│  │                                                             │    │
│  │  图数据库(Neo4j) │ LLM API │ 文件存储 │ 消息队列 │ 缓存      │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                     │
│  ╔═══════════════════════════════════════════════════════════════╗    │
│  ║              横切关注点 · M7 · 系统运维                       ║    │
│  ║     日志追踪 │ 数据备份 │ 定时任务 │ 监控告警 │ 审计 │ 通知  ║    │
│  ╚═══════════════════════════════════════════════════════════════╝    │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘

                         依赖方向：自上而下（单向）
                         L1 → L2 → L3 → L4
                         横切关注点独立于分层，跨层生效
```

---

### 4.2 L1 · 表示层 (Presentation Layer)

#### 职责

面向 5 类用户角色，提供差异化的界面与交互体验。表示层**只负责展示和用户交互**，不包含任何业务逻辑。

#### 界面划分

```
┌─────────────────────────────────────────────────────────────────┐
│                    L1 · 表示层                                   │
├────────────┬────────────┬───────────┬────────────┬──────────────┤
│  P1        │  P2        │  P3       │  P4        │  P5          │
│  管理后台   │  教师工作台  │  学生自助端 │  运维控制台  │  运营看板     │
│            │            │           │            │              │
│ 管理员      │  教师       │  学生      │  运维人员    │  运营人员      │
├────────────┼────────────┼───────────┼────────────┼──────────────┤
│• 教辅上传   │• 归因查询   │• 薄弱总览  │• 日志搜索   │• 文档产能统计  │
│• 成绩导入   │• 班级概览   │• 复习推荐  │• 备份管理   │• 知识点覆盖   │
│• 实体对齐   │• 知识图谱   │• 进度追踪  │• 定时任务   │• 文档利用率   │
│• 知识体系   │• 趋势追踪   │• 班级对比  │• LLM 配置   │• 处理时效     │
│• 剪枝策略   │• 教辅浏览   │• 复习效果  │• 性能告警   │              │
│• 权重规则   │• 报告导出   │           │            │              │
│• 全局图谱   │• 个人教辅   │           │            │              │
│• 模型定义   │• 作业/测验  │           │            │              │
└────────────┴────────────┴───────────┴────────────┴──────────────┘
```

#### 用户角色 → 界面对应

| 角色 | 界面 | 高频操作 | 低频操作 |
|------|------|---------|---------|
| 管理员 | P1 管理后台 | 教辅上传、成绩导入、实体对齐审核 | 剪枝策略调优、权重规则配置 |
| 教师 | P2 教师工作台 | 归因查询、班级概览、作业/测验录入 | 报告导出、教辅浏览 |
| 学生 | P3 学生自助端 | 薄弱总览、复习路径 | 进度追踪、复习效果 |
| 运维人员 | P4 运维控制台 | 日志搜索、备份管理 | 定时任务编排 |
| 运营人员 | P5 运营看板 | 查看统计面板 | — |

#### 设计约束

- 表示层**不直接调用** L3 领域层和 L4 基础设施层
- 所有请求通过 L2 应用层中转（包括 P5 运营看板 → M6.IOperationsDashboardService → M8）
- 界面状态管理（如表单校验、加载状态）在表示层内部处理
- 跨角色共享的 UI 组件（图谱可视化、趋势曲线）抽取为公共组件库

---

### 4.3 L2 · 应用层 (Application Layer)

#### 职责

**用例编排层**——接收表示层的请求，编排调度 L3 领域层的多个服务完成一个完整的业务用例，并组装返回结果。

#### 模块映射

**M6 · 应用服务** 整体位于应用层，它的 7 个子接口就是 7 组用例编排：

```
┌───────────────────────────────────────────────────────────────────┐
│                   L2 · 应用层 · M6                                │
├───────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌─────────────────┐   ┌─────────────────┐   ┌───────────────┐  │
│  │ IAttributionQuery│   │ IClassAnalysis   │   │ IVisualization│  │
│  │ 归因查询用例编排   │   │ 班级分析用例编排  │   │ 可视化用例编排 │  │
│  └────────┬────────┘   └────────┬────────┘   └──────┬────────┘  │
│           │                     │                    │           │
│  ┌────────▼────────┐   ┌───────▼─────────┐   ┌─────▼─────────┐ │
│  │ IReportExport   │   │ IPersonalKB      │   │ IStudentSelf  │ │
│  │ 报告导出用例编排  │   │ 个人知识库用例编排 │   │ 学生自助用例编排│ │
│  └─────────────────┘   └─────────────────┘   └───────────────┘ │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │ IOperationsDashboard                                       │ │
│  │ 运营看板用例编排（面向 P5 运营看板，编排调用 M8 运营分析）      │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
├───────────────────────────────────────────────────────────────────┤
│  应用层职责边界：                                                   │
│  ① 权限校验 — 教师只能操作自己班级，学生只能查看自己数据              │
│  ② 输入校验 — 参数格式、范围合法性                                   │
│  ③ 用例编排 — 按业务顺序调用 L3 的多个服务                           │
│  ④ 结果组装 — 将 L3 返回的领域对象转换为表示层需要的 DTO/VO          │
│  ⑤ 事务协调 — 跨模块操作的原子性保障                                 │
│                                                                   │
│  应用层不包含：                                                      │
│  ✗ 业务规则（属于 L3 领域层）                                        │
│  ✗ 数据持久化（属于 L4 基础设施层）                                   │
│  ✗ 算法实现（属于 L3 领域层）                                        │
└───────────────────────────────────────────────────────────────────┘
```

#### 典型用例编排示例

**用例：教师查询学生A的二次函数归因分析**

```
表示层(P2)           应用层(M6)                    领域层(M4/M5)
    │                   │                              │
    │── 查询请求 ────────▶│                              │
    │                   │                              │
    │                   │── ① 权限校验 ──▶ M1           │
    │                   │   (教师是否任教该学生班级)       │
    │                   │                              │
    │                   │── ② 意图路由 ────────────────▶│
    │                   │   (识别为"归因分析"任务类型)     │
    │                   │                              │
    │                   │── ③ 剪枝子图 ────────────────▶│ M4.pruningService
    │                   │◀── 返回子图 ──────────────────│   .prune(studentId,
    │                   │                              │    "归因分析")
    │                   │                              │
    │                   │── ④ 生成归因 ────────────────▶│ M5.analysisService
    │                   │◀── 归因报告 ──────────────────│   .generateAttribution()
    │                   │                              │
    │                   │── ⑤ 结果组装 ────────────────│
    │                   │   (报告 + 可视化数据            │
    │                   │    + 可下钻知识点列表)          │
    │                   │                              │
    │◀── 返回VO ────────│                              │
```

#### 应用层内的用例编排清单

| 用例编排 | 调用的 L3 服务 | 说明 |
|---------|---------------|------|
| 单学生归因查询 | M4.pruning → M5.analysis | 意图识别 → 剪枝 → LLM 分析 → 结果结构化 |
| 交互式追问下钻 | M4.pruning → M5.drillDown | 沿前置依赖链继续上溯 |
| 班级薄弱概览 | M1.getStudents → M4.weight.batch → 聚合计算 | 批量读取权重 → 计算薄弱指数 → 排名 |
| 知识图谱可视化 | M4.fusion.getNodeNeighborhood → 渲染数据组装 | 子图查询 → 节点着色/大小计算 |
| 趋势追踪 | M4.weight.getWeightHistory → 趋势模式识别 | 权重历史 → 自动分类(上升/下降/波动/平台) |
| 跨学科分析 | M4.fusion → M2.prerequisite → 关联检测 | 跨学科路径发现 → 方向判定 |
| 复习路径推荐 | M4.weight → M4.fusion.computePageRank → M2.dependency → M5.reviewPath | 多源数据综合优先级计算 |
| 报告导出 | M5.analysis → 格式转换 → 文件生成 | 报告内容 → PDF/HTML/MD 渲染 |
| 教师个人教辅上传 | M3.document.upload → M1.teacher.verify | 复用入库流水线 + 权限校验 |
| 作业/测验录入 | M1.verifyOwnership → M3.event.import → M4.weight.adjust | 权限校验 → 事件入库 → 触发权重更新 |
| 运营看板首页 | M8.getDashboardOverview → 聚合组装 | 编排 M8 多个统计接口 → 聚合为看板 VO |
| 文档产能统计 | M8.getDocumentThroughput → 图表数据组装 | 统计数据 → 堆叠柱状图数据 |
| 知识点覆盖分析 | M8.getKnowledgeCoverage → M2.getCategoryTree → 缺口标注 | 覆盖率计算 → 树形图 + 缺口列表 |
| 文档利用效率 | M8.getDocumentUtilization → 排行/标记 | 访问记录聚合 → 热门排行 + 僵尸文档标记 |
| 处理时效监控 | M8.getProcessingLatency → 趋势图组装 | 耗时数据 → 趋势折线图 + 瓶颈标注 |

---

### 4.4 L3 · 领域层 (Domain Layer)

#### 职责

**系统的核心业务逻辑**——包含所有领域规则、算法和领域模型。领域层是系统价值的核心所在，不依赖于任何上层（表示层、应用层）的实现。

#### 模块映射

领域层分为**领域核心**和**领域支撑**两个子层，区别在于：
- **领域核心**：包含系统最核心的算法和推理逻辑（图谱融合、剪枝、权重计算、AI 分析）
- **领域支撑**：为核心提供基础数据（主数据、知识体系、入库数据），本身不包含复杂算法

```
┌───────────────────────────────────────────────────────────────────────┐
│                       L3 · 领域层 (Domain)                            │
│                                                                       │
│  ┌─ 领域核心 (Domain Core) ─────────────────────────────────────────┐ │
│  │                                                                   │ │
│  │  ┌────────────────────────────┐  ┌────────────────────────────┐  │ │
│  │  │  M4 · 图谱核心引擎          │  │  M5 · AI 分析引擎           │  │ │
│  │  │                            │  │                            │  │ │
│  │  │  ┌──────────────────────┐  │  │  ┌──────────────────────┐  │  │ │
│  │  │  │ M4-a 宽图谱融合       │  │  │  │ M5-a 上下文构建       │  │  │ │
│  │  │  │ · 多源图谱合并         │  │  │  │ · 子图序列化          │  │  │ │
│  │  │  │ · 图算法计算           │  │  │  │ · Token 预算控制      │  │  │ │
│  │  │  │ · 健康度度量           │  │  │  │ · 多源信息融合        │  │  │ │
│  │  │  └──────────────────────┘  │  │  └──────────────────────┘  │  │ │
│  │  │                            │  │                            │  │ │
│  │  │  ┌──────────────────────┐  │  │  ┌──────────────────────┐  │  │ │
│  │  │  │ M4-b 剪枝引擎        │  │  │  │ M5-b LLM 网关        │  │  │ │
│  │  │  │ · 策略驱动子图生成    │  │  │  │ · 模型路由与切换      │  │  │ │
│  │  │  │ · 跳数/广度/阈值控制  │  │  │  │ · 配额与降级          │  │  │ │
│  │  │  │ · A/B 对比测试       │  │  │  │ · Prompt 模板管理     │  │  │ │
│  │  │  └──────────────────────┘  │  │  └──────────────────────┘  │  │ │
│  │  │                            │  │                            │  │ │
│  │  │  ┌──────────────────────┐  │  │  ┌──────────────────────┐  │  │ │
│  │  │  │ M4-c 权重引擎        │◀─┼──┼──│ M5-c 分析生成器       │  │  │ │
│  │  │  │ · 时间衰减计算        │  │  │  │ · 归因分析器          │  │  │ │
│  │  │  │ · 行为驱动权重调整    │  │  │  │ · 教学建议器          │  │  │ │
│  │  │  │ · 规则模拟与预估      │  │  │  │ · 复习规划器          │  │  │ │
│  │  │  └──────────────────────┘  │  │  └──────────────────────┘  │  │ │
│  │  └────────────────────────────┘  └────────────────────────────┘  │ │
│  │                    ▲                              ▲               │ │
│  │                    │ 依赖                          │ 依赖           │ │
│  │  ┌─ 领域支撑 (Domain Support) ──────────────────────────────────┐ │ │
│  │  │                                                               │ │ │
│  │  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │ │ │
│  │  │  │ M1 · 主数据    │  │ M2 · 知识体系  │  │ M3 · 数据入库引擎 │   │ │ │
│  │  │  │              │  │              │  │                  │   │ │ │
│  │  │  │ · 学生管理    │  │ · 分类树管理   │  │ · PDF 解析流水线  │   │ │ │
│  │  │  │ · 教师管理    │  │ · 前置依赖     │  │ · CSV 导入流水线  │   │ │ │
│  │  │  │ · 班级管理    │  │ · 实体对齐     │  │ · 关联模型定义    │   │ │ │
│  │  │  │ · 角色权限    │  │ · 完整性检查   │  │ · 导入校验       │   │ │ │
│  │  │  └──────────────┘  └──────────────┘  └──────────────────┘   │ │ │
│  │  │                                                               │ │ │
│  │  │  ┌──────────────────────────────────────────────────────┐   │ │ │
│  │  │  │ M8 · 运营分析                                         │   │ │ │
│  │  │  │                                                      │   │ │ │
│  │  │  │ · 文档产能统计  · 知识点覆盖分析                       │   │ │ │
│  │  │  │ · 文档利用效率  · 数据处理时效                         │   │ │ │
│  │  │  └──────────────────────────────────────────────────────┘   │ │ │
│  │  └───────────────────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────┘
```

#### 领域核心 vs 领域支撑的依赖关系

```
M4 图谱核心引擎                    M5 AI 分析引擎
  │                                  │
  │── 读取主数据 ──────▶ M1           │── 读取剪枝子图 ──▶ M4
  │── 读取知识体系 ────▶ M2           │── 读取前置依赖 ──▶ M2
  │── 消费入库数据 ────▶ M3           │── 读取权重数据 ──▶ M4
  │                                  │── 匹配教辅资源 ──▶ M3
  │
  │  领域支撑之间的依赖：
  │  M3 ──▶ M1（学生学号匹配）
  │  M3 ──▶ M2（知识点名称匹配）
```

#### 领域层的核心领域模型

领域层中最重要的模型是**宽图谱模型**——它定义了图谱中节点和关系的结构：

```
┌────────────────────────────────────────────────────────────┐
│                   Wide Graph Domain Model                   │
│                                                            │
│   ┌─────────┐    MasteryRelation     ┌─────────────────┐  │
│   │ Student  │═══════════════════════▶│ KnowledgePoint   │  │
│   │          │    权重: 0.0 ~ 1.0     │                 │  │
│   │ 学号     │                        │ 名称             │  │
│   │ 姓名     │                        │ 学科             │  │
│   │ 班级     │    PrerequisiteRelation│ 来源             │  │
│   │ 状态     │         ▲              │ 状态             │  │
│   └────┬─────┘         │              └───────┬─────────┘  │
│        │               │                      │            │
│        │ EventRelation  │                      │            │
│        │               │                      │            │
│   ┌────▼─────┐         │              ┌───────▼─────────┐  │
│   │  Event   │         └──────────────│ Prerequisite    │  │
│   │ (多态)    │         知识点→知识点    │ Dependency      │  │
│   │          │                        └─────────────────┘  │
│   │ 考试事件  │    ExtractionRelation                       │
│   │ 作业事件  │    ┌──────────────┐                         │
│   │ 测验事件  │    │  Document    │                         │
│   └──────────┘    │              │                         │
│        ▲          │ 文件名       │   KnowledgeRelation     │
│        │          │ 处理状态     │   知识点↔知识点           │
│   ScoreRelation   │ 上传人       │   (引用/推导/包含)       │
│   事件→题目→知识点  └──────────────┘                         │
│                                                            │
│   ┌──────────────┐    ┌──────────┐                         │
│   │  ExamPaper   │───▶│ Question │───▶ KnowledgePoint(s)   │
│   │  试卷        │    │ 题目      │    (一道题关联多个知识点) │
│   └──────────────┘    └──────────┘                         │
└────────────────────────────────────────────────────────────┘
```

---

### 4.5 L4 · 基础设施层 (Infrastructure Layer)

#### 职责

为领域层提供**技术实现能力**——数据库访问、外部服务集成、文件存储等。基础设施层通过**适配器模式**隔离外部技术细节，使领域层不感知具体技术选型。

#### 适配器划分

```
┌───────────────────────────────────────────────────────────────────────┐
│                    L4 · 基础设施层 (Infrastructure)                    │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │                   持久化适配器 (Persistence Adapters)            │  │
│  │                                                                 │  │
│  │  ┌─────────────────┐    ┌─────────────────┐                    │  │
│  │  │ Neo4j 适配器     │    │ 关系型DB 适配器   │                    │  │
│  │  │                 │    │                 │                    │  │
│  │  │ · 宽图谱存储     │    │ · 用户/角色/权限  │                    │  │
│  │  │ · Cypher 查询    │    │ · 配置/规则/策略  │                    │  │
│  │  │ · 图算法执行     │    │ · 导入/导出记录   │                    │  │
│  │  │ · 事务管理       │    │ · 审计日志       │                    │  │
│  │  │ · 备份/恢复      │    │                 │                    │  │
│  │  └─────────────────┘    └─────────────────┘                    │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │                  外部服务适配器 (External Adapters)              │  │
│  │                                                                 │  │
│  │  ┌─────────────────┐    ┌─────────────────┐                    │  │
│  │  │ LLM API 适配器   │    │ 文件存储适配器    │                    │  │
│  │  │                 │    │                 │                    │  │
│  │  │ · Claude API    │    │ · PDF 文件存储    │                    │  │
│  │  │ · 模型路由       │    │ · CSV 文件暂存    │                    │  │
│  │  │ · 流式响应       │    │ · 导出文件生成    │                    │  │
│  │  │ · Token 计数     │    │ · 分享链接存储    │                    │  │
│  │  │ · 降级策略       │    │                 │                    │  │
│  │  └─────────────────┘    └─────────────────┘                    │  │
│  │                                                                 │  │
│  │  ┌─────────────────┐    ┌─────────────────┐                    │  │
│  │  │ PDF 解析适配器    │    │ 缓存适配器       │                    │  │
│  │  │                 │    │                 │                    │  │
│  │  │ · 版面分析服务    │    │ · Redis 缓存     │                    │  │
│  │  │ · NER/RE 模型    │    │ · 图谱快照缓存    │                    │  │
│  │  │ · OCR 服务       │    │ · Prompt 缓存    │                    │  │
│  │  └─────────────────┘    └─────────────────┘                    │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │                  通信适配器 (Communication Adapters)             │  │
│  │                                                                 │  │
│  │  ┌─────────────────┐    ┌─────────────────┐                    │  │
│  │  │ 消息队列适配器    │    │ 通知适配器       │                    │  │
│  │  │                 │    │                 │                    │  │
│  │  │ · 入库任务队列    │    │ · 邮件通知       │                    │  │
│  │  │ · 权重计算队列    │    │ · 站内消息       │                    │  │
│  │  │ · 异步处理编排    │    │ · Webhook       │                    │  │
│  │  └─────────────────┘    └─────────────────┘                    │  │
│  └─────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────┘
```

#### 存储职责划分原则

| 存储类型 | 适配器 | 存储内容 | 原因 |
|---------|--------|---------|------|
| 图数据库 | Neo4j | 宽图谱（所有节点和关系）、权重、事件节点 | 图遍历和模式匹配是核心操作 |
| 关系型数据库 | PostgreSQL/MySQL | 用户/角色/权限、配置/策略/规则、导入记录、审计日志、教师个人知识库元数据（上传记录/公开审核状态）、分享链接 | 结构化元数据，适合事务操作 |
| 文件存储 | MinIO/S3 | PDF 原始文件、导出文件、备份文件 | 二进制大对象 |
| 缓存 | Redis | 图谱快照、热点查询结果、Prompt 渲染缓存 | 降低 Neo4j 和 LLM 调用频率 |
| 消息队列 | RabbitMQ/Kafka | 入库任务、权重计算任务、异步通知 | 解耦耗时的批处理操作 |

#### 适配器与领域层的接口契约

```
领域层(L3)                        基础设施层(L4)
    │                                │
    │  定义 Repository 接口           │  实现 Repository 接口
    │  ┌──────────────────┐          │  ┌──────────────────────┐
    │  │ IGraphRepository  │◀─────────│  │ Neo4jGraphRepository  │
    │  │ · findNode(id)    │          │  │ · Cypher 查询实现      │
    │  │ · findSubgraph()  │          │  │ · 图算法实现           │
    │  │ · saveNode()      │          │  │ · 事务管理             │
    │  └──────────────────┘          │  └──────────────────────┘
    │                                │
    │  ┌──────────────────┐          │  ┌──────────────────────┐
    │  │ ILLMClient        │◀─────────│  │ ClaudeLLMClient       │
    │  │ · call(prompt)    │          │  │ · API 调用实现         │
    │  │ · stream(prompt)  │          │  │ · 流式响应处理         │
    │  │ · countTokens()   │          │  │ · Token 计数          │
    │  └──────────────────┘          │  └──────────────────────┘
    │                                │
    │  ┌──────────────────┐          │  ┌──────────────────────┐
    │  │ IFileStorage      │◀─────────│  │ S3FileStorage         │
    │  │ · upload(file)    │          │  │ · S3 API 调用          │
    │  │ · download(id)    │          │  │ · 预签名URL生成        │
    │  │ · delete(id)      │          │  │ · 分片上传             │
    │  └──────────────────┘          │  └──────────────────────┘
```

**核心原则**：领域层定义接口，基础设施层提供实现。更换技术选型（如 Neo4j → 其他图数据库）只需替换适配器实现，领域层零改动。

---

### 4.6 横切关注点 · M7 系统运维

#### 职责

M7 不属于任何一层，而是**跨越所有层的横切关注点**，为每一层提供可观测性和运维保障。

```
┌───────────────────────────────────────────────────────────┐
│                                                           │
│  L1 表示层    ── 页面访问日志、操作审计 ──────────────┐     │
│              ── 前端错误上报                            │     │
│                                                           │
│  L2 应用层    ── 用例执行日志（Trace ID 贯穿） ───────┤     │
│              ── 请求/响应日志                            │     │
│                                                           │
│  L3 领域层    ── 图谱操作日志 ───────────────────────┤     │
│              ── LLM 调用日志                             │     │
│              ── 权重变更日志                             │     │
│                                                           │
│  L4 基础设施  ── Neo4j 慢查询日志 ───────────────────┤     │
│              ── 外部 API 调用日志                        │     │
│              ── 定时任务执行日志                          │     │
│                                                           │
│              ┌────────────────────────────────────────┘     │
│              ▼                                              │
│  ┌──────────────────────────────────────────────────┐       │
│  │              M7 · 系统运维（横切）                 │       │
│  │                                                  │       │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │       │
│  │  │ 日志服务  │  │ 备份服务  │  │ 定时任务服务  │  │       │
│  │  │ · 统一搜索│  │ · 自动备份│  │ · Cron 调度   │  │       │
│  │  │ · 链路追踪│  │ · 手动备份│  │ · 依赖编排    │  │       │
│  │  │ · 慢查询  │  │ · 恢复验证│  │ · 失败诊断    │  │       │
│  │  └──────────┘  └──────────┘  └──────────────┘  │       │
│  │                                                  │       │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │       │
│  │  │ 监控告警  │  │ 审计日志  │  │ 通知服务      │  │       │
│  │  │ · 性能指标│  │ · 操作审计│  │ · 邮件/站内   │  │       │
│  │  │ · 阈值告警│  │ · 数据变更│  │ · Webhook    │  │       │
│  │  └──────────┘  └──────────┘  │ · 偏好管理    │  │       │
│  │                              └──────────────┘  │       │
│  └──────────────────────────────────────────────────┘       │
└───────────────────────────────────────────────────────────┘
```

#### 各层向 M7 暴露的日志维度

| 层级 | 日志内容 | Trace 关联 |
|------|---------|-----------|
| L1 | 用户操作事件（点击、提交、页面切换） | 前端生成的 Trace ID 随请求传递 |
| L2 | 用例名、入参摘要、出参摘要、耗时、异常 | Trace ID 贯穿整个请求链路 |
| L3 | 图谱操作（Cypher 语句、返回节点数）、LLM 调用（Token、延迟） | 同一 Trace ID 下串联 |
| L4 | 数据库连接、外部 API 响应码、文件 IO | 最底层，补充基础设施细节 |

---

### 4.7 分层依赖规则

```
┌──────────────────────────────────────────────────────┐
│                  依赖方向规则                          │
│                                                      │
│  ✅ 允许的依赖方向（自上而下）                         │
│     L1 → L2 → L3 → L4                               │
│                                                      │
│  ✅ 允许的跨层调用                                     │
│     L1 → L2（必须经过应用层，不跳过）                  │
│     L2 → L3（直接调用领域层）                          │
│     L3 → L4（通过接口，不感知实现）                    │
│                                                      │
│  ❌ 禁止的依赖方向                                     │
│     L4 → L3（基础设施不反向依赖领域逻辑）              │
│     L3 → L2（领域层不知道应用层的存在）                │
│     L2 → L1（应用层不知道表示层的实现）                │
│     L3 → L1（领域层不直接操作 UI）                     │
│                                                      │
│  ✅ L3 内部依赖规则                                    │
│     领域核心(M4/M5) → 领域支撑(M1/M2/M3)  允许        │
│     领域支撑 → 领域核心                     禁止        │
│     领域支撑之间(M3→M1, M3→M2)           允许(单向)   │
│                                                      │
│  🔀 M7 横切规则                                       │
│     M7 可以观测所有层（只读，不修改业务逻辑）           │
│     所有层通过 AOP/中间件向 M7 发送日志和指标           │
│     M7 不参与正常的业务调用链                           │
└──────────────────────────────────────────────────────┘
```

---

### 4.8 分层与 4+1 其他视图的衔接

| 分层 | 对开发视图的影响 | 对进程视图的影响 | 对物理视图的影响 |
|------|----------------|----------------|----------------|
| **L1 表示层** | 前端项目独立仓库/模块 | 浏览器端渲染，无并发 | CDN / Nginx 静态资源 |
| **L2 应用层** | 后端 API 模块，按子接口拆包 | 请求级并发，线程池管理 | 应用服务器集群 |
| **L3 领域层** | 核心业务包，按模块拆分子包 | 图谱计算需考虑并发安全 | 与应用服务器同机或独立部署 |
| **L4 基础设施层** | 适配器包，按技术选型拆子包 | Neo4j 连接池管理、LLM 异步调用 | Neo4j 集群、LLM API、文件存储 |
| **M7 横切** | 公共中间件包 | 日志异步写入、定时任务独立线程 | 日志存储、监控平台独立部署 |
