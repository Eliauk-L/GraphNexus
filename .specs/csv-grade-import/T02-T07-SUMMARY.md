# T02+T07-SUMMARY: NodeType/EdgeType 枚举 + StudentNode/ExamNode

- **Task IDs**: T02, T07
- **Change ID**: csv-grade-import
- **状态**: ✅ done
- **时间**: 2026-06-15
- **合并理由**: NodeType 枚举引用 StudentNode.class / ExamNode.class，编译时必须节点类已存在

---

## 做了什么

1. **NodeType** — 新增 `STUDENT("Student", StudentNode.class)` + `EXAM("Exam", ExamNode.class)`
2. **EdgeType** — 新增 `ATTENDED("ATTENDED")` + `TESTED("TESTED")`
3. **StudentNode** — 新节点类：@Node("Student") extends GraphNode，字段 studentNo/name/className/grade，含 toProperties() 覆盖
4. **ExamNode** — 新节点类：@Node("Exam") extends GraphNode，字段 examNo/name/examDate/subject，含 toProperties() 覆盖

## 改动了哪些文件

| 文件 | 操作 | 改动 |
|---|---|---|
| `infrastructure/neo4j/node/NodeType.java` | 修改 | +6 行 |
| `infrastructure/neo4j/edge/EdgeType.java` | 修改 | +6 行 |
| `infrastructure/neo4j/node/StudentNode.java` | 新建 | 55 行 |
| `infrastructure/neo4j/node/ExamNode.java` | 新建 | 59 行 |

## verify 输出

```
$ mvn compile -pl . -q 2>&1 | tail -5
(编译成功，无错误输出)
```

✅ 编译通过。新节点类型可被 GraphNodeRepository 引用（待 T09 添加级联删除方法）。

## 6 维自查

- **R1 认知过载**：每类 ≤ 60 行，toProperties() ≤ 8 行 ✅
- **R2 变更传播**：4 文件均在 write_files 范围内 ✅
- **R3 知识重复**：toProperties() 模式与 T0A 既有节点一致，统一多态 ✅
- **R4 偶然复杂**：纯结构体（@Data + extends GraphNode），无多余抽象 ✅
- **R5 依赖混乱**：StudentNode/ExamNode → GraphNode（正常继承）✅
- **R6 领域扭曲**：字段名均为领域词（studentNo / examNo / className / examDate）✅

✅ 沿用既有抽象 grep（R6.4）：
- NodeType 枚举模式：grep 确认 `(label, Class<? extends GraphNode>)` → 沿用
- EdgeType 枚举模式：grep 确认 `(relationshipType)` → 沿用
- 节点类模式：grep DocumentNode → 沿用 @Data @Node @NoArgsConstructor @EqualsAndHashCode 模式
- toProperties 模式：grep GraphNode.toProperties() → 沿用 super().toProperties() + 自身字段 模式

## 越界检查（R6.5）

```
✅ TASK (T02) write_files：2 项（NodeType.java, EdgeType.java）
✅ TASK (T07) write_files：2 项（StudentNode.java, ExamNode.java）
✅ 实际 diff 涉及：4 项
✅ 越界：0
```