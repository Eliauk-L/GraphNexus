# GraphNexus — 学情智图

> 基于 Neo4j 知识图谱 + 大语言模型的教育智能分析平台
>
> 将非结构化教辅文档和考试成绩转化为结构化知识网络，为教师提供从"知道学生错了"到"知道学生为什么错"的精准诊断能力。

[![Java](https://img.shields.io/badge/Java-17-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Neo4j](https://img.shields.io/badge/Neo4j-5.26-blue)](https://neo4j.com/)
[![Vue](https://img.shields.io/badge/Vue-3.x-4fc08d)](https://vuejs.org/)
[![Podman](https://img.shields.io/badge/Podman-deploy-892CA0)](https://podman.io/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

---

## 目录

- [1. 产品概述](#1-产品概述)
- [2. 系统架构](#2-系统架构)
- [3. 快速启动](#3-快速启动)
- [4. 剪枝策略](#4-剪枝策略)
- [5. API 概览](#5-api-概览)
- [6. 项目规模](#6-项目规模)
- [7. 技术栈](#7-技术栈)

---

## 1. 产品概述

### 1.1 核心链路

```
教辅 PDF ──→ 知识图谱抽取 ──→ 宽图谱融合 ──→ 智能诊断问答
                            │
考试成绩 CSV ──→ 事件图谱构建 ──┘
```

1. **文档 → 图谱**：LLM 从教辅 PDF 中自动抽取定义、公式、概念及其前置依赖关系
2. **成绩 → 图谱**：考试成绩自动转化为学生-知识点掌握度网络
3. **图谱 → 诊断**：基于任务驱动子图剪枝 + LLM 分析，生成包含根因、证据链的结构化诊断报告

### 1.2 目标用户

| 角色 | 核心能力 |
|------|---------|
| **教师** | 薄弱点归因查询、班级薄弱概览、知识图谱可视化、成绩趋势追踪、报告导出 |
| **管理员** | PDF 文档上传构建、学生/成绩导入、宽图谱浏览、融合管理、权限控制 |
| **学生** | 薄弱点自助查询（规划中） |

---

## 2. 系统架构

### 2.1 整体架构

```
┌──────────────────────────────────────────────────────────────────────┐
│                        前端 (Vue 3 + Vite)                            │
│  文件管理 │ 成绩管理 │ 智能问答 │ 图谱可视化 │ 融合管理 │ 指标仪表盘    │
│                              port :5173 (dev) / Nginx (prod)         │
└──────────────────────────────────┬───────────────────────────────────┘
                                   │ HTTP/SSE (Axios)
┌──────────────────────────────────┴───────────────────────────────────┐
│                    Nginx 网关 (:8080)                                  │
│              least_conn → app-1:8081, app-2:8082                     │
└──────────────────────────────────┬───────────────────────────────────┘
                                   │
┌──────────────────────────────────┴───────────────────────────────────┐
│                Spring Boot 应用集群 (双副本)                            │
│  ┌──────────┐ ┌───────────┐ ┌──────────┐ ┌────────┐ ┌───────────┐   │
│  │ api/     │ │application│ │infra-    │ │common/ │ │resources/ │   │
│  │ REST 层  │ │ / 业务层  │ │structure │ │ 通用层  │ │ prompts/  │   │
│  │ 8 模块   │ │ 8 模块    │ │ / 基础设施│ │         │ │ 14 模板   │   │
│  └──────────┘ └───────────┘ └──────────┘ └────────┘ └───────────┘   │
│                              port :8081, :8082                       │
└───────┬──────────────┬──────────────┬──────────────┬─────────────────┘
        │              │              │              │
┌───────┴──┐  ┌────────┴──┐  ┌───────┴──┐  ┌───────┴──────┐
│  Neo4j   │  │   MySQL   │  │  Redis   │  │    MinIO     │
│ :7687    │  │   :3306   │  │  :6379   │  │ :9000/:9001  │
│ 图数据库  │  │  关系存储  │  │  缓存     │  │  对象存储     │
└──────────┘  └───────────┘  └──────────┘  └──────────────┘
```

### 2.2 后端分层架构（DDD 四层）

```
┌──────────────────────────────────────────────────────────────┐
│ api/       REST 接口层                                       │
│ ├── analysis/ 融合执行、剪枝预览 API                          │
│ ├── auth/     认证（登录/JWT/用户管理）                        │
│ ├── config/   系统配置管理                                    │
│ ├── file/     文件上传、成绩导入                              │
│ ├── graph/    图谱构建、指标查询                              │
│ ├── llm/      LLM 调试                                       │
│ ├── ops/      审计日志、统计快照                              │
│ ├── query/    智能问答（chat/ask/history）                    │
│ └── system/   健康检查                                        │
├──────────────────────────────────────────────────────────────┤
│ application/   业务逻辑层（核心）                              │
│ ├── analysis/   剪枝策略 + 融合引擎                           │
│ │   ├── fusion/  实体对齐（精确/模糊）、权重计算（时间衰减）     │
│ │   └── strategy/ 图剪枝策略（学生诊断/班级概览）              │
│ ├── query/      意图识别 + Prompt 组装 + LLM 编排             │
│ ├── graph/      图谱构建 + 指标计算（GDS）                    │
│ ├── config/     配置加载与校验                                │
│ ├── file/       文档解析（MinerU）、成绩解析                   │
│ └── ops/        审计切面、快照调度                            │
├──────────────────────────────────────────────────────────────┤
│ infrastructure/   基础设施层                                  │
│ ├── neo4j/      节点/边定义、GDS 适配器、Cypher 查询库         │
│ ├── mysql/      8 组 JPA Entity + Repository                 │
│ ├── storage/    MinIO 对象存储                                │
│ └── llm/        LangChain4j LLM 客户端                       │
├──────────────────────────────────────────────────────────────┤
│ common/   通用层                                              │
│ ├── security/   JWT 认证 + Spring Security 配置               │
│ ├── exception/  全局异常处理 + 业务错误码                      │
│ └── config/     CORS、OpenAPI、Redis                          │
└──────────────────────────────────────────────────────────────┘
```

### 2.3 图数据模型

**8 种节点 × 13 种边 = 宽图谱**

| 节点类型 | Neo4j Label | 说明 |
|---------|-------------|------|
| `StudentNode` | `:Student` | 学生主数据（学号、姓名、班级、年级） |
| `KnowledgePointNode` | `:KnowledgePoint` | 知识点（名称、学科、来源、PageRank） |
| `KnowledgeCategoryNode` | `:KnowledgeCategory` | 知识点分类层级 |
| `SubjectNode` | `:Subject` | 学科 |
| `EntityNode` | `:Entity` | 文档抽取实体（概念、公式等） |
| `FileNode` | `:File` | 源文档/文件 |
| `ExamNode` | `:Exam` | 考试事件 |

**宽图谱拓扑核心路径：**

```
File ──EXTRACTS──▶ Entity ──ALIGNED_TO──▶ KnowledgePoint
                                              ▲
Student ──ATTENDED──▶ Exam ──TESTED───────────┘
```

| 边类型 | Neo4j Type | 方向 | 说明 |
|--------|-----------|------|------|
| `MASTERS` | `MASTERS` | Student → KnowledgePoint | 掌握度（weight + description） |
| `PREREQUISITE_OF` | `PREREQUISITE_OF` | KnowledgePoint → KnowledgePoint | 前置依赖关系 |
| `BELONGS_TO_SUBJECT` | `BELONGS_TO_SUBJECT` | KnowledgePoint → Subject | 学科归属 |
| `EXTRACTS` | `EXTRACTS` | File → Entity | 文档抽取 |
| `ALIGNED_TO` | `ALIGNED_TO` | Entity → KnowledgePoint | 实体-知识点对齐 |
| `ATTENDED` | `ATTENDED` | Student → Exam | 参加考试 |
| `TESTED` | `TESTED` | Exam → KnowledgePoint | 考试覆盖知识点 |
| `CHILD_OF` | `CHILD_OF` | KnowledgeCategory → KnowledgeCategory | 分类层级 |
| `BELONGS_TO` | `BELONGS_TO` | KnowledgePoint → KnowledgeCategory | 知识点分类 |
| `REFERENCES`/`DERIVES`/`CONTAINS` | `REFERENCES`/`DERIVES`/`CONTAINS` | Entity → Entity | 实体间关系 |

### 2.4 前端架构

```
frontend/src/
├── views/
│   ├── file/FileManagePage.vue        # 文件上传 + 列表 + 解析
│   ├── grade/GradeManagePage.vue      # 考试成绩查询
│   ├── query/IntelligentQAPage.vue    # 自然语言问答 + Markdown/SVG 报告
│   ├── graph/GraphVisualizePage.vue   # AntV G6 v5 图谱可视化
│   ├── fusion/FusionManagePage.vue    # 融合执行 + 状态 + 回滚
│   └── metrics/MetricsDashboardPage.vue  # PageRank / 度中心性
├── api/                               # Axios HTTP 层（6 模块）
├── common/components/                 # 共享 UI 组件库
├── router/                            # 8 条路由 (lazy loading)
└── stores/                            # Pinia 状态管理
```

**前端技术栈：** Vue 3 + TypeScript + Vite + Naive UI + Tailwind CSS v4 (OKLCH) + AntV G6 v5 + Pinia

---

## 3. 快速启动

### 3.1 环境要求

| 依赖 | 最低版本 | 说明 |
|------|---------|------|
| JDK | 17+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 18+ | 前端开发 |
| Podman | 4.x+ | 容器运行时 |
| podman-compose | 1.x+ | 容器编排 |

### 3.2 三步启动

**第一步：启动基础设施**

```bash
# 1. 配置 Podman Machine（macOS，推荐 4 GiB+）
podman machine init --cpus 6 --memory 4096 --disk-size 50
podman machine start

# 2. 准备环境变量
cp .env.example .env
# 编辑 .env，填入 LLM_API_KEY 和 MINERU_API_TOKEN
source .env

# 3. 启动 Neo4j + MySQL + Redis + MinIO
podman compose up -d

# 4. 等待所有服务 healthy（约 60-90 秒）
watch -n 2 'podman compose ps'
```

**第二步：构建并启动应用**

```bash
# 编译
mvn package -DskipTests -q

# 构建镜像
podman build --format docker -t graphnexus-app:0.1.0 -f deployment/Containerfile .

# 启动应用集群（双副本）
podman compose -f deployment/podman-compose.app.yml up -d

# 启动 Nginx 网关
podman compose -f deployment/podman-compose.nginx.yml up -d
```

**第三步：启动前端（开发模式）**

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

### 3.3 验证

```bash
# 网关健康检查
curl http://localhost:8080/health

# Swagger UI
open http://localhost:8080/swagger-ui.html

# 两副本各自健康
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

### 3.4 服务入口

| 服务 | URL | 认证 |
|------|-----|------|
| **应用网关** | `http://localhost:8080` | JWT |
| **Swagger UI** | `http://localhost:8080/swagger-ui.html` | — |
| **Neo4j Browser** | `http://localhost:8080/neo4j/` | `neo4j` / `graphnexus123` |
| **MinIO Console** | `http://localhost:8080/minio/` | `minioadmin` / `minioadmin123` |
| **前端 Dev** | `http://localhost:5173` | — |

### 3.5 容器资源配置

| 容器 | 镜像 | 内存 | 端口 |
|------|------|------|------|
| Neo4j | `neo4j:5.26-community` | 512 MiB | 7474, 7687 |
| MySQL | `mysql:8.0` | 256 MiB | 3306 |
| Redis | `redis:7-alpine` | 64 MiB | 6379 |
| MinIO | `minio/minio:latest` | 128 MiB | 9000, 9001 |
| App ×2 | `graphnexus-app:0.1.0` | 512 MiB ×2 | 8081, 8082 |
| Nginx | `nginx:stable-alpine` | 32 MiB | 8080 |
| **合计** | — | **~2.0 GiB** | — |

---

## 4. 剪枝策略

### 4.1 设计理念

教育知识图谱构建后是一个高连通的"宽图谱"——包含全校学生、全学科知识点、所有考试事件和文档实体。直接将其输入 LLM 会导致：

1. **Token 溢出**：全图可达数千节点，远超 LLM 上下文窗口
2. **噪音干扰**：无关信息（其他学生、其他学科）稀释关键信号
3. **成本浪费**：每次调用处理全图，费用随图规模线性增长

**图剪枝的核心思想**：根据当前任务意图，从宽图谱中裁剪出"最小够用子图"——仅保留完成任务所需的节点和边，作为 LLM 的结构化上下文。

### 4.2 剪枝架构

```
用户问题
    │
    ▼
┌──────────────┐    ┌─────────────────────┐
│ 意图识别      │───▶│ PruningStrategyRegistry │
│ (LLM + 正则)  │    │  intent → Strategy      │
└──────────────┘    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │ SubgraphPruningStrategy │
                    │   (接口，策略模式)       │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
    ┌─────────▼─────┐  ┌──────▼────────┐  ┌────▼─────┐
    │ Student       │  │ Class         │  │ 未来     │
    │ Diagnosis     │  │ Weakness      │  │ 策略...  │
    │ Strategy      │  │ Overview      │  │          │
    └───────┬───────┘  └──────┬────────┘  └──────────┘
            │                 │
            ▼                 ▼
    ┌─────────────────────────────────────┐
    │          PrunedSubgraph              │
    │  nodes[] + edges[] + PruningMeta     │
    └─────────────────────────────────────┘
            │
            ▼
    ┌──────────────┐    ┌──────────────┐
    │ 子图序列化    │───▶│ LLM 诊断分析  │
    │ (文本/Markdown)│   │ (DeepSeek)   │
    └──────────────┘    └──────────────┘
```

### 4.3 策略一：学生薄弱点诊断（`STUDENT_DIAGNOSIS`）

**适用场景**：教师查询"分析学生张三的数学薄弱点"

**剪枝逻辑（4 步 Cypher 流水线）：**

```
Step 1: MATCH (s:Student) → 确认学生存在
         ↓
Step 2: MATCH (s)-[m:MASTERS]→(kp) WHERE m.weight < weakThreshold
         → 筛选弱掌握知识点（掌握度 < 0.6）
         ↓ 若 MASTERS 不可用（融合未执行）
         → 降级为 TESTED 路径 + MySQL exam_record 原始得分率
         ↓
Step 3: MATCH (kp)-[:PREREQUISITE_OF*1..maxHops]→(pre)
         → 展开前置依赖链（最多 2 跳）
         ↓
Step 4: MATCH (s)-[m:MASTERS]→(pre) WHERE pre.id IN $preKpIds
         → 补全前置知识点的掌握度
```

**剪枝效果**：

| 指标 | 全图 | 剪枝后 | 压缩比 |
|------|------|--------|--------|
| 节点数 | ~500+ | 10-50 | **90%+** |
| 边数 | ~2000+ | 15-100 | **95%+** |
| Token | 不可用 | 500-3000 | **可行** |

**输出子图结构**：
- 1 个 Student 节点
- N 个弱掌握 KP 节点（含 weight + examHistory）
- M 个前置依赖 KP 节点
- MASTERS 边（带掌握度 weight）
- PREREQUISITE_OF 边（带依赖强度）

### 4.4 策略二：班级薄弱概览（`CLASS_WEAKNESS_OVERVIEW`）

**适用场景**：教师查询"分析初三(1)班数学薄弱知识点"

**剪枝逻辑（5 步流水线）：**

```
Step 1: MySQL → 查询班級学生列表（去重）
         ↓
Step 2: 批量 Neo4j MASTERS 查询（单次 Cypher，替代 N 次逐生查询）
         ↓ 若 MASTERS 不可用
         → 降级为 TESTED 路径 + 逐生原始得分率
         ↓
Step 3: Java 层 KP 聚合统计（avgWeight, weakCount, minWeight, maxWeight）
         → 按薄弱人数降序排列，取 Top 20
         ↓
Step 4: MATCH (kp)-[:PREREQUISITE_OF*1..2]→(pre)
         → 展开前置依赖链
         ↓
Step 5: 组装聚合子图（ClassInfo 虚拟节点 + KP 节点 + 聚合 MASTERS 边）
```

**剪枝效果**：

| 指标 | 全图 | 剪枝后 | 说明 |
|------|------|--------|------|
| 节点数 | ~500+ | ≤60 | ClassInfo + ≤20 薄弱 KP + 前置依赖 |
| 边数 | ~2000+ | ≤80 | 聚合 MASTERS + PREREQUISITE_OF |
| KP 覆盖 | 全部 | Top 20 | 按薄弱人数降序 |

### 4.5 策略注册与扩展

策略通过 **Spring Bean Name** 自动注册，无需修改路由代码：

```java
@Component("STUDENT_DIAGNOSIS")      // ← Bean name 即意图名
public class StudentDiagnosisStrategy implements SubgraphPruningStrategy { ... }

@Component("CLASS_WEAKNESS_OVERVIEW")
public class ClassWeaknessOverviewStrategy implements SubgraphPruningStrategy { ... }
```

新增意图只需：
1. 实现 `SubgraphPruningStrategy` 接口
2. 用 `@Component("意图名")` 注册
3. 在 `QueryIntent` 枚举中添加意图

### 4.6 融合权重计算：时间衰减策略

剪枝质量高度依赖 MASTERS 边的权重准确性。权重通过**时间衰减加权平均**计算：

```
公式:
  weight = Σ(scoreRate_i × factor^monthsAgo_i) / Σ(factor^monthsAgo_i)

其中:
  scoreRate_i  = 第 i 次考试该知识点的得分率 (rawScore / maxScore)
  factor       = 衰减因子（默认 0.9）
  monthsAgo_i  = 第 i 次考试距今月数 (days / 30)

效果:
  - 最近考试权重最高（如 1 月前：0.9¹ = 0.9）
  - 半年前考试明显衰减（如 6 月前：0.9⁶ ≈ 0.53）
  - 一年前考试影响很小（如 12 月前：0.9¹² ≈ 0.28）
```

这一机制确保剪枝出的"薄弱知识点"反映的是**学生当前的薄弱状态**，而非历史平均。

### 4.7 Token 预算控制

剪枝后的子图在序列化时还会经过两层预算控制：

```
子图序列化文本
    │
    ▼
┌──────────────────┐
│ 重要性排序        │  ← 掌握度越低 + 度中心性越高 → 越优先保留
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ Token 截断        │  ← 超过 maxInputTokens × charsPerToken 时
│ (在完整条目边界)   │     截断 + 记录被省略知识点名称
└──────────────────┘
```

**默认参数：**
- `weakThreshold = 0.6`（掌握度 < 60% 视为薄弱）
- `maxPrerequisiteHops = 2`（前置依赖最多追溯 2 跳）
- `maxInputTokens = 4000`（Prompt 输入上限）
- `charsPerToken = 2`（中文字符/Token 估算比）

---

## 5. 核心处理流程

### 5.1 智能问答全链路

```
用户问题: "分析学生张三的数学薄弱点"
    │
    ▼
┌────────────────┐
│ 1. 意图识别     │  LLM 分类 → STUDENT_DIAGNOSIS
│   (LLM + 正则)  │  失败时降级为正则提取
└───────┬────────┘
        ▼
┌────────────────┐
│ 2. 实体提取     │  LLM JSON 提取 → {studentName, subject}
│   (LLM + 正则)  │  失败时降级为正则提取
└───────┬────────┘
        ▼
┌────────────────┐
│ 3. 实体解析     │  MySQL exam_record → studentNo
│                │  Neo4j StudentNode → 图 ID
└───────┬────────┘
        ▼
┌────────────────┐
│ 4. 图剪枝       │  StrategyRegistry.get("STUDENT_DIAGNOSIS")
│                │  → StudentDiagnosisStrategy.prune()
│                │  → PrunedSubgraph (nodes + edges)
└───────┬────────┘
        ▼
┌────────────────┐
│ 5. 子图序列化   │  学生信息 + 知识点掌握度表 + 前置依赖链
│                │  Markdown 格式（支持重要性排序）
└───────┬────────┘
        ▼
┌────────────────┐
│ 6. Token 预算   │  超限截断 + 记录省略节点名
└───────┬────────┘
        ▼
┌────────────────┐
│ 7. Prompt 组装  │  Prompt 模板（14 套） + 格式感知
│                │  system + user message pair
└───────┬────────┘
        ▼
┌────────────────┐
│ 8. LLM 调用     │  DeepSeek + 重试（最多 3 次）
│                │  格式校验（Markdown/HTML+SVG）
└───────┬────────┘
        ▼
┌────────────────┐
│ 9. 持久化       │  MySQL query_task (JSON 落盘)
│                │  支持历史查询、导出、删除
└────────────────┘
```

### 5.2 宽图谱融合流程

```
┌─────────────────┐     ┌─────────────────┐
│ 文档图谱 (Neo4j)  │     │ 成绩图谱 (Neo4j)  │
│ File → Entity    │     │ Student → Exam   │
│ Entity → KP      │     │ Exam → KP        │
└────────┬────────┘     └────────┬────────┘
         │                       │
         └───────────┬───────────┘
                     ▼
         ┌─────────────────────┐
         │ 1. 实体对齐           │
         │  ExactMatch: 唯一ID   │
         │  FuzzyMatch: 名称相似  │
         └──────────┬──────────┘
                    ▼
         ┌─────────────────────┐
         │ 2. MASTERS 边创建    │
         │  Student → KP        │
         │  weight = 时间衰减加权 │
         └──────────┬──────────┘
                    ▼
         ┌─────────────────────┐
         │ 3. GDS 图指标计算    │
         │  PageRank (d=0.85)   │
         │  度中心性 (in/out)    │
         └─────────────────────┘
```

---

## 6. API 概览

| 模块 | 端点 | 说明 |
|------|------|------|
| **认证** | `POST /api/v1/auth/login` | JWT 登录 |
| | `POST /api/v1/auth/refresh` | Token 刷新 |
| **文件** | `POST /api/v1/document/upload` | PDF/CSV 文件上传 |
| | `POST /api/v1/document/{id}/process` | PDF 解析（MinerU + LLM 抽取） |
| | `POST /api/v1/grade-record/import` | 考试成绩 CSV 导入 |
| **图谱** | `POST /api/v1/graph/extract` | LLM 知识图谱抽取 |
| | `GET /api/v1/graph/construction/{id}/subgraph` | 文档子图查询 |
| | `GET /api/v1/graph/metrics/pagerank` | PageRank 中心性 |
| | `GET /api/v1/graph/metrics/degree` | 度中心性 |
| **融合** | `POST /api/v1/graph/fusion/execute` | 执行宽图谱融合 |
| | `GET /api/v1/graph/fusion/status` | 查询融合状态 |
| | `POST /api/v1/graph/fusion/rollback` | 融合回滚 |
| **问答** | `POST /api/v1/query/chat` | 智能对话（意图识别 + 实体提取 + 诊断） |
| | `POST /api/v1/query/ask` | 同步问答（需显式传参） |
| | `GET /api/v1/query/result/{taskId}` | 查询任务结果 |
| | `GET /api/v1/query/subgraph/{taskId}` | 查询子图数据 |
| | `GET /api/v1/query/history` | 历史记录查询 |
| **运营** | `GET /api/v1/ops/audit-logs` | 审计日志 |
| | `GET /api/v1/ops/stats/snapshots` | 统计快照 |

> 完整 API 文档见 Swagger UI：`http://localhost:8080/swagger-ui.html`

---

## 7. 项目规模

| 维度 | 数量 |
|------|------|
| Java 源文件 | 312 |
| 前端源文件 (Vue/TS) | 76 |
| Neo4j 节点类型 | 8 |
| Neo4j 边类型 | 13 |
| MySQL 表 | 14 |
| Prompt 模板 | 14（Markdown + HTML 双格式） |
| REST API 端点 | 35+ |
| 前端路由 | 8 |
| Docker/Podman 镜像 | 7 |
| 用户故事 (V1) | 17/28 已实现 |

---

## 8. 技术栈

| 层 | 技术 | 版本 |
|----|------|------|
| **语言** | Java | 17 |
| **框架** | Spring Boot | 3.3.5 |
| **图数据库** | Neo4j (Community) | 5.26 |
| **图算法** | Neo4j GDS | graph-data-science |
| **关系数据库** | MySQL | 8.0 |
| **缓存** | Redis + Caffeine | 7 / 3.x |
| **对象存储** | MinIO | latest |
| **LLM 集成** | LangChain4j (OpenAI) | 1.0.0-beta1 |
| **LLM 模型** | DeepSeek (阿里云百炼) | — |
| **PDF 解析** | MinerU API + PDFBox | 3.0.3 |
| **认证** | JWT (jjwt) | 0.12.6 |
| **文档** | SpringDoc OpenAPI | 2.6.0 |
| **可观测性** | Micrometer + Prometheus + Logstash | — |
| **前端框架** | Vue 3 + TypeScript + Vite | 3.x |
| **UI 组件库** | Naive UI | — |
| **CSS** | Tailwind CSS v4 + OKLCH | 4.x |
| **状态管理** | Pinia | — |
| **图可视化** | AntV G6 | v5 |
| **Markdown** | marked + highlight.js | — |
| **容器** | Podman + podman-compose | 4.x+ |
| **测试** | JUnit 5 + Testcontainers + ArchUnit | — |

---

## 9. 目录结构

```
GraphNexus/
├── src/main/java/com/graphnexus/
│   ├── api/                    # REST 接口层（8 模块）
│   ├── application/            # 业务逻辑层（8 模块）
│   │   ├── analysis/           # 剪枝策略 + 融合引擎
│   │   ├── query/              # 意图识别 + Prompt + LLM
│   │   ├── graph/              # 图谱构建 + 指标计算
│   │   ├── config/             # 配置管理
│   │   ├── file/               # 文档解析 + 成绩解析
│   │   ├── auth/               # 用户认证
│   │   ├── ops/                # 审计 + 快照
│   │   └── system/             # 健康检查
│   ├── infrastructure/         # 基础设施层
│   │   ├── neo4j/              # 节点/边/GDS 适配器
│   │   ├── mysql/              # JPA Entity + Repository
│   │   ├── llm/                # LangChain4j 客户端
│   │   └── storage/            # MinIO
│   ├── common/                 # 通用层（安全/异常/配置）
│   └── resources/
│       ├── application*.yml    # 配置（dev/prod）
│       └── prompts/            # 14 套 Prompt 模板
├── frontend/                   # Vue 3 前端
│   └── src/views/              # 6 功能页面
├── deployment/                 # 部署配置
│   ├── Containerfile           # 多阶段构建
│   ├── podman-compose.app.yml  # 应用集群
│   ├── podman-compose.nginx.yml # Nginx 网关
│   ├── init-sql/               # MySQL 初始化
│   ├── nginx/                  # Nginx 配置
│   └── deploy.md               # 部署指南
├── docs/                       # 产品 & 技术文档
│   ├── design-view/            # 架构设计（逻辑/过程/物理视图）
│   ├── agile-testing/          # 敏捷测试计划
│   ├── user-stories/           # 用户故事
│   └── 学情智图-产品分享稿.md    # 产品分享
├── podman-compose.yml          # 基础设施编排
├── pom.xml                     # Maven 配置
└── STATE.md                    # 项目状态
```