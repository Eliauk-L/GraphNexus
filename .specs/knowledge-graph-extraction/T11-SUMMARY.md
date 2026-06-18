# T11-SUMMARY: 集成测试 — AC-1/AC-2/AC-4/AC-7/AC-8

- **Task ID**: T11
- **Change ID**: `knowledge-graph-extraction`
- **日期**: 2026-06-13

---

## 做了什么

创建集成测试 `GraphControllerIntegrationTest.java`：

| 用例 | AC | 验证内容 |
|------|-----|----------|
| testExtractGraph_Success | AC-1+AC-4 | POST extract → 200 + entityCount>0 + kpCount>0 → 走真实 DeepSeek API |
| testGetSubgraph_Success | AC-2 | GET document → nodes/edges 非空 + 含 Document/Entity/KnowledgePoint label |
| testExtractGraph_ReExtract_Overwrites | AC-8 | 两次抽取 nodes 数差异 ≤ 20% |
| testExtractGraph_InvalidDocument_Rejected | AC-7 | 不存在文档 → 404 拒绝 |

测试策略：`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@ActiveProfiles("dev")`，TestRestTemplate 调真实端口。

## 前置条件（运行前需确认）

- `podman ps` — 全部容器运行中
- `echo $DEEPSEEK_API_KEY` — 已设置
- MySQL 中存在 status=COMPLETED 且 text_content 非空的文档

## verify

`mvn test-compile -q` — 编译通过

⚠️ 集成测试需要真实基础设施，当前仅验证编译。完整运行：`mvn test -Dtest="GraphControllerIntegrationTest" -Dspring.profiles.active=dev`

## 越界检查

TASK write_files: 1 项 | diff: 1 项 | 越界: 0 ✅

## 完成判定

5 个集成测试用例覆盖 AC-1/AC-2/AC-4/AC-7/AC-8；编译通过，pending 真实环境运行。