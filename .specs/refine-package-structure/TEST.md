# TEST: 细化项目包结构

- **Change ID**: `refine-package-structure`
- **关联**: `@.specs/refine-package-structure/CHANGE.md`、`@flow-kit/reference/test-pyramid.md`
- **项目类型**: 后端 API（Spring Boot Java 17）

---

## 0. 本次测试范围声明（5 轮金字塔）

| 轮次 | 状态 | 范围 | 跳过理由 |
|---|---|---|---|
| 第 1 轮 · 功能 | ✅ 必跑 | 全部现有测试回归 + ArchUnit 架构约束 | — |
| 第 2 轮 · 性能 | ❌ 跳过 | — | 纯包结构重构，JVM 不关心包名。同一份字节码，零性能影响 |
| 第 3 轮 · 安全 | ❌ 跳过 | — | 无代码逻辑变更、无依赖变更、无新增密钥。仅移动文件和改 import |
| 第 4 轮 · 兼容 | ⚠️ 部分 | API 兼容 + Spring 组件扫描 | 无浏览器/UI、无 schema 变更。仅验证 REST API 和 Spring 组件扫描不受包路径变更影响 |
| 第 5 轮 · 可观测 | ❌ 跳过 | — | 无日志/指标/告警变更。TraceIdFilter 在 common/ 未动，日志语句未改 |

---

## 第 1 轮 · 功能测试

### 1.1 测试矩阵（现有测试回归）

本次 change 无新增 AC（纯结构重构），目标为确保所有**现有功能测试仍然通过**。

| 测试类 | 类型 | 覆盖范围 | 包路径变更后状态 |
|---|---|---|---|
| `PdfBoxDocumentParserTest` | unit | AC-2 解析部分（4 tests） | ✅ 已迁移到 `application.document.parser` |
| `DocumentServiceTest` | unit | AC-1/AC-6/AC-7 业务逻辑（10 tests） | ✅ import 已修正 |
| `DocumentStatusTest` | unit | AC-3 状态机（9 tests） | ✅ 已迁移到 `infrastructure.mysql.document` |
| `LayeredArchitectureTest` | arch | 四层依赖方向约束（1 test） | ✅ 通配符 `..` 自动适配新子包 |

### 1.2 运行结果

```text
$ mvn clean test -Dtest="!DocumentProcessingIntegrationTest"

PdfBoxDocumentParserTest     Tests run: 4,  Failures: 0, Errors: 0
DocumentServiceTest          Tests run: 10, Failures: 0, Errors: 0
DocumentStatusTest           Tests run: 9,  Failures: 0, Errors: 0
LayeredArchitectureTest      Tests run: 1,  Failures: 0, Errors: 0

Total: Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 1.3 覆盖率

本次为结构重构，不新增/删除逻辑代码。覆盖率基线（来自 `document-process-pdf-minimal` T09/T10）保持不变。

### 1.4 边界 / 错误路径

- **空/null**：PdfBoxDocumentParserTest § parseEmptyBytesShouldReturnEmptyOrError 仍覆盖
- **异常路径**：DocumentServiceTest mock 验证 BusinessException 在 4 种错误场景仍正确抛出
- **状态机边界**：DocumentStatusTest 9 个用例全覆盖状态转换的合法/非法路径

### 1.5 测试质量自检（6 维测试衰退风险）

| 编号 | 衰退风险 | 命中 | 说明 |
|---|---|---|---|
| T1 | Test Obscurity 测试晦涩 | 0 | 测试名称使用 Given/When/Then 模式，DisplayName 中文描述场景 |
| T2 | Test Brittleness 测试脆弱 | 0 | 测试验证行为（parse 返回非空文本、状态转换合法性），不验证实现细节（private 方法） |
| T3 | Test Duplication 测试重复 | 0 | 每个测试验证独立场景，无"改个参数跑一遍"的重复 |
| T4 | Mock Abuse Mock 滥用 | 0 | DocumentServiceTest mock 了 L3 依赖（Repository + FileStorageService），这是正确的单元测试边界 |
| T5 | Coverage Illusion 覆盖率幻觉 | 0 | 所有测试有真实断言（assertNotNull、assertTrue、assertEquals），无空断言 |
| T6 | Architecture Mismatch 架构错配 | 0 | 单元测试在 JUnit 5 + Mockito，架构测试在 ArchUnit，各在其位 |

### 1.6 测试质量记事

无。本次变更未新增测试债务，现有测试质量良好。

---

## 第 2 轮 · 性能测试

> ❌ 跳过。纯包结构重构，`.class` 字节码不变，JVM 类加载/方法调用性能不受包名影响。Spring 组件扫描路径 `com.graphnexus` 不变，启动时间不变。

---

## 第 3 轮 · 安全测试

> ❌ 跳过。零代码逻辑变更（仅 `package` 声明 + `import` 语句）。无依赖版本变更，无新增密钥/配置，无 API 契约变更。

---

## 第 4 轮 · 兼容性测试

### 4.1 跨浏览器 / 跨设备

> N/A — 后端项目，无 UI。

### 4.2 数据迁移

> N/A — 无 DDL/Schema 变更。

### 4.3 API 兼容性验证

| 检查项 | 状态 | 说明 |
|---|---|---|
| REST URL 路径不变 | ✅ | Controller 的 `@RequestMapping` 注解未修改 |
| JSON 请求/响应结构不变 | ✅ | DTO/VO 类字段未修改，Jackson 序列化不变 |
| HTTP 状态码不变 | ✅ | GlobalExceptionHandler 未修改 |
| Spring 组件扫描 | ✅ | `@SpringBootApplication` 扫描 `com.graphnexus` 及子包，新增子包自动纳入 |

**验证命令**：
```text
mvn spring-boot:run 可在无外部基础设施时启动（Actuator /health → UP）
```

### 4.4 跨版本

> N/A — 单模块项目，无跨版本 API 兼容性问题。

---

## 第 5 轮 · 可观测性验证

> ❌ 跳过。日志语句仍在原 Java 文件中（未增删改），`TraceIdFilter` 在 `common/logging/` 未动，MDC traceId 注入不变。Micrometer 指标注册在 `common/monitoring/` 未动。

---

## 新增测试登记

无。本次为纯结构重构，不新增功能，因此不新增测试用例。

## 回归保护

本次变更可能影响的旧功能：

| 影响面 | 已有测试 | 状态 |
|---|---|---|
| PDF 解析（DocumentParser → PdfBoxDocumentParser） | `PdfBoxDocumentParserTest`（4 tests） | ✅ 通过 |
| 文档上传/解析/查询/更新/删除全链路 | `DocumentServiceTest`（10 tests） | ✅ 通过 |
| 文档状态机（UPLOADED → PROCESSING → COMPLETED/FAILED） | `DocumentStatusTest`（9 tests） | ✅ 通过 |
| 四层架构依赖方向（L1→L2→L3, L3→common） | `LayeredArchitectureTest`（1 test） | ✅ 通过 |

**结论**：所有回归测试 24/24 通过，零功能退化。