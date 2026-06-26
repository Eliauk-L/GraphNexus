# REQUIREMENT: 配置管理模块 — 运行时动态配置管理

- **Change ID**: `config-management`
- **关联**: `@.specs/config-management/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1（配置查看）**：作为超级管理员（ADMIN），我想在后台查看所有可动态管理的配置项，按类别分组展示，以便快速了解哪些参数可以调整以及当前值是什么
- **US-2（业务参数编辑）**：作为超级管理员，我想编辑业务参数（如融合阈值、衰减因子、剪枝参数等数值/文本型配置），以便无需改代码就能调优系统行为
- **US-3（提示词编辑）**：作为超级管理员，我想编辑 LLM 提示词模板的完整文本内容（system prompt / user prompt / few-shot），以便无需重新部署就能优化 AI 输出质量
- **US-4（模型参数编辑）**：作为超级管理员，我想编辑 LLM 模型的调用参数（模型名称、temperature、max-tokens），以便切换模型或调整生成风格
- **US-5（应用配置）**：作为超级管理员，我修改配置后想通过「应用配置」按钮手动触发重载，以便所有新配置值对后续业务请求即时生效，且不需要重启服务
- **US-6（启动加载）**：作为系统，我期望启动时自动从数据库加载已保存的配置值，覆盖 yml 中的默认值，以便服务重启后配置不丢失
- **US-7（安全编辑）**：作为系统，我想对配置修改进行基本校验（类型匹配、非空校验、数值范围），以便防止管理员误输入导致系统异常

---

## 验收准则（AC）

### AC-1 · 查看配置列表（按类别分组）

- **Given** 用户以 ADMIN 角色登录，数据库 `system_config` 表中存在已保存的配置值
- **When** 客户端 `GET /api/v1/config`（无参数）
- **Then** 返回 HTTP 200，响应体为按 `category` 分组的配置列表，每组含 `category` 名称 + 配置项数组，每项含 `configKey`、`configName`（中文显示名）、`configValue`、`configType`（STRING/NUMBER/BOOLEAN/TEXT）、`description`。至少包含三类：`BUSINESS_PARAM`、`LLM_PROMPT`、`LLM_MODEL`
- **验证方式**: `curl -X GET http://localhost:8080/api/v1/config -H 'Authorization: Bearer <admin_token>' | jq '.data | group_by(.category)'` 验证明细分组 ≥ 3

### AC-2 · 查看单个配置详情

- **Given** 配置项 `fusion.kp-matching.threshold` 存在于数据库中
- **When** 客户端 `GET /api/v1/config/fusion.kp-matching.threshold`
- **Then** 返回 HTTP 200，响应体含完整的配置项信息（`configKey`、`configValue`、`configType`、`category`、`description`、`defaultValue`）
- **验证方式**: curl 验证响应字段完整

### AC-3 · 编辑业务参数

- **Given** 配置项 `fusion.kp-matching.threshold` 当前值为 `0.85`，configType 为 `NUMBER`
- **When** 客户端 `PUT /api/v1/config/fusion.kp-matching.threshold` 携带 `{"configValue": "0.80"}`
- **Then** 返回 HTTP 200，数据库 `system_config` 表中该 key 的 `config_value` 更新为 `0.80`，`update_time` 刷新。但系统中正在运行的业务逻辑仍使用旧值 `0.85`（尚未 Apply）
- **验证方式**: ① curl PUT 验证响应 200；② `podman exec -i graphnexus-mysql mysql -u graphnexus -pgraphnexus123 graphnexus -e "SELECT config_value FROM system_config WHERE config_key='fusion.kp-matching.threshold'"` 验证已更新

### AC-4 · 编辑 LLM 提示词

- **Given** 配置项 `prompt.student-diagnosis-system` 当前值为某段 Markdown 文本，configType 为 `TEXT`
- **When** 客户端 `PUT /api/v1/config/prompt.student-diagnosis-system` 携带 `{"configValue": "新的 system prompt 内容..."}`
- **Then** 返回 HTTP 200，数据库 `config_value` 更新为新文本。前端编辑器支持多行文本输入
- **验证方式**: curl PUT 携带多行文本 → 验证 200 → 查 MySQL 确认完整文本已存储

