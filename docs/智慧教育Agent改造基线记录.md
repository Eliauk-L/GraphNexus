# 智慧教育 Agent 改造基线记录

> 记录日期：2026-08-13
>
> 基线提交：`8a2ea62`
>
> 改造分支：`codex/education-agent-refactor`

## 1. 环境

| 组件 | 版本 |
|---|---|
| Java | 17.0.18 |
| Maven | 3.9.12 |
| Node.js | 24.13.0 |

## 2. 工作区边界

开始改造前，工作区已经存在以下与本次改造无关的用户修改：

- `src/main/java/com/graphnexus/common/config/OpenApiConfig.java`
- `src/main/java/com/graphnexus/common/config/SecurityConfig.java`
- `src/main/java/com/graphnexus/infrastructure/neo4j/config/Neo4jIndexConfig.java`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`
- `src/main/resources/application.yml`
- `src/main/resources/db/init.sql`
- `flow-kit/`

本次各任务提交不得混入上述既有修改。必须修改同一文件时，使用精确暂存并在提交前检查 staged diff。

## 3. 后端基线

执行命令：

```shell
mvn test
```

结果：失败。已知基线失败分为两类：

1. 本地 MySQL/Neo4j 未启动导致的集成测试连接错误，包括查询、融合、事务可见性和 Repository 集成测试。
2. 与本次 Agent 改造无关的已有单测失败，包括部分文件删除、图指标、Prompt 一致性、架构规则和异常日志断言。

改造期间的后端任务门禁：

- 新增或修改模块的定向单元测试必须全部通过；
- 编译必须通过；
- 全量测试不得新增基线之外的失败；
- P6 阶段统一处理或隔离既有失败。

## 4. 前端基线

执行命令：

```shell
cd frontend
npm run build
```

结果：失败。基线包含以下 TypeScript 问题：

- `HtmlSvgViewer.vue` 的 DOMPurify Config 类型冲突；
- `opsStore.ts` 错误读取响应的 `data` 字段；
- `FileManagePage.vue` 下拉选项使用 `null` 值；
- `GraphLegend.vue` style 类型不兼容；
- `graphAdapter.ts` 的 `dotted` 类型及 `properties` 字段不匹配；
- `OpsChart.vue` 引用了不存在的图表主题导出。

这些问题登记为 P5 的独立修复任务，不混入后端 P1～P4 提交。

## 5. 验收夹具约定

`src/test/resources/fixtures/agent/knowledge-graph.cypher` 定义唯一关系语义：

```text
A -[:PREREQUISITE_OF]-> B
```

表示 A 是 B 的前置知识。验收问题“为什么顶点坐标总丢分”必须能够反向得到：

```text
二次函数定义 → 二次函数一般式 → 对称轴 → 顶点坐标
```

禁止返回仅属于后继知识的“二次函数综合应用”作为顶点坐标的前置根因。
