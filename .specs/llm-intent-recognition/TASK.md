# TASK: LLM 意图识别 + HTML/SVG 输出

- **Change ID**: `llm-intent-recognition`
- **关联**: `@.specs/llm-intent-recognition/REQUIREMENT.md`、`@.specs/llm-intent-recognition/DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P], T03[P], T04[P], T05[P], T06[P], T07[P]
Wave 2 (parallel): T08,        T09[P], T10[P]      (depends on Wave 1)
Wave 3 (parallel): T11,        T12[P]               (depends on Wave 2)
Wave 4 (parallel): T13[P],     T14[P]               (depends on Wave 3)
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="pending">
  <name>QueryProperties 新增 outputFormat 配置项</name>
  <read_files>
    application/query/chat/config/QueryProperties.java
    application/query/chat/service/impl/QueryServiceImpl.java
  </read_files>
  <write_files>
    application/query/chat/config/QueryProperties.java
  </write_files>
  <action>
    见 DESIGN D2、ADR-026 D1。
    在 QueryProperties 中新增内嵌静态类 OutputFormat，含 format 字段（String，默认 "html-svg"）。
    添加 @PostConstruct 校验：format 值仅允许 "html-svg" 或 "markdown"，非法值时抛 IllegalArgumentException 拒绝启动。
    同步更新 application-dev.yml：query.output-format 配置项及注释。
  </action>
  <verify>grep -n "output-format\|outputFormat\|OutputFormat" src/main/java/com/graphnexus/application/query/chat/config/QueryProperties.java | head -10</verify>
  <done>QueryProperties 编译通过；@PostConstruct 校验覆盖合法/非法值；yml 配置项新增完成</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>DTOs + BO 新增 outputFormat 字段</name>
  <read_files>
    api/query/dto/chat/QueryAskResponse.java
    api/query/dto/chat/QueryResultResponse.java
    application/query/chat/model/QueryResultBO.java
  </read_files>
  <write_files>
    api/query/dto/chat/QueryAskResponse.java
    api/query/dto/chat/QueryResultResponse.java
    application/query/chat/model/QueryResultBO.java
  </write_files>
  <action>
    见 DESIGN D7、AC-11。
    QueryResultBO 新增 outputFormat(String) 字段。
    QueryAskResponse 新增 outputFormat(String) 字段，from(QueryResultBO) 映射。
    QueryResultResponse 新增 outputFormat(String) 字段，from(QueryResultBO) 映射。
    字段为新增可选字段，旧客户端忽略不报错。
  </action>
  <verify>grep -n "outputFormat" src/main/java/com/graphnexus/api/query/dto/chat/QueryAskResponse.java src/main/java/com/graphnexus/api/query/dto/chat/QueryResultResponse.java src/main/java/com/graphnexus/application/query/chat/model/QueryResultBO.java</verify>
  <done>3 个文件均含 outputFormat 字段；编译通过</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>IntentRecognitionStrategy 接口 + IntentRecognitionService 编排器</name>
  <read_files>
    application/query/chat/model/QueryIntent.java
    application/query/chat/service/QueryService.java
  </read_files>
  <write_files>
    application/query/chat/intent/IntentRecognitionStrategy.java
    application/query/chat/intent/IntentRecognitionService.java
  </write_files>
  <action>
    见 ADR-025 D0、DESIGN D1。
    创建 IntentRecognitionStrategy 接口：
    - QueryIntent recognize(String question) — 返回 null 表示本策略无法判定
    - default int priority() — 返回 100（扩展策略用），内置策略 1-99

    创建 IntentRecognitionService 编排器：
    - 构造器注入 List&lt;IntentRecognitionStrategy&gt;（Spring 自动发现所有实现）
    - @PostConstruct 按 priority 排序，log.info 打印策略链顺序
    - recognize(question) 方法：遍历策略链，首个返回 non-null 即成功；全部 null → throw BusinessException(A0019)
    - 日志：INFO 记录成功策略名；WARN 记录全部失败（含原始问题文本）
  </action>
  <verify>grep -n "IntentRecognitionStrategy\|IntentRecognitionService" src/main/java/com/graphnexus/application/query/chat/intent/IntentRecognitionStrategy.java src/main/java/com/graphnexus/application/query/chat/intent/IntentRecognitionService.java | head -20</verify>
  <done>接口 + 编排器编译通过；能注入 0 个策略（空链）时 recognize() 抛 A0019</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="pending">
  <name>新增 3 个 Prompt 模板文件</name>
  <read_files>
    src/main/resources/prompts/student-diagnosis-system.md
    src/main/resources/prompts/student-diagnosis-user.md
    application/query/prompt/service/PromptTemplateService.java
  </read_files>
  <write_files>
    src/main/resources/prompts/intent-classification-system.md
    src/main/resources/prompts/student-diagnosis-system-html.md
    src/main/resources/prompts/student-diagnosis-user-html.md
  </write_files>
  <action>
    见 ADR-025 D2、ADR-026 D3。

    1. intent-classification-system.md：LLM 意图分类 system prompt。
       - 角色：意图识别助手，分析用户问题判断查询意图
       - 输出约束：仅返回 JSON {"intent":"&lt;意图名&gt;"}，无法判断返回 {"intent":null}
       - 意图列表使用 {{intentList}} 占位符（由 PromptTemplateService 动态注入 QueryIntent 枚举值）
       - 含 few-shot 示例（正向：学生诊断；负向：闲聊→null）

    2. student-diagnosis-system-html.md：HTML+SVG 输出的 system prompt。
       - 角色：教育诊断专家（与 Markdown 版一致）
       - 输出格式约束：以 HTML 标签开头（&lt;h2&gt;/&lt;div&gt;/&lt;table&gt;），禁止 Markdown 标记，禁止前导语
       - 必须含至少 1 个 &lt;svg&gt; 元素
       - SVG 约束：xmlns + viewBox，柱宽≥30px/间距≥10px/字体≥12px，依赖图 circle r≥20，最大画布 800×600，最多 10 个数据点
       - 结构要求：h2 标题 → h3 学生信息 → table → h3 薄弱点 → svg 柱状图 → h3 依赖链 → svg 拓扑图 → h3 根因分析 → p 文本
       - 含正确示例（完整 HTML+SVG 片段）

    3. student-diagnosis-user-html.md：HTML+SVG 输出的 user prompt。
       - 与 Markdown 版 user prompt 结构相同（子图数据注入 + 用户问题），仅去掉 Markdown 格式约束
       - 变量占位符：{{studentName}}/{{studentNo}}/{{className}}/{{subject}}/{{subgraphText}}/{{userQuestion}}/{{weakThreshold}}/{{maxHops}}/{{mastersAvailable}}
  </action>
  <verify>ls -la src/main/resources/prompts/intent-classification-system.md src/main/resources/prompts/student-diagnosis-system-html.md src/main/resources/prompts/student-diagnosis-user-html.md</verify>
  <done>3 个模板文件创建完毕；含 {{var}} 占位符；含 few-shot 示例；SVG 约束完整</done>
  <depends_on></depends_on>
</task>

<task id="T05" parallel="true" status="pending">
  <name>PruningStrategyRegistry 策略注册表</name>
  <read_files>
    application/analysis/strategy/SubgraphPruningStrategy.java
    application/analysis/strategy/StudentDiagnosisStrategy.java
  </read_files>
  <write_files>
    application/query/chat/registry/PruningStrategyRegistry.java
  </write_files>
  <action>
    见 ADR-027、DESIGN D6。
    创建 PruningStrategyRegistry：
    - 构造器注入 Map&lt;String, SubgraphPruningStrategy&gt;（Spring 自动注入所有实现，key=bean name）
    - get(intentName) 方法：从 Map 获取策略，null → throw BusinessException(A0019)
    - @PostConstruct 时 log.info 打印已注册策略清单（bean name → 类名）
  </action>
  <verify>grep -n "PruningStrategyRegistry\|Map<String, SubgraphPruningStrategy>" src/main/java/com/graphnexus/application/query/chat/registry/PruningStrategyRegistry.java</verify>
  <done>Registry 编译通过；能正常注入 Spring 容器中所有 SubgraphPruningStrategy bean</done>
  <depends_on></depends_on>
</task>

<task id="T06" parallel="true" status="pending">
  <name>StudentDiagnosisStrategy 注册为 Spring Bean</name>
  <read_files>
    application/analysis/strategy/StudentDiagnosisStrategy.java
  </read_files>
  <write_files>
    application/analysis/strategy/StudentDiagnosisStrategy.java
  </write_files>
  <action>
    见 ADR-027 D2、AC-12。
    在 StudentDiagnosisStrategy 类上添加 @Component("STUDENT_DIAGNOSIS") 注解。
    Bean name = "STUDENT_DIAGNOSIS" = QueryIntent 枚举名，使 PruningStrategyRegistry.get("STUDENT_DIAGNOSIS") 能定位到此策略。
    类内剪枝逻辑（分步 Cypher）完全不改。
  </action>
  <verify>grep -n "@Component\|STUDENT_DIAGNOSIS" src/main/java/com/graphnexus/application/analysis/strategy/StudentDiagnosisStrategy.java</verify>
  <done>@Component("STUDENT_DIAGNOSIS") 注解添加完成；编译通过；PruningStrategyRegistry 可自动发现此 bean</done>
  <depends_on></depends_on>
</task>

<task id="T07" parallel="true" status="pending">
  <name>前端依赖安装 + API 类型扩展</name>
  <read_files>
    frontend/src/api/types.ts
    frontend/package.json
  </read_files>
  <write_files>
    frontend/package.json
    frontend/src/api/types.ts
  </write_files>
  <action>
    见 UI-DESIGN §2.3、AC-7。
    1. npm install dompurify @types/dompurify（dompurify 约 20KB gzipped）
    2. types.ts：QueryAskResponse 和 QueryResultResponse 各新增 outputFormat?: string 可选字段（向后兼容旧响应）
  </action>
  <verify>grep "dompurify" frontend/package.json && grep -n "outputFormat" frontend/src/api/types.ts</verify>
  <done>dompurify 依赖安装完成；TypeScript 类型含 outputFormat 可选字段；编译通过</done>
  <depends_on></depends_on>
</task>

<task id="T08" status="pending">
  <name>LlmIntentRecognitionStrategy + KeywordIntentRecognitionStrategy 实现</name>
  <read_files>
    application/query/chat/intent/IntentRecognitionStrategy.java
    application/query/chat/intent/IntentRecognitionService.java
    application/query/chat/model/QueryIntent.java
    application/query/prompt/service/PromptTemplateService.java
    common/LlmGateway.java
    src/main/resources/prompts/intent-classification-system.md
  </read_files>
  <write_files>
    application/query/chat/intent/LlmIntentRecognitionStrategy.java
    application/query/chat/intent/KeywordIntentRecognitionStrategy.java
    application/query/prompt/service/PromptTemplateService.java
  </write_files>
  <action>
    见 ADR-025 D1/D2/D3/D4、DESIGN D1。

    1. LlmIntentRecognitionStrategy (priority=10)：
       - @Component，注入 LlmGateway + PromptTemplateService + ObjectMapper
       - recognize(question)：调用 llmGateway.chat(systemPrompt, question) → parseClassificationResponse → 返回 QueryIntent 或 null
       - 异常处理：任何异常（超时/JSON解析失败/未知意图名）→ log.warn + 返回 null（不抛异常，交给下一策略）
       - JSON 解析容错：处理 ```json code fence、intent:null、未知意图名 → 均返回 null
       - intent-classification-system.md 的 {{intentList}} 占位符需 PromptTemplateService 新增方法 buildIntentList()：遍历 QueryIntent.values() 动态生成意图描述列表

    2. KeywordIntentRecognitionStrategy (priority=20)：
       - @Component
       - 内建关键词 Map（从现有 QueryServiceImpl.INTENT_KEYWORDS 迁移过来）
       - recognize(question)：遍历 Map，question.contains(key) → 返回对应 QueryIntent；全部不命中 → 返回 null
       - v2 新意图只需在 buildKeywordMap() 中追加关键词

    3. PromptTemplateService 新增方法：
       - buildIntentList()：遍历 QueryIntent.values() 生成 "- INTENT_NAME — description" 文本
  </action>
  <verify>grep -n "class LlmIntentRecognitionStrategy\|class KeywordIntentRecognitionStrategy" src/main/java/com/graphnexus/application/query/chat/intent/LlmIntentRecognitionStrategy.java src/main/java/com/graphnexus/application/query/chat/intent/KeywordIntentRecognitionStrategy.java</verify>
  <done>两个策略实现编译通过；LlmIntentRecognitionStrategy 异常时返回 null（不抛异常）；KeywordIntentRecognitionStrategy 关键词 Map 与迁移前一致</done>
  <depends_on>T03, T04</depends_on>
