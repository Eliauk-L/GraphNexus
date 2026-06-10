# GraphNexus 逻辑视图

> 版本：v2.0 | 日期：2026-06-10
>
> 描述系统的静态结构，核心亮点：**知识图谱的节点与边抽象层设计**。

---

## 子文档速览

| 文档 | 版本 | 说明 |
|:--|:--|:--|
| [logical-view-class-diagrams.md](logical-view-class-diagrams.md) | v2.0 | 领域类图完整设计：8 种图节点 + 13 种图边 + 44 个 MySQL 类 = **68 个类** / 12 组 Mermaid 图 |

---

## 核心设计：节点与边抽象层

GraphNexus 的数据分为两层存储，类图据此严格分离：

```
┌──────────────────────────────────────────────────────────────┐
│                     Neo4j 图存储                              │
│                                                              │
│   GraphNode (abstract)              GraphEdge (abstract)     │
│   ├── StudentNode                   ├── MasteryEdge          │
│   ├── KnowledgePointNode            ├── PrerequisiteEdge     │
│   ├── KnowledgeCategoryNode         ├── BelongsToEdge        │
│   ├── DocumentNode                  ├── ChildOfEdge          │
│   ├── EntityNode                    ├── ExtractsEdge         │
│   ├── ExamNode                      ├── ReferencesEdge       │
│   ├── QuestionNode                  ├── AlignedToEdge        │
│   └── EventNode                     ├── HasEventEdge         │
│                                     ├── RelatesToEdge        │
│       label: "Student"              ├── ContainsEdge         │
│       label: "KnowledgePoint"       ├── TestsEdge            │
│       label: "Entity"  ...          ├── ScoresOnEdge         │
│                                     └── BelongsToExamEdge    │
│                                         type: "MASTERS"      │
│                                         type: "PREREQUISITE" │
│                                         type: "RELATES_TO"   │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                     MySQL 关系存储                             │
│                                                              │
│   用户与权限 (7)  │  文档处理流水线 (6)                         │
│   图谱业务配置 (9) │  图谱分析结果 (4)                          │
│   智能查询记录 (11)│  运维运营数据 (7)                          │
└──────────────────────────────────────────────────────────────┘
```

## 图节点一览 (8 种)

| 节点类 | Neo4j Label | 核心属性 | 对应 Story |
|:--|:--|:--|:--|
| `StudentNode` | `:Student` | studentNo, name, className, grade, status | 1.2 学生主数据 |
| `KnowledgePointNode` | `:KnowledgePoint` | name, subject, source, pageRank, status | 1.1 实体抽取 / 1.7 知识体系 |
| `KnowledgeCategoryNode` | `:KnowledgeCategory` | name, subject, level | 1.7 知识点分类树 |
| `DocumentNode` | `:Document` | name, subject, minioPath, status, pageCount | 1.1 PDF 上传与管理 |
| `EntityNode` | `:Entity` | name, entityType, confidence, pageNumber | 1.1 实体抽取 |
| `ExamNode` | `:Exam` | name, subject, examDate, totalScore | 1.8 试卷结构定义 |
| `QuestionNode` | `:Question` | questionNumber, maxScore, questionType | 1.8 题目定义 |
| `EventNode` | `:Event` | eventType, rawScore, allocatedScore, eventTime | 1.2 成绩事件化 |

## 图边一览 (13 种)

| 边类 | Neo4j Type | 方向 | 核心属性 |
|:--|:--|:--|:--|
| `MasteryEdge` | `MASTERS` | Student → KnowledgePoint | weight, confidence, eventCount |
| `PrerequisiteEdge` | `PREREQUISITE_OF` | KnowledgePoint → KnowledgePoint | strength, description |
| `BelongsToEdge` | `BELONGS_TO` | KnowledgePoint → KnowledgeCategory | — |
| `ChildOfEdge` | `CHILD_OF` | KnowledgeCategory → KnowledgeCategory | sortOrder |
| `ExtractsEdge` | `EXTRACTS` | Document → Entity | confidence |
| `ReferencesEdge` | `REFERENCES/DERIVES/CONTAINS` | Entity → Entity | relationSubType, confidence |
| `AlignedToEdge` | `ALIGNED_TO` | Entity → KnowledgePoint | confidence |
| `HasEventEdge` | `HAS_EVENT` | Student → Event | — |
| `RelatesToEdge` | `RELATES_TO` | Event → KnowledgePoint | score, maxScore, weightDelta |
| `ContainsEdge` | `CONTAINS` | Exam → Question | sortOrder |
| `TestsEdge` | `TESTS` | Question → KnowledgePoint | weightRatio |
| `ScoresOnEdge` | `SCORES_ON` | Event → Question | score, maxScore |
| `BelongsToExamEdge` | `BELONGS_TO_EXAM` | Event → Exam | — |

## 宽图谱拓扑核心路径

```
Document ──EXTRACTS──▶ Entity ──ALIGNED_TO──▶ KnowledgePoint
                                                    ▲
Student ──HAS_EVENT──▶ Event ──RELATES_TO──────────┘
                │                  ▲
                └──SCORES_ON──▶ Question ──TESTS───┘
                                     ▲
                              Exam ──┘ CONTAINS
```

## MySQL 实体包级架构

```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ 用户与权限域   │  │ 文档处理域    │  │图谱业务配置域  │  │ 智能查询域    │  │ 运维运营域    │
│              │  │              │  │              │  │              │  │              │
│ User         │  │ ParseTask    │  │EntAlignment  │  │AttribQuery   │  │ServiceHealth │
│ Role         │  │ ParseResult  │  │PrunStrategy  │  │AttribReport  │  │ AlertRule    │
│ Permission   │  │ExtractEntity │  │ WeightRule   │  │TeachingSugg. │  │ BackupTask   │
│ AuditLog     │  │ CsvImport    │  │ScheduledTask │  │ ReviewPath   │  │ UsageMetric  │
│              │  │              │  │              │  │ LlmConfig    │  │              │
│              │  │              │  │              │  │PromptTempl.  │  │              │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
```

---

> 详细类图、属性、操作和完整关系矩阵请查阅 [logical-view-class-diagrams.md](logical-view-class-diagrams.md)。
