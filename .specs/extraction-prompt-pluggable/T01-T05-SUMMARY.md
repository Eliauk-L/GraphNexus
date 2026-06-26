# SUMMARY: Wave 1（T01–T05）— 抽取类型可插拔基础

- **Change ID**: `extraction-prompt-pluggable`
- **任务**: T01 / T02 / T03 / T04 / T05（Wave 1，5 个并行独立任务）
- **作者**: AI（Dev 角色）
- **日期**: 2026-06-21

> Wave 1 为 5 个互不依赖的基础任务，合并一份汇总。每任务均 TDD + 单独 verify 通过。

## 做了什么 / 改了哪些文件

| 任务 | 文件 | 说明 |
|---|---|---|
| T01 | `infrastructure/neo4j/node/EntityType.java`（M）+ `EntityTypeTest.java`（A） | 枚举增 `example` 字段，5 值填充（D2） |
| T02 | `extract/registry/EntityRelationType.java`（A）+ `EntityRelationTypeTest.java`（A） | 新建 LLM 可抽取关系枚举（DERIVES/CONTAINS/REFERENCES，D3） |
| T03 | `extract/registry/ExtractionNodeHandler.java` + `ExtractionNodeHandlerRegistry.java`（A）+ `ExtractionNodeHandlerRegistryTest.java`（A） | 顶层节点 handler 接口 + 注册表（构造期注入 + `register()` 供测试，D4） |
| T04 | `construction/model/ExtractionRawResult.java`（M）+ `extract/ExtractionJsonParserTest.java`（M） | `@JsonAnySetter` 收集扩展段；parser 无需改（与 `FAIL_ON_UNKNOWN_PROPERTIES=false` 兼容） |
| T05 | `resources/prompts/extraction-{system,user,fewshot-math,fewshot-default}.md`（A） | prompt 外置载体，类型/few-shot 段为占位符（D1/D5） |

M=修改 A=新增

## verify 输出（真实）

```
mvn test -Dtest=EntityTypeTest                                    → Tests run: 2,  Failures: 0  ✅
mvn test -Dtest=EntityRelationTypeTest                            → Tests run: 2,  Failures: 0  ✅
mvn test -Dtest=ExtractionNodeHandlerRegistryTest                 → Tests run: 3,  Failures: 0  ✅
mvn test -Dtest=ExtractionJsonParserTest                          → Tests run: 25 (含新增 2), Failures: 0  ✅
T05 文件检查：4 md 存在 + system.md 4 占位符 + user.md 4 占位符齐全  ✅
```

TDD：T01 先写测试见 RED（`getExample()` 未定义编译错），再加字段转 GREEN。T02–T04 新类 RED=缺类编译错，GREEN 如上。BUILD SUCCESS。

## 6 维自查（内置快查 · 生产代码改动必跑）

- **R1 认知过载**：枚举/注册表均简单，无 >50 行函数、无 >3 层嵌套 ✅
- **R2 变更传播**：diff 仅 Wave 1 write_files 并集，无越界（见边界检查）✅
- **R3 知识重复**：EntityRelationType 风格对齐 EntityType，未粘贴重复逻辑；注册表沿用 FileParserRegistry 范式 ✅
- **R4 偶然复杂**：仅加必要字段/接口，无"以后可能用到"的扩展点 ✅
- **R5 依赖混乱**：extract/registry 依赖 model + infra.node（正向），无反向 ✅
- **R6 领域扭曲**：`example`/`sectionKey`/`extensionSections` 命名贴合领域 ✅

## 越界检查（R6.5）

```
TASK write_files 并集：EntityType.java / EntityTypeTest / registry/*(3 源+2 测) /
                      ExtractionRawResult / ExtractionJsonParserTest / prompts/*(4)
实际 diff 涉及：       同上（M: ExtractionRawResult, EntityType, ExtractionJsonParserTest；
                      ??: registry/, prompts/*, test registry/, EntityTypeTest）
越界：0 ✅  禁动文件：0 ✅（PromptTemplateService/repository/ConstructionService 未碰）
.specs/ 已被 gitignore，不会误提交
```

## 沿用既有抽象 grep（R6.4）

- 注册表范式：找到 `application/file/parse/FileParserRegistry.java`（构造期注入 `List<>` + 内部 Map 索引）→ T03/T06 沿用
- 枚举富元数据：`EntityType`/`EdgeType`/`NodeType` 既有风格 → T02 `EntityRelationType` 对齐
- `@JsonAnySetter`：全项目无既有用法 → T04 首次引入（DESIGN 0.5.3 已批准"引入新模式"）
- prompt md 加载：`PromptTemplateService`（ADR-011）→ T05 沿用约定（T10 才自建轻量 loader，不改其类）

## LESSONS 检查（R1.8）

`grep -niE "枚举|entitytype|prompt|registry|jackson|jsonanysetter" .specs/LESSONS.md` → 无命中。无 active 条目适用，无需声明差异。

## 破坏性变更（R4.6）

- T01 改 `EntityType` 构造器签名（加 `example` 参）。enum 构造器非 public；grep 确认无外部构造调用（仅 `EntityType::getValue` 在 validator 用，不受影响）→ 属 1.8.6「重构内部实现，导出符号不变」，未触发 1.8 反问。
- T04 加 `@JsonAnySetter`：未知 JSON key 从"忽略"变"捕获"。既有 ExtractionJsonParserTest 用例仅含已知 key，行为不变（25 测试全绿）。无外部 API 变更。
- 其余任务均为新增文件，无破坏性变更。

## 数据库迁移

无 schema 变更（纯 Java 枚举/接口/资源文件，不涉及 Neo4j 或 MySQL 表结构）。跳过 1.7。

## 是否触发新 fix-plan

否。Wave 1 全部 verify 通过，进入 Wave 2。

## 备注

- TASK.md 中 T04/T07 原写的测试路径 `construction/service/ExtractionJsonParserTest.java` / `ExtractionValidatorTest.java` 实际在 `construction/extract/`（最近重构移过），已就地修正 TASK.md。