</task>

<task id="T09" parallel="true" status="pending">
  <name>PromptTemplateService 格式感知模板加载</name>
  <read_files>
    application/query/prompt/service/PromptTemplateService.java
    src/main/resources/prompts/student-diagnosis-system.md
    src/main/resources/prompts/student-diagnosis-user.md
    src/main/resources/prompts/student-diagnosis-system-html.md
    src/main/resources/prompts/student-diagnosis-user-html.md
  </read_files>
  <write_files>
    application/query/prompt/service/PromptTemplateService.java
  </write_files>
  <action>
    见 ADR-026 D2、DESIGN D3。
    扩展 PromptTemplateService.buildPrompt() 方法签名：
    - 新增重载：buildPrompt(QueryIntent intent, Map&lt;String,String&gt; variables, String format)
    - 模板加载逻辑：format="markdown" → 加载 {intent}-system.md（现有文件名，向后兼容）
    - format="html-svg" → 先尝试 {intent}-system-html.md，不存在则 fallback 到 {intent}-system.md
    - 原有 buildPrompt(intent, variables) 方法保留，内部调用新重载并传入 format="markdown"（向后兼容）
  </action>
  <verify>grep -n "buildPrompt.*format\|loadTemplateWithFormat\|-html" src/main/java/com/graphnexus/application/query/prompt/service/PromptTemplateService.java</verify>
  <done>buildPrompt(intent, vars, format) 重载编译通过；markdown 模式加载现有模板不变；html-svg 模式加载 -html.md 后缀模板</done>
  <depends_on>T04</depends_on>
