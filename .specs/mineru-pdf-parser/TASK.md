# TASK: 接入 MinerU API 作为主 PDF 解析器（v1 默认 + v4 可切换）

- **Change ID**: `mineru-pdf-parser`
- **关联**: `@.specs/mineru-pdf-parser/REQUIREMENT.md`、`@.specs/mineru-pdf-parser/CHANGE.md`

---

## 波次划分

```
Wave 1:            T01 — MinerU 基础设施层（配置 + MinerUApiClient 接口 + v1/v4 实现 + MinerUClient 路由）
Wave 2:            T02 — MinerUDocumentParser 实现（depends on T01）
Wave 3:            T03 — DocumentServiceImpl 兜底改造（depends on T02）
Wave 4 (parallel): T04[P], T05[P] — 测试（depends on T03）
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T01" parallel="false" status="done">
  <name>MinerU 基础设施层 — 配置 + MinerUApiClient 策略接口 + v1/v4 实现 + MinerUClient 路由</name>
  <read_files>
    src/main/resources/application.yml
    src/main/resources/application-dev.yml
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    src/main/java/com/graphnexus/common/exception/BusinessException.java
    src/main/java/com/graphnexus/infrastructure/storage/config/MinioProperties.java
  </read_files>
  <write_files>
    src/main/resources/application-dev.yml
    src/main/java/com/graphnexus/infrastructure/mineru/config/MinerUProperties.java
    src/main/java/com/graphnexus/infrastructure/mineru/client/MinerUApiClient.java
    src/main/java/com/graphnexus/infrastructure/mineru/client/MinerUV1Client.java
    src/main/java/com/graphnexus/infrastructure/mineru/client/MinerUV4Client.java
    src/main/java/com/graphnexus/infrastructure/mineru/client/MinerUClient.java
    src/main/java/com/graphnexus/infrastructure/mineru/package-info.java
    src/main/java/com/graphnexus/infrastructure/mineru/config/package-info.java
    src/main/java/com/graphnexus/infrastructure/mineru/client/package-info.java
  </write_files>
  <action>
    1. MinerUProperties（@ConfigurationProperties(prefix="mineru")），沿用 MinioProperties 模式：
       - mineru.enabled / mineru.api.version (v1/v4/custom) / base-url / token
       - **mineru.api.submit-path**（POST 提交路径，可配置覆盖）
       - **mineru.api.poll-path-template**（GET 轮询路径，{taskId} 占位符，可配置覆盖）
       - poll-timeout / poll-interval / parser-name / model-version
       - parse.enable-formula / enable-table / language

    2. MinerUApiClient 策略接口：
       - `getVersion()` 返回版本标识（"v1"/"v4"/"custom"），与 yml 配置匹配
       - `submitTask(fileName)` → TaskSubmitResult(taskId, fileUrl)
       - `pollTaskResult(taskId)` → TaskPollResult(state, resultUrl, errMsg)
       - `downloadResult(resultUrl)` → String markdown

    3. MinerUV1Client（@Component，v1 Agent API，免 Token）：
       - POST /api/v1/agent/parse/file（路径从 properties.getSubmitPath() 读取）
       - GET /api/v1/agent/parse/{taskId}（路径从 properties.getPollPathTemplate() 读取）
       - downloadResult 直接 GET 文本（非 zip）
       - RestClient 不含 Authorization 头

    4. MinerUV4Client（@Component，v4 API，需 Token）：
       - POST /api/v4/file-urls/batch（Bearer Token，files 数组）
       - GET /api/v4/extract-results/batch/{batchId}
       - downloadResult GET zip → ZipInputStream 解压提取 full.md

    5. MinerUClient（@Service，路由门面 + uploadFile 共用）：
       - 注入 List&lt;MinerUApiClient&gt;，按 version 自动匹配
       - uploadFile：独立 RestClient，清空所有请求头（requestInterceptor 清空 headers + setContentLength）
       - submitTask/pollTaskResult/downloadMarkdown 委托给选中的 apiClient
       - 未找到匹配版本时抛 IllegalStateException（含可用版本列表）

    6. 使用 Spring 6.1 RestClient（spring-web 已包含，零新增 pom.xml 依赖）
    7. 配置移至 application-dev.yml（application.yml 不留 mineru 配置）
  </action>
  <verify>mvn test -Dtest="MinerUClientTest"</verify>
  <done>7 个测试通过；v1/v4 切换可用 yml 配置控制；新增实现只需加 @Component</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="false" status="done">
  <name>MinerUDocumentParser 实现 DocumentParser 接口</name>
  <read_files>
    src/main/java/com/graphnexus/application/document/parser/DocumentParser.java
    src/main/java/com/graphnexus/application/document/parser/PdfBoxDocumentParser.java
    src/main/java/com/graphnexus/application/document/model/ParseResult.java
    src/main/java/com/graphnexus/infrastructure/mineru/client/MinerUClient.java
    src/main/java/com/graphnexus/infrastructure/mineru/client/MinerUApiClient.java
    src/main/java/com/graphnexus/infrastructure/mineru/config/MinerUProperties.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/document/parser/MinerUDocumentParser.java
  </write_files>
  <action>
    MinerUDocumentParser（@Service），实现 DocumentParser：

    1. 构造器注入 MinerUClient + MinerUProperties
    2. parse(byte[] pdfBytes) 流程：
       a. 检查 mineru.enabled，false → 抛异常（由上层 fallback）
       b. mineruClient.submitTask(fileName) → taskId + fileUrl
       c. mineruClient.uploadFile(fileUrl, pdfBytes) → PUT 到 OSS
       d. mineruClient.pollTaskResult(taskId) → 轮询等待 done/failed
       e. state=failed → 抛 BusinessException(C0001)
       f. mineruClient.downloadMarkdown(resultUrl) → Markdown 文本
       g. ParseResult(textContent, 0, metadata={parser, model, taskId})
    3. 日志：开始解析(fileSize)、轮询进度(state)、完成(textLength, elapsed)
    4. 参考 PdfBoxDocumentParser 风格：@Slf4j、try-catch、中文日志
  </action>
  <verify>mvn test -Dtest="MinerUDocumentParserTest"</verify>
  <done>6 个测试通过；parse() 完成完整 v1 调用链；disabled 时抛异常供上层 fallback</done>
  <depends_on>T01</depends_on>
</task>

<task id="T03" parallel="false" status="done">
  <name>DocumentServiceImpl 兜底改造 + ParseResult metadata 标记解析器</name>
  <read_files>
    src/main/java/com/graphnexus/application/document/service/impl/DocumentServiceImpl.java
    src/main/java/com/graphnexus/application/document/parser/MinerUDocumentParser.java
    src/main/java/com/graphnexus/application/document/parser/PdfBoxDocumentParser.java
    src/main/java/com/graphnexus/infrastructure/mineru/config/MinerUProperties.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/document/service/impl/DocumentServiceImpl.java
  </write_files>
  <action>
    修改 DocumentServiceImpl.process()：

    1. 注入 MinerUDocumentParser + PdfBoxDocumentParser + MinerUProperties（替换原 DocumentParser）
    2. 显式构造器（移除 @RequiredArgsConstructor）
    3. process() 解析流程：
       - mineru.enabled=true → try minerUDocumentParser.parse()
       - catch(Exception) → WARN + PDFBox fallback，metadata.parser="pdfbox-fallback"
       - mineru.enabled=false → 直接 PDFBox，metadata.parser="pdfbox-direct"
       - MinerU 成功 → metadata.parser=properties.getApi().getParserName()
       - PDFBox fallback 也失败 → FAILED + 双重 fail_reason
    4. metadata 写入 MySQL metadata_json 列
    5. 日志：parserUsed + fallback 原因 + 耗时
  </action>
  <verify>mvn test -Dtest="DocumentServiceTest#processShouldSucceed"</verify>
  <done>MinerU 优先→PDFBox 兜底 链路编译通过；mineru.enabled=false 直接走 PDFBox</done>
  <depends_on>T02</depends_on>
</task>

<task id="T04" parallel="true" status="done">
  <name>MinerUClient + MinerUDocumentParser 单元测试</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mineru/client/MinerUClient.java
    src/main/java/com/graphnexus/infrastructure/mineru/client/MinerUV1Client.java
    src/main/java/com/graphnexus/infrastructure/mineru/config/MinerUProperties.java
    src/main/java/com/graphnexus/application/document/parser/MinerUDocumentParser.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/infrastructure/mineru/client/MinerUClientTest.java
    src/test/java/com/graphnexus/application/document/parser/MinerUDocumentParserTest.java
  </write_files>
  <action>
    1. MinerUClientTest（7 用例，MockRestServiceServer 模拟 v1 API）：
       - submitTask 成功返回 taskId + fileUrl
       - submitTask API 错误码抛 BusinessException
       - submitTask 网络异常抛 BusinessException
       - pollTaskResult 轮询 running→done 返回 markdownUrl
       - pollTaskResult state=failed 标记 isFailed
       - downloadMarkdown 下载 Markdown 文本
       - downloadMarkdown 空文本抛异常

    2. MinerUDocumentParserTest（6 用例，mock MinerUClient）：
       - 正常解析全流程（submit→upload→poll→download）
       - disabled 时抛异常
       - null/空 字节数组抛异常
       - poll 返回 failed 时抛异常
       - submit 异常向上传播
  </action>
  <verify>mvn test -Dtest="MinerUClientTest,MinerUDocumentParserTest"</verify>
  <done>13 个测试全部通过；覆盖正常流程、异常 fallback、边界条件</done>
  <depends_on>T03</depends_on>
</task>

<task id="T05" parallel="true" status="done">
  <name>DocumentServiceImpl 兜底集成测试 + 现有测试回归</name>
  <read_files>
    src/main/java/com/graphnexus/application/document/service/impl/DocumentServiceImpl.java
    src/test/java/com/graphnexus/application/document/service/DocumentServiceTest.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/document/service/DocumentServiceTest.java
  </write_files>
  <action>
    1. 更新 DocumentServiceTest：
       - @Mock MinerUDocumentParser + @Mock PdfBoxDocumentParser + @Mock MinerUProperties
       - setUp 中 minerUProperties.isEnabled()=false（默认走 PDFBox）
       - processShouldSucceed 使用 pdfBoxDocumentParser mock
       - AC-6 测试调整为验证 PdfBoxDocumentParser 独立可用

    2. 运行全量测试验证 AC-6（回归）：
       - PdfBoxDocumentParserTest
       - DocumentServiceTest
       - MinerUClientTest
       - MinerUDocumentParserTest
  </action>
  <verify>mvn test -Dtest="DocumentServiceTest,PdfBoxDocumentParserTest,MinerUClientTest,MinerUDocumentParserTest"</verify>
  <done>全部测试通过；现有 PDFBox 解析路径行为不变</done>
  <depends_on>T03</depends_on>
</task>
```

---

## 状态字段说明

- `status="pending"` — 未开始
- `status="in_progress"` — 进行中
- `status="done"` — 已完成（verify 通过）

---

## 关键设计决策摘要

| 决策 | 选择 |
|---|---|
| HTTP 客户端 | Spring 6.1 `RestClient`（零新增依赖） |
| API 默认版本 | v1 Agent API（免 Token） |
| v4 支持 | `mineru.api.version=v4` + 配置路径 |
| 扩展方式 | `@Component implements MinerUApiClient` + `getVersion()` |
| URL 路径 | 可配置（`submit-path` / `poll-path-template`） |
| 文件上传 | 清空所有请求头，仅保留 Content-Length |
| 兜底策略 | MinerU 所有异常 → PDFBox → 双失败才 FAILED |
| OSS 上传响应 | `toBodilessEntity()` 处理空 body |

## 阻塞日志

| 任务 | 阻塞原因 | 待人工决策项 | 时间 |
|---|---|---|---|
|  |  |  |  |

## Fix 任务（来自 REVIEW / INTEGRATION）

> 此区域由 review/integration 阶段自动追加。

```xml
<!-- 占位 -->
```