# TASK: 配置管理模块

- **Change ID**: `config-management`
- **关联**: `@.specs/config-management/REQUIREMENT.md`、`@.specs/config-management/DESIGN.md`、`@.specs/config-management/UI-DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P] DDL+ErrorCode, T02[P] DO+Repository
Wave 2:            T03 ConfigValidator                    (depends on T02)
Wave 3:            T04 ConfigService impl                 (depends on T03)
Wave 4 (parallel): T05[P] ConfigLoader, T06[P] LLMGateway reinit,
                   T07[P] PromptTemplateService, T08[P] ExtractionPromptBuilder,
                   T09[P] ConfigController                (all depend on T04)
Wave 5 (parallel): T10[P] config.ts API, T11[P] configStore.ts
Wave 6:            T12 SettingsConfigPage.vue             (depends on T11)
Wave 7:            T13[P] Router+Sidebar, T14[P] UnitTests,
                   T15 IntegrationTest, T16 FullVerify     (depends on T04~T13)
```

---

## 任务清单

```xml
<!-- ═══════════════════════════════════════════════════════════════ -->
<!-- Wave 1: Foundation (parallel)                                    -->
<!-- ═══════════════════════════════════════════════════════════════ -->

<task id="T01" parallel="true" status="pending">
  <name>DDL system_config 表 + ErrorCode 枚举值 + init.sql 同步</name>
  <read_files>
    src/main/resources/db/init.sql
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
  </read_files>
  <write_files>
    src/main/resources/db/init.sql
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
  </write_files>
  <action>
    1. 在 init.sql 末尾追加 system_config 表 DDL（字段：id/config_key/config_value/config_type/category/
       config_name/description/default_value/required/validation_rule/sort_order/create_time/update_time，
       含 uk_config_key + idx_category_sort 索引，见 DESIGN §6）
    2. 追加 seed INSERT 语句：约 30 条配置项（BUSINESS_PARAM 18 条 + LLM_PROMPT 9 条 + LLM_MODEL 3 条），
       全部 config_value=NULL（表示使用 yml 默认值）。具体 key 列表见 DESIGN §6 Seed Data
    3. 在 ErrorCode 枚举中新增 A0023（配置值类型不匹配）、A0024（配置值不能为空）、A0025（配置值超出允许范围）
    4. 执行 DDL：podman exec -i graphnexus-mysql mysql ... graphnexus &lt; init.sql 中的新增部分
  </action>
  <verify>
    podman exec -i graphnexus-mysql mysql -u graphnexus -pgraphnexus123 graphnexus -e "DESC system_config; SELECT COUNT(*) FROM system_config;"
    grep "A0023\|A0024\|A0025" src/main/java/com/graphnexus/common/exception/ErrorCode.java
  </verify>
  <done>system_config 表创建成功且含 30+ 条 seed 数据；ErrorCode 枚举含 A0023/A0024/A0025</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>SystemConfigDO 实体 + SystemConfigRepository 接口</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mysql/auth/entity/UserAccountDO.java
    src/main/java/com/graphnexus/infrastructure/mysql/auth/repository/UserAccountRepository.java
    src/main/java/com/graphnexus/infrastructure/mysql/config/JpaAuditConfig.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/mysql/config/entity/SystemConfigDO.java
    src/main/java/com/graphnexus/infrastructure/mysql/config/repository/SystemConfigRepository.java
  </write_files>
  <action>
    1. 创建 SystemConfigDO（@Entity, @Table(name="system_config")），字段对齐 DDL：
       id(Long, @Id, @GeneratedValue), configKey(String, @Column unique), configValue(String),
       configType(String), category(String), configName(String), description(String),
       defaultValue(String), required(Boolean), validationRule(String, @Column columnDefinition="JSON"),
       sortOrder(Integer), createTime(LocalDateTime, @CreatedDate), updateTime(LocalDateTime, @LastModifiedDate)
       使用 Lombok @Data + @NoArgsConstructor + @AllArgsConstructor + @Builder。
       沿用既有 DO 命名规范（类名以 DO 结尾）。
    2. 创建 SystemConfigRepository（interface extends JpaRepository&lt;SystemConfigDO, Long&gt;）：
       - findByConfigKey(String configKey) → Optional&lt;SystemConfigDO&gt;
       - findAllByOrderBySortOrderAsc() → List&lt;SystemConfigDO&gt;
       遵循 CONTEXT.md「Repository 方法命名规范」：方法名不含 DO 后缀、完整反映查询条件。
  </action>
  <verify>./mvnw compile -pl . 2>&1 | grep -E "BUILD SUCCESS|BUILD FAILURE"</verify>
  <done>mvn compile 通过；DO 字段与 DDL 一致；Repository 方法签名符合规范</done>
  <depends_on></depends_on>
</task>

<!-- ═══════════════════════════════════════════════════════════════ -->
<!-- Wave 2                                                            -->
<!-- ═══════════════════════════════════════════════════════════════ -->

<task id="T03" status="pending">
  <name>ConfigValidator — 配置值校验器</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mysql/config/entity/SystemConfigDO.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    src/main/java/com/graphnexus/common/exception/BusinessException.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/config/validation/ConfigValidator.java
    src/test/java/com/graphnexus/application/config/validation/ConfigValidatorTest.java
  </write_files>
  <action>
    创建 ConfigValidator 工具类（package com.graphnexus.application.config.validation）：
    - validate(String configKey, String configType, String value, String validationRuleJson) 方法
    - NUMBER：try Double.parseDouble(value)，解析 validationRule JSON 中的 min/max 做范围检查
    - BOOLEAN：value 必须为 "true" 或 "false"
    - STRING：required=true 时空字符串拒绝
    - TEXT：required=true 时空字符串拒绝
    - 校验失败抛 BusinessException(对应 ErrorCode)
    - validationRule JSON 解析失败时 WARN 日志跳过范围校验（不阻断）
    - 使用 Jackson ObjectMapper 解析 JSON（既有依赖）
  </action>
  <verify>./mvnw test -Dtest=ConfigValidatorTest -pl . 2>&1 | grep -E "Tests run|BUILD SUCCESS|BUILD FAILURE"</verify>
  <done>单元测试通过：覆盖 NUMBER 合法/非法/范围外、BOOLEAN 合法/非法、STRING 空值拒绝、TEXT 空值拒绝、JSON 解析失败不阻断</done>
  <depends_on>T02</depends_on>
</task>

<!-- ═══════════════════════════════════════════════════════════════ -->
<!-- Wave 3                                                            -->
<!-- ═══════════════════════════════════════════════════════════════ -->

<task id="T04" status="pending">
  <name>ConfigService 接口 + ConfigServiceImpl 实现（CRUD + Caffeine 缓存 + Apply）</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mysql/config/entity/SystemConfigDO.java
    src/main/java/com/graphnexus/infrastructure/mysql/config/repository/SystemConfigRepository.java
    src/main/java/com/graphnexus/application/config/validation/ConfigValidator.java
    src/main/java/com/graphnexus/application/analysis/fusion/config/FusionProperties.java
    src/main/java/com/graphnexus/application/analysis/fusion/config/FuzzyMatchProperties.java
    src/main/java/com/graphnexus/application/analysis/fusion/config/TimeDecayProperties.java
    src/main/java/com/graphnexus/application/query/chat/config/QueryProperties.java
    src/main/java/com/graphnexus/infrastructure/neo4j/gds/config/MetricsProperties.java
    src/main/java/com/graphnexus/application/file/textbook/parser/pdf/mineru/config/MinerUProperties.java
    src/main/java/com/graphnexus/infrastructure/llm/client/Langchain4jLlmGateway.java
    src/main/java/com/graphnexus/common/exception/BusinessException.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    src/main/java/com/graphnexus/application/graph/metrics/service/impl/MetricsServiceImpl.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/config/service/ConfigService.java
    src/main/java/com/graphnexus/application/config/service/impl/ConfigServiceImpl.java
  </write_files>
  <action>
    创建 ConfigService 接口 + ConfigServiceImpl 实现类：

    1. ConfigService 接口方法：
       - List&lt;ConfigResponse&gt; listAll() — 按 category+sortOrder 返回所有配置（含 applied 标记）
       - SystemConfigDO get(String configKey) — 单个查询
       - SystemConfigDO update(String configKey, String configValue) — 校验+更新 DB+更新缓存
       - Map apply() — 全量 reload DB→更新 Properties Bean→刷新缓存→重建 ChatModel（如需）
       - String getPromptText(String configKey) — 提示词专用：返回 config_value 或 null

    2. ConfigServiceImpl：
       - Caffeine Cache&lt;String, SystemConfigDO&gt;：initialCapacity=64, 无 TTL（全量 reload/put 刷新）
       - listAll()：遍历全量缓存，对比 Properties Bean 当前值计算 applied 标记
       - update()：调用 ConfigValidator.validate() → SystemConfigRepository.save() → cache.put()
       - apply()（核心逻辑，见 DESIGN §1 D2）：
         a. List&lt;SystemConfigDO&gt; all = systemConfigRepository.findAll()
         b. 遍历 all，config_value!=null 时调用对应 Properties Bean setter
         c. 维护 Map&lt;String, Consumer&lt;String&gt;&gt; 映射表（configKey→setter 调用），例：
            "fusion.kp-matching.threshold" → v → fusionProperties.getKpMatching().setThreshold(Double.parseDouble(v))
            "query.pruning.weak-threshold" → v → queryProperties.getPruning().setWeakThreshold(Double.parseDouble(v))
            "llm.model" → v → llmModelChanged = true; newModel = v
            ...（覆盖全部 30 个 config key 的 BUSINESS_PARAM 和 LLM_MODEL）
         d. Caffeine cache 全量刷新（cache.putAll）
         e. 若 LLM 模型参数变更 → langchain4jLlmGateway.reinitialize(model, temperature, maxTokens)
            （api-key/base-url 从 @Value 读取，不动）
         f. 返回 reloadedCount + reloadedAt
       - getPromptText()：检查 Caffeine 缓存 → config_value != null 返回 DB 值，否则返回 null
       - 构造器注入所有 Properties Bean（FusionProperties/FuzzyMatchProperties/TimeDecayProperties/
         QueryProperties/MetricsProperties/MinerUProperties）+ SystemConfigRepository +
         ConfigValidator + Langchain4jLlmGateway
       - 遵循构造器注入范式（@RequiredArgsConstructor + private final）

    3. 线程安全：volatile 或 synchronized 保护缓存写操作；setter 调用在单线程 Apply 中顺序执行。
  </action>
  <verify>./mvnw compile -pl . 2>&1 | grep -E "BUILD SUCCESS|BUILD FAILURE"</verify>
  <done>编译通过；ConfigService 接口含 4 个方法；ConfigServiceImpl 注入全部 Properties Bean + Repository + Validator + LLMGateway；setter 映射表覆盖 30 个 key</done>
  <depends_on>T03</depends_on>
</task>

<!-- ═══════════════════════════════════════════════════════════════ -->
<!-- Wave 4: Integration (5 tasks, parallel — all depend on T04)     -->
<!-- ═══════════════════════════════════════════════════════════════ -->

<task id="T05" parallel="true" status="pending">
  <name>ConfigLoader — ApplicationRunner 启动时从 DB 加载配置</name>
  <read_files>
    src/main/java/com/graphnexus/application/config/service/ConfigService.java
    src/main/java/com/graphnexus/application/config/service/impl/ConfigServiceImpl.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/config/loader/ConfigLoader.java
  </write_files>
  <action>
    创建 ConfigLoader 实现 ApplicationRunner, @Order(0)：
    - run() 方法调用 configService.apply() 完成启动时 DB→Properties Bean 加载
    - 用 try-catch 包裹：DB 不可达时 WARN 日志 + 不阻断启动
    - 日志："Loaded N configs from database" 或 "No custom configs in database, using yml defaults"
      见 AC-7、AC-8
    极简实现——直接复用 ConfigService.apply() 的逻辑（确保启动加载和 Apply 走的同一套 setter 映射）
  </action>
  <verify>
    启动服务：./mvnw spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | grep "Loaded.*config"
    （预期输出 "Loaded 0 configs from database" 因为 seed 数据 config_value 全为 NULL）
  </verify>
  <done>服务启动日志含配置加载记录；DB 不可达时不阻断启动（WARN 日志 + yml 默认值运行）</done>
  <depends_on>T04</depends_on>
</task>

<task id="T06" parallel="true" status="pending">
  <name>Langchain4jLlmGateway — 新增 reinitialize() 方法支持 ChatModel 热重建</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/llm/client/Langchain4jLlmGateway.java
    src/main/java/com/graphnexus/common/LlmGateway.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/llm/client/Langchain4jLlmGateway.java
  </write_files>
  <action>
    在 Langchain4jLlmGateway 中新增：
    1. reinitialize(String newModel, Double newTemperature, Integer newMaxTokens) 方法：
       - 从既有 @Value 字段读取 baseUrl 和 apiKey（不变，不属于配置管理范围）
       - 创建新的 OpenAiChatModel 实例（复用 init() 中的 builder 逻辑）
       - 用 volatile 修饰 chatModel 字段
       - 原子替换：新实例创建成功 → 替换 chatModel → INFO 日志
       - 失败：保留旧 chatModel → ERROR 日志 → 抛 BusinessException(C0001)
    2. 重构 init() 方法：提取 buildChatModel(model, temperature, maxTokens) private 方法，
       供 init() 和 reinitialize() 共用，避免代码重复
    3. 新增 getCurrentModelName() 方法供 ConfigService.apply() 判断模型是否变更
    见 DESIGN §1 D4
  </action>
  <verify>./mvnw compile -pl . 2>&1 | grep -E "BUILD SUCCESS|BUILD FAILURE"</verify>
  <done>编译通过；reinitialize() 原子替换 chatModel；失败时保留旧实例不中断服务；gateway 的其他行为不变</done>
  <depends_on>T04</depends_on>
</task>

<task id="T07" parallel="true" status="pending">
  <name>PromptTemplateService — 提示词双源加载（DB 优先 → classpath fallback）</name>
  <read_files>
    src/main/java/com/graphnexus/application/query/prompt/service/PromptTemplateService.java
    src/main/java/com/graphnexus/application/config/service/ConfigService.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/query/prompt/service/PromptTemplateService.java
  </write_files>
  <action>
    修改 PromptTemplateService.loadTemplate(String name)：
    - 先构建 configKey = "prompt." + name（如 "prompt.student-diagnosis-system"）
    - 调用 configService.getPromptText(configKey)
    - 若返回非 null → 使用 DB 自定义文本（log.debug "Using custom prompt from DB: {name}"）
    - 若返回 null → 走原有 classpath ResourceLoader 加载（不受影响）
    - 不影响 assemble()/buildPrompt()/loadTemplateWithFormat()/buildIntentList() 等其他方法
    见 DESIGN §2.3 数据流 + ADR-043
    - 构造器注入新增 ConfigService（保持 @RequiredArgsConstructor）
  </action>
  <verify>./mvnw compile -pl . 2>&1 | grep -E "BUILD SUCCESS|BUILD FAILURE"</verify>
  <done>编译通过；loadTemplate 优先查 ConfigService DB 值，无自定义时 fallback classpath；其他方法行为不变</done>
  <depends_on>T04</depends_on>
</task>

<task id="T08" parallel="true" status="pending">
  <name>ExtractionPromptBuilder — 提示词双源加载（对齐 PromptTemplateService）</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionPromptBuilder.java
    src/main/java/com/graphnexus/application/config/service/ConfigService.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionPromptBuilder.java
  </write_files>
  <action>
    修改 ExtractionPromptBuilder 中加载 classpath 模板的方法：
    - 定位其内部 load 模板的逻辑（约在 loadFewshot/loadTemplate 等私有方法中，使用 ResourceLoader 从 classpath 读取）
    - 将加载逻辑改为：先 configService.getPromptText("prompt." + templateName) → 非 null 返回 DB 值 → null 时走原有 classpath 路径
    - 构造器注入新增 ConfigService
    - 具体修改范围：仅改模板文件加载点（few-shot default/math, system, user 共 4 个加载路径），不改 prompt 装配逻辑
    见 DESIGN §2.3 + ADR-043
  </action>
  <verify>./mvnw compile -pl . 2>&1 | grep -E "BUILD SUCCESS|BUILD FAILURE"</verify>
  <done>编译通过；ExtractionPromptBuilder 模板加载走双源（DB 优先 → classpath fallback）；抽取 prompt 装配行为不变</done>
  <depends_on>T04</depends_on>
</task>

<task id="T09" parallel="true" status="pending">
  <name>ConfigController + DTO/VO — 配置管理 REST API</name>
  <read_files>
    src/main/java/com/graphnexus/application/config/service/ConfigService.java
    src/main/java/com/graphnexus/api/auth/controller/AuthController.java
    src/main/java/com/graphnexus/common/ApiResult.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    src/main/java/com/graphnexus/infrastructure/mysql/config/entity/SystemConfigDO.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/config/controller/ConfigController.java
    src/main/java/com/graphnexus/api/config/dto/ConfigResponse.java
    src/main/java/com/graphnexus/api/config/dto/UpdateConfigRequest.java
    src/main/java/com/graphnexus/api/config/dto/ApplyResultResponse.java
  </write_files>
  <action>
    创建 L1 API 层：

    1. ConfigResponse（VO）：configKey, configValue, configType, category, configName,
       description, defaultValue, applied（boolean, DB值=运行值）, isCustomized（boolean, config_value!=null）

    2. UpdateConfigRequest（DTO）：configValue（String, @NotBlank）

    3. ApplyResultResponse（VO）：reloadedCount(int), reloadedAt(LocalDateTime)

    4. ConfigController（@RestController, @RequestMapping("/api/v1/config"),
       @PreAuthorize("hasRole('ADMIN')") 类级别）：
       - GET / → listAll() → ApiResult.success(configService.listAll())
       - GET /{configKey} → get(configKey) → 查 ConfigService，转换为 ConfigResponse
       - PUT /{configKey} → update(configKey, body) → 调 configService.update() → 返回更新后的 ConfigResponse
       - POST /apply → apply() → 调 configService.apply() → 返回 ApplyResultResponse
       - 遵循既有 Controller 风格（ApiResult 包装、@RequiredArgsConstructor、@Slf4j）
       - 参数校验失败由 GlobalExceptionHandler 统一处理
    对齐 AC-1/AC-2/AC-3/AC-4/AC-5/AC-6/AC-9/AC-10/AC-11/AC-13/AC-14
  </action>
  <verify>
    ./mvnw compile -pl . 2>&1 | grep -E "BUILD SUCCESS|BUILD FAILURE"
    grep "@PreAuthorize" src/main/java/com/graphnexus/api/config/controller/ConfigController.java
  </verify>
  <done>编译通过；4 个端点含 @PreAuthorize("hasRole('ADMIN')")；DTO/VO 字段覆盖 REQUIREDMENT AC 所需数据</done>
  <depends_on>T04</depends_on>
</task>

<!-- ═══════════════════════════════════════════════════════════════ -->
<!-- Wave 5: Frontend Foundation (parallel)                           -->
<!-- ═══════════════════════════════════════════════════════════════ -->

<task id="T10" parallel="true" status="pending">
  <name>config.ts — 前端配置管理 API 模块</name>
  <read_files>
    frontend/src/api/client.ts
    frontend/src/api/auth.ts
  </read_files>
  <write_files>
    frontend/src/api/config.ts
  </write_files>
  <action>
    创建 frontend/src/api/config.ts：
    - 使用既有 client.ts 导出的 axios 实例（含 JWT interceptor）
    - getConfigs(): Promise&lt;ApiResult&lt;ConfigVO[]&gt;&gt; → GET /api/v1/config
    - getConfig(key: string): Promise&lt;ApiResult&lt;ConfigVO&gt;&gt; → GET /api/v1/config/{key}
    - updateConfig(key: string, configValue: string): Promise&lt;ApiResult&lt;ConfigVO&gt;&gt; → PUT /api/v1/config/{key}
    - applyConfigs(): Promise&lt;ApiResult&lt;ApplyResultVO&gt;&gt; → POST /api/v1/config/apply
    - 导出 TypeScript 类型：ConfigVO, ApplyResultVO（对齐后端 DTO/VO 字段）
    - 对齐前端既有 API 模块风格（api/auth.ts 模式）
  </action>
  <verify>npx vue-tsc --noEmit 2>&1 | grep -E "error TS|Found" | head -5</verify>
  <done>vue-tsc 无新增错误；API 模块含 4 个方法 + TypeScript 类型定义</done>
  <depends_on>T09</depends_on>
</task>

<task id="T11" parallel="true" status="pending">
  <name>configStore.ts — 前端配置状态管理（Pinia）</name>
  <read_files>
    frontend/src/stores/authStore.ts
    frontend/src/api/config.ts
  </read_files>
  <write_files>
    frontend/src/stores/configStore.ts
  </write_files>
  <action>
    创建 frontend/src/stores/configStore.ts（Pinia store）：
    - state：configs(ConfigVO[]), pendingCount(number), loading(boolean), activeCategory(string)
    - actions：
      - loadConfigs() — 调 getConfigs()，按 category 分组，计算 pendingCount（applied=false 的数量）
      - updateConfig(key, value) — 调 updateConfig()，成功后局部更新 configs 中对应项
      - applyConfigs() — 调 applyConfigs()，成功后 reloadConfigs()
    - getters：
      - configsByCategory — 按 activeCategory 过滤
      - hasPendingChanges — pendingCount > 0
    - 复用既有 store 模式（authStore.ts 的 defineStore + 箭头 action 风格）
    - 对齐 UI-DESIGN §3 组件规约中的状态定义
  </action>
  <verify>npx vue-tsc --noEmit 2>&1 | grep -E "error TS|Found" | head -5</verify>
  <done>vue-tsc 无新增错误；store 含 3 个 action + 2 个 getter</done>
  <depends_on>T10</depends_on>
</task>

<!-- ═══════════════════════════════════════════════════════════════ -->
<!-- Wave 6: Frontend Page                                            -->
<!-- ═══════════════════════════════════════════════════════════════ -->

<task id="T12" status="pending">
  <name>SettingsConfigPage.vue — 系统配置页面组件</name>
  <read_files>
    frontend/src/stores/configStore.ts
    frontend/src/api/config.ts
    frontend/src/views/auth/UserManagePage.vue
    frontend/src/common/components/BaseCard.vue
    frontend/src/common/components/BaseButton.vue
    frontend/src/assets/tokens.css
  </read_files>
  <write_files>
    frontend/src/views/settings/SettingsConfigPage.vue
  </write_files>
  <action>
    创建 frontend/src/views/settings/SettingsConfigPage.vue：
    按 UI-DESIGN §3 组件规约实现：

    1. 页面结构：
       - &lt;h1 class="headline"&gt;系统配置&lt;/h1&gt;
       - NTabs type="line" v-model:value="activeCategory"
         tabs: [{label:'业务参数',value:'BUSINESS_PARAM'},{label:'提示词模板',value:'LLM_PROMPT'},{label:'模型配置',value:'LLM_MODEL'}]
       - BaseCard 包裹配置列表

    2. 配置列表：
       - 按 configStore.configsByCategory 渲染
       - 每行：configName（body 色 primary）+ configValue（mono 色 secondary）+ Pencil 编辑图标
       - applied=false 行：左侧 2px --color-warning 竖条 + NTag type="warning" size="tiny" "未应用"
       - 行底分隔线 border-bottom

    3. 编辑弹窗（NModal preset="card" style="width:520px"）：
       - 显示当前值、默认值、description 说明
       - 按 configType 渲染不同输入控件（见 UI-DESIGN §3.3 对照表）：
         NUMBER→NInputNumber, STRING→NInput, BOOLEAN→NSwitch, TEXT→NInput type="textarea" autosize minRows=15
       - 底部：取消 BaseButton danger + 保存 BaseButton primary
       - 保存调 configStore.updateConfig() + message.success

    4. 底部固定栏（position:sticky, bottom:0）：
       - 左：NTag warning（pendingCount > 0 时显示 "N 项待应用"）
       - 右：BaseButton primary "应用配置"（pendingCount===0 时 disabled opacity 0.5）
       - 点击调 configStore.applyConfigs() + loading + message 反馈

    5. 对齐既有页面模式（UserManagePage）：BaseCard/BaseButton/NModal 使用方式、间距引用 tokens.css var
    6. 空状态：NEmpty description="暂无配置数据"（configs 数组为空时）
  </action>
  <verify>
    npx vue-tsc --noEmit 2>&1 | grep -E "error TS|Found" | head -5
  </verify>
  <done>vue-tsc 无类型错误；页面包含 Tab 切换、配置列表、编辑弹窗（4 种控件）、底部 Apply 栏；对齐 UI-DESIGN 全部规约</done>
  <depends_on>T11</depends_on>
</task>

<!-- ═══════════════════════════════════════════════════════════════ -->
<!-- Wave 7: Router + Sidebar + Tests                                 -->
<!-- ═══════════════════════════════════════════════════════════════ -->

<task id="T13" parallel="true" status="pending">
  <name>Router 路由 + AppLayout 侧边栏 — 新增 /settings/config 入口</name>
  <read_files>
    frontend/src/router/index.ts
    frontend/src/common/components/AppLayout.vue
  </read_files>
  <write_files>
    frontend/src/router/index.ts
    frontend/src/common/components/AppLayout.vue
  </write_files>
  <action>
    1. router/index.ts：在 settings 路由组中新增：
       { path: '/settings/config', name: 'settings-config', component: () => import('@/views/settings/SettingsConfigPage.vue'),
         meta: { title: '系统配置', roles: ['ADMIN'] } }
    2. AppLayout.vue：
       - import { Settings } from '@lucide/vue'（已有 Settings 图标导入）
       - settingsItems 数组新增：
         { path: '/settings/config', label: '系统配置', icon: Settings, roles: ['ADMIN'] }
       - 无需其他修改（Settings 图标已在既有导入中，roles 过滤逻辑已存在）
    对齐 UI-DESIGN §1（侧边栏新增 Settings 入口）
  </action>
  <verify>npx vue-tsc --noEmit 2>&1 | grep -E "error TS|Found" | head -5</verify>
  <done>路由 /settings/config 可构建；侧边栏新增长"系统配置"入口（仅 ADMIN 可见）；vue-tsc 无类型错误</done>
  <depends_on>T12</depends_on>
</task>

<task id="T14" parallel="true" status="pending">
  <name>单元测试：ConfigValidatorTest + ConfigServiceTest</name>
  <read_files>
    src/main/java/com/graphnexus/application/config/validation/ConfigValidator.java
    src/main/java/com/graphnexus/application/config/service/ConfigService.java
    src/main/java/com/graphnexus/application/config/service/impl/ConfigServiceImpl.java
    src/main/java/com/graphnexus/infrastructure/mysql/config/entity/SystemConfigDO.java
    src/main/java/com/graphnexus/infrastructure/mysql/config/repository/SystemConfigRepository.java
    src/test/java/com/graphnexus/application/analysis/fusion/service/impl/FusionServiceImplTest.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/config/validation/ConfigValidatorTest.java
    src/test/java/com/graphnexus/application/config/service/impl/ConfigServiceImplTest.java
  </write_files>
  <action>
    1. ConfigValidatorTest（JUnit 5 + AssertJ）：
       - 覆盖：NUMBER 合法/非法字符/范围外/边界值、BOOLEAN true/false/"maybe"、
         STRING 非空/空白/正常值、TEXT 非空/正常长文本、
         validationRule JSON 解析失败不阻断、min=null/max=null 时仅做类型校验
       - 每个失败用例验证抛出 BusinessException 且 errorCode 正确
    2. ConfigServiceImplTest（JUnit 5 + Mockito）：
       - Mock SystemConfigRepository、ConfigValidator、Properties Beans
       - 覆盖：listAll 按 category 分组且 applied 计算正确、
         update 调 validator 后 save + cache put、
         apply 重载后 Properties Bean setter 被正确调用、
         getPromptText 缓存命中/未命中返回 null、
         apply 中 LLM 模型参数变更时 gateway.reinitialize 被调用
       - 对齐既有测试风格（MockitoExtension、@Mock/@InjectMocks）
  </action>
  <verify>
    ./mvnw test -Dtest="ConfigValidatorTest,ConfigServiceImplTest" -pl . 2>&1 | grep -E "Tests run|BUILD SUCCESS|BUILD FAILURE"
  </verify>
  <done>所有单测通过（≥ 25 test cases）；ConfigValidatorTest 覆盖 5 种校验场景；ConfigServiceImplTest 覆盖 5 个核心方法</done>
  <depends_on>T04</depends_on>
</task>

<task id="T15" status="pending">
  <name>集成测试：ConfigControllerIntegrationTest — 完整 API 链路验证</name>
  <read_files>
    src/main/java/com/graphnexus/api/config/controller/ConfigController.java
    src/test/java/com/graphnexus/api/graph/controller/ConstructionControllerIntegrationTest.java
    src/test/java/com/graphnexus/common/security/JwtTestHelper.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/api/config/controller/ConfigControllerIntegrationTest.java
  </write_files>
  <action>
    创建 ConfigControllerIntegrationTest（@SpringBootTest, @ActiveProfiles("dev"), @AutoConfigureMockMvc）：
    - 使用 JwtTestHelper 生成 ADMIN Token（复用既有的测试 Token 生成机制）
    - 覆盖 AC：
      - AC-1：GET /api/v1/config → 200 + 按 category 分组
      - AC-2：GET /api/v1/config/{key} → 200 + 字段完整
      - AC-3/4/5：PUT /api/v1/config/{key} → 200 + MySQL 验证值已更新
      - AC-6：POST /api/v1/config/apply → 200 + reloadedCount
      - AC-9：PUT NUMBER 非法值 → 400 + A0023
      - AC-10：PUT 空值（required=true）→ 400 + A0024
      - AC-11：PUT NUMBER 超范围 → 400 + A0025
      - AC-13：GET with TEACHER token → 403
      - AC-14：PUT 后 GET 验证 applied=false → Apply 后 applied=true
    - 直连 podman MySQL 真实环境（dev profile）
  </action>
  <verify>
    ./mvnw test -Dtest=ConfigControllerIntegrationTest -pl . -Dspring.profiles.active=dev 2>&1 | grep -E "Tests run|BUILD SUCCESS|BUILD FAILURE"
  </verify>
  <done>集成测试全通过（≥ 10 test cases）；覆盖 AC-1 到 AC-14 中可自动化验证的项；包含权限校验</done>
  <depends_on>T09</depends_on>
</task>

<task id="T16" status="pending">
  <name>全量验证：mvn test + 前端检查 + 手动 curl 冒烟</name>
  <read_files>
    src/test/java/com/graphnexus/
    frontend/src/
  </read_files>
  <write_files>
    <!-- 无新增文件，仅验证 -->
  </write_files>
  <action>
    最终全量验证：
    1. ./mvnw test -pl . 全量后端测试通过（含新增 + 回归）
    2. npx vue-tsc --noEmit 前端类型检查通过
    3. 手动冒烟验证：
       - 启动服务 ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
       - curl GET /api/v1/config（验证 seed 数据全返回）
       - curl PUT 修改一个值 → GET 验证 applied=false
       - curl POST /api/v1/config/apply → 验证 applied=true
       - 检查日志：启动日志含 "Loaded N configs" + Apply 日志含 "Config applied"
       - 检查现有功能未破坏：文件列表、融合、问答各调一次
    见所有 AC
  </action>
  <verify>
    ./mvnw test -pl . 2>&1 | tail -20
    npx vue-tsc --noEmit 2>&1 | tail -5
  </verify>
  <done>全量 mvn test 通过（含新增 + 回归无失败）；vue-tsc 0 错误；手动 curl 冒烟全部 AC 通过</done>
  <depends_on>T15</depends_on>
</task>
```