</task>

<task id="T10" parallel="true" status="pending">
  <name>HtmlSvgViewer.vue 安全渲染组件</name>
  <read_files>
    frontend/src/common/components/MarkdownViewer.vue
    frontend/src/assets/tokens.css
    frontend/src/assets/global.css
  </read_files>
  <write_files>
    frontend/src/common/components/HtmlSvgViewer.vue
  </write_files>
  <action>
    见 UI-DESIGN §5.1、ADR-026 D5、AC-7。
    创建 HtmlSvgViewer.vue：
    - Props: content: string, maxWidth?: number (默认 720)
    - computed sanitizedHtml：DOMPurify.sanitize(content, CONFIG)
    - template：&lt;div class="html-svg-viewer body" :style="{ maxWidth: maxWidth + 'px' }" v-html="sanitizedHtml" /&gt;
    - DOMPurify CONFIG（组件内常量，不暴露为 prop）：
      * ALLOWED_TAGS：HTML 结构（h2/h3/h4/p/div/span/table/thead/tbody/tr/th/td/ul/ol/li/strong/em/br/hr）
        + SVG 图形（svg/g/circle/ellipse/rect/line/path/polygon/polyline/text/tspan/defs/linearGradient/stop/title/desc）
        + MathML（math/mi/mo/mn/mrow/msup/mfrac/msqrt/mroot）
      * ALLOWED_ATTR：标准 HTML + SVG 属性（class/id/style/d/cx/cy/r/x/y/width/height/viewBox/xmlns/fill/stroke/stroke-width/text-anchor/font-size 等）
      * FORBID_TAGS：script/foreignObject/iframe/object/embed/use
      * FORBID_ATTR：onclick/onload/onerror/xlink:href
    - scoped CSS：:deep() 排版规则对标 MarkdownViewer（h2/h3/p/table/th/td/strong/blockquote/svg）
    - SVG 溢出：max-width: 100%; height: auto; overflow-x: auto
    - 空 content → 静默空 div（不显示占位提示）
  </action>
  <verify>grep -n "DOMPurify\|ALLOWED_TAGS\|html-svg-viewer" frontend/src/common/components/HtmlSvgViewer.vue | head -10</verify>
  <done>组件编译通过；DOMPurify 配置含白名单标签+属性+禁止清单；:deep() 排版与 MarkdownViewer 对称；空 content 渲染空 div</done>
  <depends_on>T07</depends_on>
