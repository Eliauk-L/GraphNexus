# CHANGE: 细化项目包结构，建立统一分层子包模板

- **Change ID**: `refine-package-structure`
- **创建日期**: 2026-06-12
- **路径建议**: 中等（`DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: draft

---

## Why（为什么做）

当前包结构已按四层架构（L1 api / L2 application / L3 infrastructure / common）搭建完毕，`document-process-pdf-minimal` change 也已全链路跑通（10/10 任务完成）。但随着代码量增长，结构债务已显现：

1. **L2 `application/document/service/` 摊大饼**：7 个文件（接口 `DocumentService`/`DocumentParser`、实现 `DocumentServiceImpl`/`PdfBoxDocumentParser`、BO `DocumentBO`/`UpdateDocumentBO`、模型 `ParseResult`）全部塞在一个 `service/` 包下。未来每加一个模块（graph/analysis/query），这个模式会扩散成 N 个"大饼包"。
2. **L3 `infrastructure/mysql/` 层级不清**：`JpaAuditConfig.java`（配置类）和 `document/`（数据对象）同级混放，`document/` 内部 DO / Repository / 枚举也未分层。
3. **L3 `infrastructure/storage/` 配置与业务混放**：`MinioConfig`/`MinioProperties`（配置）和 `FileStorageService`（服务）同一包下。
4. **预留模块空有目录无骨架**：graph、analysis、query、basic、llmgateway 等模块只有 `package-info.java` 和空的 `controller/`/`service/`，缺乏统一的子包骨架，新同学不知道代码该放哪个子包。
5. **测试目录未对齐**：test 侧包路径直接复制 main 侧，但缺少统一的 `architecture/` 之外的模块包骨架。

在更多业务模块（图谱构建、LLM 集成等）启动前，**先固化包模板**，避免结构债务随代码量线性扩散。

## What（做什么）

为四层架构的每一层定义**标准子包模板**，然后将所有现有 `.java` 文件归位到正确子包，并为所有预留模块补全骨架 `package-info.java`：

1. **定义统一包模板**：产出 `docs/package-structure-spec.md`，区分两层规则：
   - **基础模板**（所有模块必须遵循）：如 L2 每个模块含 `service/`→接口、`service/impl/`→实现、`model/`→BO/Query
   - **模块扩展**（按需添加）：如 `parser/` 仅文档模块有，其他模块不加
2. **L2 拆分**：`application/document/` 下将"大饼包"拆为 `service/`、`service/impl/`、`model/` + 模块扩展 `parser/`（仅文档模块）
3. **L3 归位**：`JpaAuditConfig` 移入 `mysql/config/`；`storage/` 下配置类移入 `storage/config/`
4. **全模块骨架补全**：所有预留模块（graph/analysis/query/basic/llmgateway/neo4j/redis/mq/llm）按统一模板创建子包 + `package-info.java`
5. **文件迁移 + import 修正**：所有受影响 `.java` 文件的 `package` 声明和 `import` 语句全部更新
6. **测试对齐**：test 目录也按新模板重建，移动测试类到对应新路径
7. **编译 + 测试验证**：`mvn compile` + `mvn test` 零错误通过

## 影响面

- [ ] 影响 `REQUIREMENT.md` — 否，纯结构重构
- [x] 影响 `DESIGN.md` / 引入新 ADR — 是，需 DESIGN 定义统一包模板（子包命名/职责/废弃路径）
- [ ] 影响现有 AC — 否，不改变任何业务行为
- [ ] 影响数据模型 / 迁移 — 否
- [ ] 影响外部 API 兼容性 — 否，REST 路径和 JSON 结构不变
- [ ] 仅修复 bug — 否

## 范围排除（这次不做）

- ❌ **修改任何业务逻辑代码**：只动 `package` 声明、`import` 语句和文件路径，不增删改任何方法/字段/注释
- ❌ **修改 `pom.xml`**（禁动清单，依赖和构建配置不变）
- ❌ **修改 `docs/项目规范.md`**（禁动清单，规范变更需全员评审——本次产出独立的 `docs/package-structure-spec.md` 作为细化补充，后续可评审是否合并入主规范）
- ❌ **修改 `application.yml` 等配置文件**（包路径变更不影响 Spring 组件扫描）
- ❌ **移动 `common/` 下的类**（`ApiResponse`、`PageResult`、`BusinessException` 等已处于合理位置，无细化必要）
- ❌ **修改 `GraphNexusApplication.java`**（启动类位置不变，`@SpringBootApplication` 默认扫描 `com.graphnexus` 包及子包，路径变更不影响）
- ❌ **新增/删除任何类或方法**
- ❌ **修改数据库 DDL 或表结构**

## 验收线（粗粒度，不是 AC）

1. **包模板文档落地**：`docs/package-structure-spec.md` 包含四层全部模块的标准子包定义，新同学可据此判断"新代码放哪个包"
2. **编译零错误**：`mvn compile` 通过，所有 `package`/`import` 指向正确路径
3. **测试全绿**：`mvn test` 通过，现有 23+ 测试用例零失败（含 ArchUnit 分层架构测试）
4. **骨架完整**：所有预留模块均含标准子包 + `package-info.java`，`find src/main/java -type d | sort` 展示清晰的统一结构

## 风险与未知

- **Import 修正遗漏风险**：涉及 ~15 个 Java 文件的 package/import 修改，需逐文件验证。风险等级低——编译阶段可 100% 检测
- **ArchUnit 测试敏感度**：`LayeredArchitectureTest` 可能因包路径变化（如 `..service.impl..` 新增）需要微调规则。需在 DESIGN 阶段确认 ArchUnit 规则的包名匹配模式是否需要更新
- **IDE 缓存**：重构后 IDE（IntelliJ IDEA）可能需要手动 invalidate caches 才能正确识别新包结构
- **Git 历史断裂**：`git mv` 移动文件后，`git log --follow` 可能无法追踪个别文件的完整历史。接受此代价——结构健康优于历史连续性

---

> 后续包模板定义与任务拆解进入 `DESIGN.md` → `TASK.md`，本文件不再扩展。