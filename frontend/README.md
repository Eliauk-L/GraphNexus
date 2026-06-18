# GraphNexus 前端启动指南

## 环境要求

- Node.js >= 18
- npm >= 9

## 安装

```bash
cd frontend
npm install
```

> 如遇 npm 缓存权限问题：`npm install --cache /tmp/npm-cache`

## 开发

```bash
cd frontend
npm run dev
```

默认端口 `5173`，访问 http://localhost:5173

API 请求通过 Vite proxy 自动转发到后端 Spring Boot `http://localhost:8080`。

## 构建

```bash
cd frontend
npm run build
```

产物输出到 `frontend/dist/`，为纯静态文件，可由 Nginx 直接 serve。

## 类型检查

```bash
cd frontend
npx vue-tsc --noEmit
```

## 目录结构

```
frontend/src/
├── main.ts                         # 入口
├── App.vue                         # 根组件
├── api/                            # HTTP 层
│   ├── types.ts                    # ApiResult / DTO 类型定义
│   ├── client.ts                   # Axios 实例 + 拦截器
│   ├── file.ts                     # /api/v1/document/*
│   ├── graph.ts                    # /api/v1/graph/*
│   ├── query.ts                    # /api/v1/query/*
│   └── analysis.ts                 # /api/v1/analysis/*
├── assets/
│   ├── tokens.css                  # 15 OKLCH 颜色 + spacing/rounded/motion
│   └── global.css                  # 9 级 typography + base reset
├── router/
│   └── index.ts                    # 8 条路由（lazy loading）
├── common/                         # 跨模块共享组件
│   └── components/
│       ├── AppLayout.vue           # 侧边栏 + 顶栏 + 内容区
│       ├── BaseButton.vue          # primary / danger
│       ├── BaseInput.vue           # 文本输入
│       ├── BaseSelect.vue          # 下拉选择
│       ├── BaseCard.vue            # 卡片容器
│       ├── StatusBadge.vue         # 状态标签
│       ├── DataTable.vue           # 通用表格（分页/空态/加载）
│       └── MarkdownViewer.vue      # Markdown 渲染
└── views/                          # 功能页面
    ├── file/                       # 文件管理
    │   ├── FileManagePage.vue
    │   ├── fileStore.ts
    │   └── components/FileUpload.vue
    ├── grade/                      # 成绩管理
    │   ├── GradeManagePage.vue
    │   └── gradeStore.ts
    ├── query/                      # 智能问答
    │   ├── IntelligentQAPage.vue
    │   ├── queryStore.ts
    │   └── components/ (ChatInput / MarkdownReport / TokenUsageBar)
    ├── graph/                      # 图谱可视化
    │   ├── GraphVisualizePage.vue
    │   ├── graphStore.ts
    │   ├── graphAdapter.ts         # API 响应 → G6 GraphData
    │   └── components/ (GraphCanvas / NodeDetailPopover)
    ├── fusion/                     # 融合管理
    │   ├── FusionManagePage.vue
    │   └── fusionStore.ts
    └── metrics/                    # 图指标
        ├── MetricsDashboardPage.vue
        └── metricsStore.ts
```

## 路由表

| 路径 | 页面 | 说明 |
|------|------|------|
| `/` | — | redirect → `/files` |
| `/files` | FileManagePage | 文件上传 + 列表 + 解析/删除 |
| `/grades` | GradeManagePage | 按考试编号查询成绩 |
| `/qa` | IntelligentQAPage | 自然语言问答 + Markdown 报告 |
| `/graph` | GraphVisualizePage | 文档子图可视化（G6 渲染） |
| `/graph/document/:id` | GraphVisualizePage | 直接加载指定文档子图 |
| `/fusion` | FusionManagePage | 融合执行 + 状态查询 + 回滚 |
| `/metrics` | MetricsDashboardPage | PageRank / 度中心性查询 |

## 技术栈

| 层 | 选型 |
|----|------|
| 框架 | Vue 3 + TypeScript + Vite |
| 组件库 | Naive UI |
| CSS | Tailwind CSS v4 + OKLCH 设计 token |
| 状态管理 | Pinia |
| 路由 | Vue Router 4 |
| HTTP | Axios |
| 图标 | @lucide/vue |
| 图可视化 | AntV G6 v5 |
| Markdown | marked + highlight.js |