</task>

<task id="T11" status="pending">
  <name>QueryServiceImpl 重构 — 集成意图识别链 + 策略注册表 + 格式感知</name>
  <read_files>
    application/query/chat/service/impl/QueryServiceImpl.java
    application/query/chat/intent/IntentRecognitionService.java
    application/query/chat/registry/PruningStrategyRegistry.java
    application/query/chat/config/QueryProperties.java
    application/query/prompt/service/PromptTemplateService.java
    application/query/chat/model/QueryResultBO.java
  </read_files>
  <write_files>
    application/query/chat/service/impl/QueryServiceImpl.java
  </write_files>
  <action>
    见 DESIGN D1/D2/D4/D6、AC-1/AC-2/AC-3/AC-4/AC-5/AC-6/AC-12。
    QueryServiceImpl 重构（单一文件，3 处变更）：

    1. 依赖注入替换：
       - 删除：private final StudentDiagnosisStrategy diagnosisStrategy
       - 新增：private final IntentRecognitionService intentRecognitionService
       - 新增：private final PruningStrategyRegistry pruningStrategyRegistry

    2. recognizeIntent() 重构（AC-1/AC-2/AC-3）：
       - 删除现有 INTENT_KEYWORDS Map + recognizeIntent() 方法体
       - 替换为：return intentRecognitionService.recognize(question)
       - 意图识别失败时 IntentRecognitionService 内部抛 BusinessException(A0019)，QueryServiceImpl 不额外处理

    3. 剪枝步骤（ask/askInternal/chat 三处）：
       - 替换：diagnosisStrategy.prune(pruningRequest)
       - 为：pruningStrategyRegistry.get(intent.name()).prune(pruningRequest)

    4. 格式感知（ask/askInternal 两处）：
       - 读取 queryProperties.getOutput().getFormat()
       - buildPrompt 调用：promptTemplateService.buildPrompt(intent, templateVars, format)
       - 构建 QueryResultBO 时传入 outputFormat = format
       - callLlmWithRetry 方法：
         * 新增 format 参数
         * 按 format 选择校验器（"markdown" → validateMarkdownResponse / 其他 → validateHtmlSvgResponse）
         * 按 format 注入重试修正提示（Markdown: "请务必以 ## 标题开头..." / HTML+SVG: "请务必以 HTML 标签开头，包含至少一个 &lt;svg&gt; 元素..."）

    5. HTML+SVG 校验方法（AC-4/AC-5）：
       - 新增 validateHtmlSvgResponse(String response)：以 &lt; 开头 + 包含 &lt;svg + 含 xmlns + 不含 Markdown 标记 + 不含前导语
       - 现有 validateLlmResponse 保留不变（Markdown 模式使用）

    6. askAsync/executeAsync 路径：
       - 同 ask 路径的变更（意图识别委托 + 策略注册表 + 格式感知）
       - askInternal 方法同步更新

    7. 清理：
       - 删除 INTENT_KEYWORDS 静态 Map（已迁移到 KeywordIntentRecognitionStrategy）
       - 删除未使用的 import
  </action>
  <verify>grep -n "intentRecognitionService\|pruningStrategyRegistry\|getOutput\|validateHtmlSvgResponse\|getFormat" src/main/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImpl.java | head -20</verify>
  <done>QueryServiceImpl 编译通过；不再直接注入 StudentDiagnosisStrategy；不再含 INTENT_KEYWORDS Map；格式感知逻辑通过 grep 确认存在</done>
  <depends_on>T01, T02, T05, T06, T08, T09</depends_on>