### AC-5 · 编辑 LLM 模型参数

- **Given** 配置项 `llm.model` 当前值为 `deepseek-v4-flash`，configType 为 `STRING`
- **When** 客户端 `PUT /api/v1/config/llm.model` 携带 `{"configValue": "deepseek-v4-pro"}`
- **Then** 返回 HTTP 200，数据库值更新
- **验证方式**: curl PUT → 验证 200 → 查 MySQL 确认

### AC-6 · 应用配置（重载生效）

- **Given** 数据库中多项配置已被修改（如 threshold 从 0.85 → 0.80，model 从 `deepseek-v4-flash` → `deepseek-v4-pro`），但尚未 Apply
- **When** 客户端 `POST /api/v1/config/apply`
- **Then** 返回 HTTP 200，响应体含 `{"reloadedCount": N, "reloadedAt": "2026-06-23T..."}`。之后发起的 LLM 调用使用新模型名称，融合计算使用新阈值
- **验证方式**: ① curl POST apply → 验证 200；② 触发一次智能问答，观察日志中 LLM 调用使用的 model 参数是否变为新值；③ 触发一次融合，日志中阈值是否使用 0.80

### AC-7 · 启动时从数据库加载配置

- **Given** 数据库中 `system_config` 表有已保存的配置值（如 `llm.temperature = 0.5`），而 `application.yml` 中默认值为 `0.3`
- **When** 服务重新启动
- **Then** 启动完成后，系统使用的 `llm.temperature` 值为 `0.5`（DB 值优先于 yml 默认值）。日志中可见 "Loaded N configs from database" 信息
- **验证方式**: ① 重启服务；② 触发 LLM 调用，验证 temperature 为 0.5；③ 检查启动日志含配置加载记录

### AC-8 · yml 默认值回退

- **Given** 数据库中不存在 `query.pruning.weak-threshold` 的配置记录（该配置从未被管理员修改过）
- **When** 系统启动或查询该配置
- **Then** `GET /api/v1/config/query.pruning.weak-threshold` 返回的 `configValue` 为 yml 中的默认值 `0.6`，`isCustomized` 字段为 `false`
- **验证方式**: ① 确保 DB 中无该 key；② curl GET → 验证 configValue=0.6，isCustomized=false

### AC-9 · 配置校验 — 类型不匹配拒绝

- **Given** 配置项 `fusion.kp-matching.threshold` 的 configType 为 `NUMBER`
- **When** 客户端 `PUT /api/v1/config/fusion.kp-matching.threshold` 携带 `{"configValue": "not_a_number"}`
- **Then** 返回 HTTP 400，`errorCode=A0023`，`userTip="配置值类型不匹配，期望 NUMBER"`
- **验证方式**: curl PUT 验证 400 + errorCode

### AC-10 · 配置校验 — 必填非空拒绝

- **Given** 配置项 `llm.model` 标记为 `required=true`（不可为空）
- **When** 客户端 `PUT /api/v1/config/llm.model` 携带 `{"configValue": ""}`
- **Then** 返回 HTTP 400，`errorCode=A0024`，`userTip="配置值不能为空"`
- **验证方式**: curl PUT 验证 400 + errorCode

### AC-11 · 配置校验 — 数值范围拒绝

- **Given** 配置项 `fusion.kp-matching.threshold` 的合法范围为 `0.0 ~ 1.0`
- **When** 客户端 `PUT /api/v1/config/fusion.kp-matching.threshold` 携带 `{"configValue": "1.5"}`
- **Then** 返回 HTTP 400，`errorCode=A0025`，`userTip="配置值超出允许范围"`
- **验证方式**: curl PUT 验证 400 + errorCode

### AC-12 · 提示词应用后 LLM 调用使用新文本

- **Given** 管理员已将 `prompt.student-diagnosis-system` 修改为新提示词并 Apply
- **When** 客户端发起一次智能问答 `POST /api/v1/query/ask`
- **Then** LLM 调用使用的 system prompt 为新文本内容。日志中可见新 prompt 的特征片段（或通过 LLM 返回的响应风格变化间接验证）
- **验证方式**: ① 修改 prompt 加入特殊标记（如末尾加 `[VERSION:v2]`）；② Apply；③ 触发问答；④ 检查日志中 LLM 请求内容含 `[VERSION:v2]`

