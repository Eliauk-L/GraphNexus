# REQUIREMENT: 接入 MinerU API 作为主 PDF 解析器（v1 默认 + v4 可切换）

- **Change ID**: `mineru-pdf-parser`
- **关联**: `@.specs/mineru-pdf-parser/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为系统（自动化流程），我想通过 MinerU v1 Agent API（默认）解析 PDF 教辅文件并开启公式识别，以便提取的 `textContent` 中公式以 Markdown LaTeX 格式保留（如 `$E=mc^2$`），提升后续 LLM 图谱抽取的公式实体质量。通过配置 `mineru.api.version=v4` 可切换到 v4 精准解析。

- **US-2**：作为系统，当 MinerU 调用发生任意异常（网络错误、超时、`state=failed`、轮询超时等）时，我想自动降级到 `PdfBoxDocumentParser` 完成解析，以便解析流程不中断，用户无感知。

- **US-3**：作为运维人员，我想通过 `application-dev.yml` 配置 MinerU 的版本、连接参数、API 路径和行为开关（v1/v4 切换、Token、轮询超时、公式识别开关、全局启用/禁用），以便无需重新编译即可调整解析行为。未来新增自部署 MinerU 服务只需新增一个 `@Component` 实现类 + yml 配置。

- **US-4**：作为开发者，我想 `MinerUApiClient` 策略接口支持 v1/v4/自部署三种实现，Spring Bean 自动发现，新增实现只需加 `@Component` + 返回 `getVersion()`，`MinerUClient` 零改动。

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · MinerU 正常解析含公式的 PDF

- **Given** MinerU v1 API 可达，且 `mineru.enabled=true`
- **When** 对含数学公式的有效 PDF 调用 `DocumentServiceImpl.process(documentId)`
- **Then** 返回的 `ParseResult.textContent` 包含 Markdown LaTeX 公式标记（如 `$...$` 或 `$$...$$`），`pageCount` ≥ 0；`metadata.parser` = `mineru-v1`；日志包含解析器名称和耗时
- **验证方式**: `mvn test` 中 `MinerUDocumentParserTest` 使用 Mockito mock `MinerUClient`，断言 parse 结果含公式标记

### AC-2 · MinerU 返回 state=failed 时自动 fallback 到 PDFBox

- **Given** MinerU API 可达，但解析任务最终 `state=failed`
- **When** 调用 `DocumentServiceImpl.process(documentId)`
- **Then** 日志输出 MinerU 失败信息 + `fallback to PDFBox`；`PdfBoxDocumentParser.parse()` 完成解析；文档 `COMPLETED`；`metadata.parser` = `pdfbox-fallback`
- **验证方式**: `mvn test` 中 mock `MinerUDocumentParser` 抛异常，注入真实 `PdfBoxDocumentParser`，断言 status=COMPLETED

### AC-3 · MinerU 网络不可达时自动 fallback 到 PDFBox

- **Given** MinerU API 不可达（连接超时或 DNS 解析失败）
- **When** 调用 `DocumentServiceImpl.process(documentId)`
- **Then** 不抛异常；日志输出 `fallback to PDFBox`；PDFBox 完成解析；文档 `COMPLETED`
- **验证方式**: `application-dev.yml` 中 `mineru.api.base-url=http://localhost:19999`，验证解析仍成功

### AC-4 · MinerU 和 PDFBox 均失败时标记 FAILED

- **Given** MinerU 不可达，且 PDF 文件损坏导致 PDFBox 也失败
- **When** 调用 `DocumentServiceImpl.process(documentId)`
- **Then** 文档 `FAILED`；`fail_reason` 包含 MinerU + PDFBox 双重错误信息
- **验证方式**: 集成测试：MinerU 指向无效 URL + 传入损坏 PDF，断言 status=FAILED

### AC-5 · 可通过配置关闭 MinerU 直接使用 PDFBox

- **Given** `mineru.enabled=false`
- **When** 调用 `DocumentServiceImpl.process(documentId)`
- **Then** 不发起任何 MinerU HTTP 请求；直接 PDFBox 完成解析；日志含 `MinerU disabled`
- **验证方式**: 单元测试：`mineru.enabled=false`，断言无 HTTP 请求