</task>

<task id="T12" parallel="true" status="pending">
  <name>前端页面集成 — 按 outputFormat 选择渲染器</name>
  <read_files>
    frontend/src/views/query/IntelligentQAPage.vue
    frontend/src/views/query/components/MarkdownReport.vue
    frontend/src/views/query/queryStore.ts
    frontend/src/common/components/HtmlSvgViewer.vue
    frontend/src/common/components/MarkdownViewer.vue
    frontend/src/api/types.ts
  </read_files>
  <write_files>
    frontend/src/views/query/components/MarkdownReport.vue
    frontend/src/views/query/IntelligentQAPage.vue
    frontend/src/views/query/queryStore.ts
  </write_files>
  <action>
    见 UI-DESIGN §5.2、AC-8。

    1. queryStore.ts：
       - 新增 state: outputFormat ref&lt;string&gt;('markdown')（默认 markdown 向后兼容）
       - handleResult() 中从 response 读取 outputFormat 并存储
       - 返回 outputFormat 到组件

    2. MarkdownReport.vue：
       - 新增 prop: outputFormat?: string
       - 条件渲染：v-if="outputFormat === 'html-svg'" → HtmlSvgViewer / v-else → MarkdownViewer
       - 缺省 outputFormat 时 fallback 到 MarkdownViewer（向后兼容旧响应）

    3. IntelligentQAPage.vue：
       - 从 store 读取 outputFormat
       - 传给 MarkdownReport：&lt;MarkdownReport :content="item.answer" :output-format="store.outputFormat" /&gt;
       - 模板其他部分不变
  </action>
  <verify>grep -n "outputFormat\|HtmlSvgViewer\|MarkdownViewer" frontend/src/views/query/components/MarkdownReport.vue frontend/src/views/query/IntelligentQAPage.vue frontend/src/views/query/queryStore.ts</verify>
  <done>前端编译通过；outputFormat='html-svg' 时渲染 HtmlSvgViewer；outputFormat='markdown' 或缺省时渲染 MarkdownViewer</done>
  <depends_on>T02, T10</depends_on>