---

## 状态字段说明

- `status="pending"` — 未开始
- `status="in_progress"` — 进行中
- `status="done"` — 已完成（verify 通过）
- `status="blocked"` — 阻塞

---

## 阻塞日志

| 任务 | 阻塞原因 | 待人工决策项 | 时间 |
|---|---|---|---|
|  |  |  |  |

---

## Fix 任务（来自 REVIEW / INTEGRATION）

```xml
<!-- 占位 -->
```

---

## 任务-需求追溯矩阵

| 任务 | 覆盖 AC |
|------|---------|
| T01 | 基础（DB 就绪） |
| T02 | 基础（持久层就绪） |
| T03 | AC-9, AC-10, AC-11 |
| T04 | AC-3, AC-4, AC-5, AC-6, AC-12, AC-14 |
| T05 | AC-7, AC-8 |
| T06 | AC-5, AC-6 |
| T07 | AC-4, AC-12 |
| T08 | AC-4, AC-12 |
| T09 | AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-13 |
| T10 | 基础（前端 API 就绪） |
| T11 | AC-14（applied 状态追踪） |
| T12 | AC-1, AC-3, AC-4, AC-5, AC-14 |
| T13 | AC-13（前端入口仅 ADMIN 可见） |
| T14 | AC-9, AC-10, AC-11 |
| T15 | AC-1~AC-6, AC-9~AC-11, AC-13, AC-14 |
| T16 | 全量 AC 冒烟 |