### AC-6 · 现有 PDFBox 单解析路径行为不变

- **Given** `mineru.enabled=false` 时 `DocumentParser` 注入 `PdfBoxDocumentParser`
- **When** 执行现有 process 流程
- **Then** 行为与 `main` 分支完全一致
- **验证方式**: 运行 `mvn test`，现有 `PdfBoxDocumentParserTest`、`DocumentServiceTest` 全部通过

### AC-7 · v1/v4 切换可通过配置完成

- **Given** 配置文件设置 `mineru.api.version=v4` + 正确的 `submit-path`、`poll-path-template`、`token`
- **When** 调用 `DocumentServiceImpl.process(documentId)`
- **Then** 使用 `MinerUV4Client` 处理请求（POST /api/v4/file-urls/batch → 轮询 batch → zip 解压）
- **验证方式**: `MinerUV4Client` 单元测试（MockRestServiceServer 模拟 v4 端点）

---

## 范围切分

### v1（本次必做）

- `MinerUApiClient` 策略接口 + `MinerUV1Client` + `MinerUV4Client` + `MinerUClient` 路由
- Spring Bean 自动发现（`List<MinerUApiClient>` 注入 + `getVersion()` 匹配）
- `MinerUDocumentParser` 实现 `DocumentParser` 接口
- `DocumentServiceImpl.process()` try-MinerU → catch-all-fallback-PDFBox 策略
- MinerU 配置项：`version`、`submit-path`、`poll-path-template`、`base-url`、`token`、`parser-name`、`poll-timeout`、`poll-interval`、`enable-formula`、`enable-table`、`language`
- 日志记录解析器名称 + fallback 原因 + 耗时
- `ParseResult.metadata.parser` 字段标记使用的解析器
- Spring 6.1 `RestClient`（零新增依赖）
- OSS 上传清空所有请求头
- API 路径可通过 yml 配置覆盖

### v2（下一轮考虑，不本次）

- callback 模式替代轮询
- 批量解析并行化
- 解析结果缓存（同 MD5 不重复解析）
- 解析进度实时推送

### out（永远不做）

- 移除 PDFBox 依赖（PDFBox 永久作为最终兜底）
- 修改 `DocumentParser` 接口契约（`byte[] → ParseResult` 不变）
- 改造 `FileParser` 接口统一（留待独立 refactor change）
- 支持非 PDF 文件类型

---

## 非功能性需求

- **性能**: 轮询超时默认 300s（可配置），轮询间隔默认 3s；fallback 总耗时 = MinerU 超时 + PDFBox 时间
- **可访问性**: 无（纯后端）
- **安全**: v1 免 Token，v4 Token 通过环境变量 `${MINERU_API_TOKEN}` 注入；HTTPS 传输
- **兼容性**: `DocumentParser` 接口不变；`DocumentServiceImpl` 对外行为不变；JDK 17 + Spring Boot 3.3.x
- **可观测性**: INFO 日志（解析器名称 + fallback + 耗时）；WARN（MinerU 原始错误）；Trace ID 贯穿；`metadata.parser` 持久化到 MySQL

## 依赖与假设

- **依赖**: MinerU API 服务（`https://mineru.net`）网络可达
- **依赖**: MinerU OSS 上传地址网络可达
- **依赖**: Spring 6.1 `RestClient`（`spring-boot-starter-web` 已传递引入，**无新增依赖**）
- **依赖**: `init-platform` 已有 `BusinessException`、`ErrorCode` 体系
- **依赖**: `document-process-pdf-minimal` 已完成的 `DocumentParser` + `PdfBoxDocumentParser` + `DocumentServiceImpl`
- **假设**: v1 API IP 限频在正常使用下不会触发 429；触发时走 PDFBox 兜底
- **假设**: v1 的 10MB/20 页限制对大部分教辅 PDF 够用；超限自动降级 PDFBox，可切换到 v4 解决

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。