</task>

<task id="T13" parallel="true" status="pending">
  <name>后端单元测试 — 意图识别 + 格式感知 + 策略注册</name>
  <read_files>
    application/query/chat/intent/IntentRecognitionStrategy.java
    application/query/chat/intent/IntentRecognitionService.java
    application/query/chat/intent/LlmIntentRecognitionStrategy.java
    application/query/chat/intent/KeywordIntentRecognitionStrategy.java
    application/query/chat/registry/PruningStrategyRegistry.java
    application/query/chat/config/QueryProperties.java
    application/query/prompt/service/PromptTemplateService.java
    src/test/java/com/graphnexus/application/query/
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/query/chat/intent/IntentRecognitionServiceTest.java
    src/test/java/com/graphnexus/application/query/chat/intent/KeywordIntentRecognitionStrategyTest.java
    src/test/java/com/graphnexus/application/query/chat/registry/PruningStrategyRegistryTest.java
  </write_files>
  <action>
    覆盖以下测试场景（JUnit 5 + Mockito）：

    1. IntentRecognitionServiceTest：
       - 注入 2 个 mock 策略（priority=10 返回 STUDENT_DIAGNOSIS, priority=20 不应被调用）→ 验证链在首个成功策略处停止
       - 注入 2 个 mock 策略（priority=10 返回 null, priority=20 返回 STUDENT_DIAGNOSIS）→ 验证 fallback 生效
       - 注入 2 个 mock 策略（均返回 null）→ 验证抛 BusinessException(A0019)
       - 注入空列表 → 验证抛 BusinessException(A0019)
       - 验证策略按 priority 排序

    2. KeywordIntentRecognitionStrategyTest：
       - "分析学生张三的数学薄弱点" → STUDENT_DIAGNOSIS（含"薄弱"+"分析学生"双命中）
       - "帮我看看李四数学怎么样" → null（无关键词命中）
       - null/空字符串输入 → null

    3. PruningStrategyRegistryTest：
       - 注入 Map("STUDENT_DIAGNOSIS" → mockStrategy) → get("STUDENT_DIAGNOSIS") 返回 mockStrategy
       - get("UNKNOWN_INTENT") → 抛 BusinessException(A0019)

    测试依赖：Mockito mock、不启动 Spring 上下文（纯单元测试）
  </action>
  <verify>mvn test -pl . -Dtest="IntentRecognitionServiceTest,KeywordIntentRecognitionStrategyTest,PruningStrategyRegistryTest" -DfailIfNoTests=false 2>&1 | tail -20</verify>
  <done>3 个测试类全部绿色；覆盖 AC-1/AC-2/AC-3/AC-12 的关键路径</done>
  <depends_on>T11</depends_on>
