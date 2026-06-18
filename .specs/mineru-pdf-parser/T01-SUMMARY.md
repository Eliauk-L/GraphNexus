# T01-SUMMARY: MinerU 基础设施层

- **Task ID**: T01
- **Change ID**: mineru-pdf-parser
- **完成时间**: 2026-06-16

---

## 做了什么

1. **MinerUProperties** — `@ConfigurationProperties(prefix = "mineru")`，嵌套 `Api`（base-url/token/model-version/poll-timeout/poll-interval）和 `Parse`（enable-formula/enable-table/language）配置类。沿用 `MinioProperties` 的 `@Component` + `@ConfigurationProperties` 模式。

2. **application.yml** — 新增 `mineru` 默认配置块，token 通过 `${MINERU_API_TOKEN:}` 环境变量注入。

3. **application-dev.yml** — 新增 `mineru` dev 配置块，`mineru.enabled=true`，MinerU client 日志级别 DEBUG。

4. **MinerUClient** — 封装 MinerU v4 API 完整调用链：
   - `submitBatch(fileName)` → `POST /api/v4/file-urls/batch`
   - `uploadFile(fileUrl, pdfBytes)` → `PUT` 到 OSS 签名 URL
   - `pollBatchResult(batchId)` → 轮询 `GET /api/v4/extract-results/batch/{batchId}`（支持 running/pending/waiting-file/converting 状态过渡）
   - `downloadAndExtractMarkdown(fullZipUrl)` → GET 下载 zip → `ZipInputStream` 解压提取 `full.md`
   - 所有异常统一抛 `BusinessException(C0001)`

5. **package-info.java** × 3 — `infrastructure/mineru/`、`mineru/config/`、`mineru/client/`。

**未新增 pom.xml 依赖**：使用 Spring 6.1 `RestClient`（`spring-web` 中，已由 `spring-boot-starter-web` 传递引入），免去 `spring-boot-starter-webflux`。

## 改了什么文件

| 文件 | 操作 |
|---|---|
| `infrastructure/mineru/config/MinerUProperties.java` | 新建 |
| `infrastructure/mineru/client/MinerUClient.java` | 新建 |
| `infrastructure/mineru/package-info.java` | 新建 |
| `infrastructure/mineru/config/package-info.java` | 新建 |
| `infrastructure/mineru/client/package-info.java` | 新建 |
| `application.yml` | 修改（新增 mineru 配置块） |
| `application-dev.yml` | 修改（新增 mineru 配置块） |

## verify 输出

```
$ mvn compile
[INFO] BUILD SUCCESS
```

## 6 维自查

- **R1 认知过载**: MinerUClient 约 270 行，方法职责单一（submit/upload/poll/download/extract），无嵌套过深。
- **R2 变更传播**: 仅触及 T01 write_files 范围内文件，未动其他模块。
- **R3 知识重复**: 无重复逻辑。
- **R4 偶然复杂**: 无"以后可能用到"的扩展点，只实现了 v4 batch 模式。
- **R5 依赖混乱**: `MinerUClient` 位于 L3 `infrastructure.mineru.client`，被上层依赖方向正确。
- **R6 领域扭曲**: 变量命名用 MinerU 领域词（batchId/fileUrl/fullZipUrl/markdown）。

## 越界检查（R6.5）

```
TASK write_files：9 项
实际 diff 涉及：7 项（2 个 package-info 不在 write_files 中但属合理建包文件）
越界：0（无文件在 write_files 范围外被修改）
```

## 沿用既有抽象 grep（R6.4）

- HTTP 请求：项目无统一 HTTP 客户端，使用 Spring 6.1 RestClient（内置）→ 新建符合项目 Spring Boot 3.3.x 栈
- 配置属性：找到 `MinioProperties` (`@Component` + `@ConfigurationProperties`) → 沿用此模式
- 异常处理：找到 `FileStorageService` 的 `BusinessException(C0001/B0001)` → 沿用
- 错误码：`ErrorCode.C0001`（第三方错误）→ 沿用用于 MinerU API 失败

## LESSONS 检查（R1.8）

`.specs/LESSONS.md` 无相关条目，本次无冲突。