### AC-13 · 权限控制 — 非 ADMIN 拒绝

- **Given** 用户以 TEACHER 角色登录（非 ADMIN）
- **When** 客户端 `GET /api/v1/config` 或 `POST /api/v1/config/apply`
- **Then** 返回 HTTP 403，`errorCode=A0105`，`userTip="权限不足"`
- **验证方式**: 以 TEACHER token 调用 config 端点 → 验证 403

### AC-14 · 未 Apply 标记提示

- **Given** 管理员修改了 `fusion.kp-matching.threshold` 但尚未 Apply
- **When** 客户端 `GET /api/v1/config`
- **Then** 该配置项的 `applied` 字段为 `false`（表示 DB 值与内存中运行值不一致），前端 UI 应展示明显提示「有待应用的修改」
- **验证方式**: ① PUT 修改不 Apply；② GET 列表验证该条目 applied=false；③ Apply 后再次 GET 验证 applied=true

---

## 范围切分

### v1（本次必做）

- 三类配置的 CRUD：`BUSINESS_PARAM`、`LLM_PROMPT`、`LLM_MODEL`
- 配置值持久化到 MySQL `system_config` 表
- 手动「应用配置」按钮触发热重载
- 服务启动时从 DB 加载配置覆盖 yml 默认值
- 基本类型校验（NUMBER/STRING/BOOLEAN/TEXT + 非空 + 数值范围）
- 管理后台「系统配置」前端页面（ADMIN only）
- 未 Apply 状态标记与前端提示
- 现有 9 个 prompt 模板 + 约 20 个业务/模型参数纳入管理

### v2（下一轮考虑，不本次）

- 配置版本历史与 diff 对比（可回滚到历史版本）
- 变更审计日志（谁在什么时候把什么从 X 改成 Y）
- 配置导入/导出（JSON/YAML 格式）
- 自定义校验规则引擎（如正则匹配、依赖校验 A>B）
- 配置分组级别的 Apply（部分应用而非全量）
- 配置搜索/筛选功能

### out（永远不做）

- 基础设施连接参数管理（DB/Redis/Neo4j/MinIO 地址端口密码）— 这些必须在部署时确定，运行时修改会导致连接断开
- 敏感凭证（API Key、JWT Secret）纳入配置管理 — 必须通过环境变量/Secret 管理，不落 DB
- 多环境配置同步（dev → prod 推送）— 各环境独立管理，防止误操作
- 配置热生效（修改即生效无需 Apply）— 用户明确选择手动 Apply 模式（Q2=C）
- 非 ADMIN 角色的配置查看/修改权限 — 配置管理仅限超级管理员

---

## 非功能性需求

- **性能**: 配置读取全量内存缓存（Caffeine），`GET /api/v1/config` 响应时间 ≤ 50ms；Apply 操作重载耗时 ≤ 2s（含缓存刷新 + 配置重新绑定）
- **安全**: 端点类级别 `@PreAuthorize("hasRole('ADMIN')")`；配置值中的敏感字段不返回前端（如 api-key 不在配置管理范围内）
- **兼容性**: 现有 yml 默认值 100% 保留为 fallback；不修改任何现有 `@ConfigurationProperties` 类的字段名或类型；现有 prompt 文件不删除（作为 DB 为空时的 fallback）
- **可观测性**: Apply 操作记 INFO 日志（含操作人、重载配置数量、耗时）；启动加载记 INFO 日志（含加载数量和跳过数量）；配置校验失败记 WARN 日志
- **可访问性**: 无特殊要求

## 依赖与假设

- **依赖**: 现有 Spring Boot 配置体系（`Environment`、`@ConfigurationProperties`、`PropertySource`）；Caffeine 本地缓存（已有依赖）；现有前端 Naive UI 组件库和极简设计体系
- **假设**: 
  - 配置修改频率低（日均 ≤ 5 次），高并发读写不是设计目标
  - ADMIN 角色具备基本的技术判断力，不会故意输入非法值
  - 配置项总量 ≤ 100 条，无需分页
  - 提示词修改后由管理员自行保证文本质量，系统不做内容审查
  - 系统以单实例运行（或各实例独立 Apply，不做跨实例同步）

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。