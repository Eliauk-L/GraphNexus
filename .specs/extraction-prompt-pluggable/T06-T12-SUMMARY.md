# SUMMARY: Wave 2–5（T06–T12）— 关系工厂化 / prompt 外置 / 扩展端到端 / 回归

- **Change ID**: `extraction-prompt-pluggable`
- **任务**: T06–T12（Wave 2–5）
- **作者**: AI（Dev 角色）
- **日期**: 2026-06-21

## 做了什么 / 改了哪些文件

| 任务 | 文件 | 说明 |
|---|---|---|
| T06 | `extract/registry/ExtractionEdgeFactory` + `ExtractionEdgeFactoryRegistry` + `DerivesEdgeFactory`/`ContainsEdgeFactory`/`ReferencesEdgeFactory`（A）+ 测试 | 关系边工厂接口 + EnumMap 注册表 + 3 实现（D3，替代 switch） |
| T07 | `ExtractionValidator`（M）+ `ExtractionValidatorTest`（M） | 关系合法集从 `EntityRelationType.values()` 派生，消除硬编码 `Set.of`（AC-3） |
| T08 | `registry/TestNode`/`TestNodeRaw`/`TestNodeHandler`/`TestNodeHandlerTest`（A，test-only） | 顶层节点扩展演示载体（D4 / AC-4） |
| T09 | `ExtractionService`（M）+ `ExtractionServiceTest`（A）+ `ConstructionServiceTest`（回归） | `convertToDomain`：关系 switch→`edgeFactoryRegistry.create` + 扩展节点 handler 循环；`ExtractionResult` 增 `extensionNodes` + 向后兼容构造器（D3/D4，AC-3/AC-4） |
| T10 | `ExtractionPromptBuilder`（M，重写）+ `ExtractionPromptBuilderTest`（A） | md 加载 + 段落装配（实体/关系段枚举派生、扩展段 handler 派生、few-shot 按 subject 切换+默认回退）+ 启动缓存（D1/D2/D3/D4/D5，AC-1/AC-2/AC-5） |
| T11 | `ExtractionNodeExtensionIntegrationTest`（A） | AC-4 端到端：TestNode 走完 prompt段→反序列化→校验→转换链路 |
| T12 | `ExtractionRegressionTest`（A） | AC-1/AC-6：固定输入快照 + 3 类非法输入仍抛 A0010/C0001 |

M=修改 A=新增

## verify 输出（真实）

```
T06 ExtractionEdgeFactoryRegistryTest        → 5  pass ✅
T07 ExtractionValidatorTest                   → 10 pass（含新增枚举派生反射断言）✅
T08 TestNodeHandlerTest                        → 5  pass ✅
T09 ExtractionServiceTest                      → 4  pass ✅  + ConstructionServiceTest 4 回归 pass ✅
T10 ExtractionPromptBuilderTest                → 10 pass ✅
T11 ExtractionNodeExtensionIntegrationTest     → 2  pass ✅
T12 ExtractionRegressionTest                   → 4  pass ✅
全量 mvn test：182 单元测试全绿；16 *IntegrationTest 错误（见下）
```

## mvn test 全量结果说明（R6.3 实跑 / R5.2 不掩盖）

`mvn test`：Tests run: 198, Failures: 0, **Errors: 16**。16 个错误全部在 `*IntegrationTest`（Fusion/Construction/Query Controller + FileProcessing），根因 = Spring 上下文启动时 `examRecordRepository`（JPA）Bean 创建抛 `java.lang.AssertionError`。

**已确认非本次回归**：切到本次改动前 baseline（ac48241）跑同一 `ConstructionControllerIntegrationTest`，同样 5 个 `AssertionError` 失败。本次改动不碰 MySQL/JPA/ExamRecord，属既有环境/配置问题，不在本 change 范围（R7.1 不扩大）。所有抽取相关单元测试全绿。

## 6 维自查（Wave 2–5 生产代码改动）

- **R1 认知过载**：`convertToDomain` 拆出 `convertSectionToList`/`invokeHandler` 辅助方法，单函数均 < 50 行 ✅
- **R2 变更传播**：每波次 diff 仅本波 write_files，0 越界（Wave 3 还原了 IDE 自动格式化的 `init.sql` 越界）✅
- **R3 知识重复**：关系工厂/节点 handler 沿用 `FileParserRegistry` 范式，无粘贴 ✅
- **R4 偶然复杂**：`ExtractionResult` 加向后兼容构造器避免破坏既有调用方，无多余扩展点 ✅
- **R5 依赖混乱**：extract/registry → model + infra（正向）；ExtractionPromptBuilder 依赖 ResourceLoader + NodeHandlerRegistry（正向）✅
- **R6 领域扭曲**：`extensionNodes`/`edgeFactoryRegistry`/`nodeHandlerRegistry` 命名贴合领域 ✅

## 越界检查（R6.5）

- Wave 2：11 文件全在 T06/T07/T08 write_files，0 越界
- Wave 3：4 文件（ExtractionService/PromptBuilder + 2 测试），0 越界；`init.sql` 越界已 `git checkout` 还原
- Wave 4/5：各 1 测试文件，0 越界
- **遗留**：工作区有 1 个非本次任务的 `MetricsServiceImpl.java` 改动（stash 恢复时混入，非我所做），已原样保留未提交，待用户确认处置

## 破坏性变更（R4.6）

- T09 删除 `convertToDomain` 的关系 switch（约 5 行）→ 改为 `edgeFactoryRegistry.create`。引用点：仅 `convertToDomain` 内部，无外部调用。行为等价（ConstructionServiceTest 4 回归 pass）。未触发 1.8 反问（内部重构，导出符号不变）。
- T10 `ExtractionPromptBuilder` 构造器签名变更（注入 `ResourceLoader` + `ExtractionNodeHandlerRegistry`）+ `buildSystemPrompt` 增 subject 重载（保留无参重载向后兼容）。引用点：仅 `ExtractionService`，已同步改为 `buildSystemPrompt(subject)`。
- T09 `ExtractionResult` record 增 `extensionNodes` 字段 → 既有 4 参构造通过向后兼容构造器保留，`ConstructionServiceImpl` 等调用方未改未破坏。

## LESSONS 检查（R1.8）

`grep` LESSONS.md 关键词（registry/Jackson/prompt/switch/枚举）→ 无命中。无 active 条目适用。

## 数据库迁移

无 schema 变更（纯 Java + 资源文件）。跳过 1.7。

## 是否触发新 fix-plan

否。12 任务全 verify 通过，进入 5-test / REVIEW。

## 备注

- Wave 3 提交前发现 `init.sql` 被 IDE 自动格式化（缩进漂移），已 `git checkout` 还原，未污染提交。
- 全量 `mvn test` 的 16 个集成测试错误为既有问题（baseline 复现），建议另开 change 排查 Hibernate JPA repository AssertionError。
- 工作区遗留 `MetricsServiceImpl.java` 改动非本次任务，未提交，待用户处置。
