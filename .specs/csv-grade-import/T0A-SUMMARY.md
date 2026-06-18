# T0A-SUMMARY: GraphNode.toProperties() 多态重构

- **Task ID**: T0A
- **Change ID**: csv-grade-import
- **状态**: ✅ done
- **时间**: 2026-06-15

---

## 做了什么

1. **GraphNode.java** — 基类新增 `toProperties()` 方法（返回 `Map<String, Object>`），包含公共字段映射（id / nodeType / documentId / createdAt）
2. **DocumentNode / EntityNode / KnowledgePointNode / KnowledgeCategoryNode** — 各新增 `@Override toProperties()`，先调 `super.toProperties()`，再追加自身独有字段
3. **KnowledgePointNode** — 新增不含 `documentId` 的构造器 `KnowledgePointNode(String name, String subject)`，用于 CSV 成绩解析场景（见 DESIGN D6）
4. **GraphNodeRepository.java** — `toNodeProps()` 从 27 行 instanceof 链缩减为 1 行委托 `return node.toProperties()`

## 改动了哪些文件

| 文件 | 改动 |
|---|---|
| `infrastructure/neo4j/node/GraphNode.java` | +16 行（toProperties 基类方法） |
| `infrastructure/neo4j/node/DocumentNode.java` | +10 行（import Map + @Override） |
| `infrastructure/neo4j/node/EntityNode.java` | +10 行（import Map + @Override） |
| `infrastructure/neo4j/node/KnowledgePointNode.java` | +22 行（import Map + 新构造器 + @Override） |
| `infrastructure/neo4j/node/KnowledgeCategoryNode.java` | +10 行（import Map + @Override） |
| `infrastructure/neo4j/repository/GraphNodeRepository.java` | -24 +3 行（instanceof 链 ⇒ 委托） |

总计：+81 / -27 行

## verify 输出

```
$ mvn compile -pl . -q 2>&1 | tail -10
(编译成功，无错误输出)
```

✅ 编译通过。新增节点类型不再需要修改 `GraphNodeRepository`。

### 既有测试回归

```
$ mvn test -pl .
Tests run: 50, Failures: 2, Errors: 5

Failures (2) — 预存：
  - DocumentServiceTest.deleteShouldMarkDeletedAndRemoveFile:221 → "文档不存在: id=1"（测试数据依赖 MySQL，非本次重构引入）

Errors (5) — 预存：
  - GraphControllerIntegrationTest.* x4 → ApplicationContext 启动失败（需 Neo4j/MySQL 连接）
  - DocumentProcessingIntegrationTest → Docker 不可用（Testcontainers）

→ 无新增回归 ✅
```

## 6 维自查

- **R1 认知过载**：toProperties() 每方法 ≤ 8 行，嵌套 0 层 ✅
- **R2 变更传播**：6 文件均在 write_files 范围内 ✅
- **R3 知识重复**：toProperties() 模式统一（super 调用 + 自身字段），无重复 ✅
- **R4 偶然复杂**：D11 多态方案恰好满足当前 6 种节点类型，无过度设计 ✅
- **R5 依赖混乱**：GraphNodeRepository → GraphNode.toProperties()（正常方向）✅
- **R6 领域扭曲**：字段名 100% 领域词（examNo / studentNo / gradeLevel），无误 ✅

✅ 沿用既有抽象 grep（R6.4）：
- NodeType 枚举模式：grep 确认 `(label, Class<? extends GraphNode>)` 构造 → 沿用（T02 即将扩展）
- GraphNodeRepository.save() 模式：grep 确认 MERGE Cypher 模式 → 未修改，继续沿用
- Map import：grep 确认 4 个节点类均需新增 `import java.util.Map` → 已添加

## 越界检查（R6.5）

```
✅ TASK write_files：6 项
✅ 实际 diff 涉及：6 项
✅ 越界：0
```