</task>

<task id="T14" parallel="true" status="pending">
  <name>集成测试 — 端到端验证 + 前端安全验证</name>
  <read_files>
    src/test/java/com/graphnexus/api/query/controller/QueryControllerIntegrationTest.java
    frontend/src/common/components/HtmlSvgViewer.vue
    frontend/src/views/query/components/MarkdownReport.vue
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/api/query/controller/QueryControllerIntegrationTest.java
  </write_files>
  <action>
    扩展既有 QueryControllerIntegrationTest：

    1. AC-1 验证：LLM 意图识别成功 — mock LlmGateway 返回 {"intent":"STUDENT_DIAGNOSIS"} → POST /chat → 断言 HTTP 200 + intent=STUDENT_DIAGNOSIS

    2. AC-2 验证：规则 fallback — mock LlmGateway 抛异常 → POST /chat（含关键词"薄弱"）→ 断言 HTTP 200 + intent=STUDENT_DIAGNOSIS

    3. AC-3 验证：双重失败 — mock LlmGateway 抛异常 → POST /chat（无关键词）→ 断言 HTTP 400 + errorCode=A0019

    4. AC-4 验证：HTML+SVG 输出 — 配置 output-format=html-svg，mock LlmGateway 返回合法 HTML+SVG → POST /ask → 断言 outputFormat=html-svg + answer 以 &lt; 开头

    5. AC-6 验证：Markdown 回滚 — 配置 output-format=markdown → 断言 outputFormat=markdown + answer 以 # 开头

    6. AC-11 验证：向后兼容 — 响应 JSON 含 outputFormat 字段，JSON 解析器忽略未知字段不报错

    测试依赖：使用 @SpringBootTest + @ActiveProfiles("dev")（直连 podman 中的真实 Neo4j/MySQL），LlmGateway 使用 @MockBean
  </action>
  <verify>mvn test -pl . -Dtest="QueryControllerIntegrationTest" -DfailIfNoTests=false 2>&1 | tail -20</verify>
  <done>集成测试全部绿色；覆盖 AC-1/AC-2/AC-3/AC-4/AC-6/AC-11</done>
  <depends_on>T11, T12</depends_on>
</task>
```

---

## 状态字段说明

- `status="pending"` — 未开始
- `status="in_progress"` — 进行中（同时只允许一个非 [P] 任务为此状态）
- `status="done"` — 已完成（verify 通过）
- `status="blocked"` — 阻塞（必须在文件末尾「阻塞日志」记录）

---

## 阻塞日志

| 任务 | 阻塞原因 | 待人工决策项 | 时间 |
|---|---|---|---|
|  |  |  |  |

---

## Fix 任务（来自 REVIEW / INTEGRATION）

> 此区域由 review/integration 阶段自动追加，编号 `T-FIX-XX`。

```xml
<!-- 占位 -->
```