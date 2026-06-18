# TASK: 搭建可运行的项目框架

- **Change ID**: `init-platform`
- **关联**: `@.specs/init-platform/REQUIREMENT.md`、`@.specs/init-platform/DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P], T03[P], T04[P], T05[P]
Wave 2:            T06                     (depends on T02, T03)
Wave 3 (parallel): T07[P], T08[P], T09[P]  (depends on Wave 1 for dirs)
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="done">
  <name>创建四层包目录结构及 package-info.java</name>
  <read_files>
    docs/项目规范.md
    .specs/CONTEXT.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/gateway/package-info.java
    src/main/java/com/graphnexus/api/document/package-info.java
    src/main/java/com/graphnexus/api/graph/package-info.java
    src/main/java/com/graphnexus/api/analysis/package-info.java
    src/main/java/com/graphnexus/api/query/package-info.java
    src/main/java/com/graphnexus/api/basic/package-info.java
    src/main/java/com/graphnexus/application/document/package-info.java
    src/main/java/com/graphnexus/application/graph/package-info.java
    src/main/java/com/graphnexus/application/analysis/package-info.java
    src/main/java/com/graphnexus/application/query/package-info.java
    src/main/java/com/graphnexus/application/basic/package-info.java
    src/main/java/com/graphnexus/application/llmgateway/package-info.java
    src/main/java/com/graphnexus/infrastructure/neo4j/package-info.java
    src/main/java/com/graphnexus/infrastructure/mysql/package-info.java
    src/main/java/com/graphnexus/infrastructure/redis/package-info.java
    src/main/java/com/graphnexus/infrastructure/mq/package-info.java
    src/main/java/com/graphnexus/infrastructure/storage/package-info.java
    src/main/java/com/graphnexus/infrastructure/llm/package-info.java
    src/main/java/com/graphnexus/common/config/package-info.java
    src/main/java/com/graphnexus/common/exception/package-info.java
    src/main/java/com/graphnexus/common/logging/package-info.java
    src/main/java/com/graphnexus/common/monitoring/package-info.java
    src/test/java/com/graphnexus/architecture/package-info.java
  </write_files>
  <action>
    按 docs/项目规范.md §1.4.2 定义的完整包树，创建所有目录及 package-info.java。
    每个 package-info.java 包含：① 包级 Javadoc（一句话描述该层/模块职责）；
    ② @NonNullApi 注解（来自 Spring 的 org.springframework.lang.NonNullApi），确保该包默认非空。
    目录树须覆盖 L1(api)、L2(application)、L3(infrastructure)、common 四层共 23 个包，
    以及 src/test 下的 architecture 测试包。
  </action>
  <verify>find src/main/java/com/graphnexus -type d | sort | diff - <(cat docs/项目规范.md | grep "├──" | ... ) 或直接人工对照 docs/项目规范.md §1.4.2 包目录树</verify>
  <done>23 个包目录均存在，每个含 package-info.java，目录树与 docs/项目规范.md §1.4.2 完整一致</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="done">
  <name>创建 ErrorCode 枚举 + BusinessException 异常类</name>
  <read_files>
    .specs/CONTEXT.md
    .specs/init-platform/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    src/main/java/com/graphnexus/common/exception/BusinessException.java
  </write_files>
  <action>
    1. ErrorCode 枚举：按 CONTEXT「错误码」术语定义实现 5 位编码规则。
       - A——用户端错误：A0001=资源不存在, A0002=参数校验失败, A0003=无权限访问
       - B——系统错误：B0001=系统内部错误, B0002=服务暂不可用
       - C——第三方错误：C0001=外部服务调用失败
       每个枚举值含 errorCode(String) + httpStatus(int) + userTip 默认值(String)。
    2. BusinessException extends RuntimeException：
       - 字段：errorCode(ErrorCode)、errorMessage(String)、userTip(String)
       - 三个构造方法：(ErrorCode), (ErrorCode, errorMessage), (ErrorCode, errorMessage, userTip)
       - userTip 缺省时取 ErrorCode 枚举中的默认值
  </action>
  <verify>mvn test -Dtest="*ErrorCode*,*BusinessException*" 2>/dev/null; mvn compile</verify>
  <done>ErrorCode 含 A/B/C 三类共 6 个值；BusinessException 编译通过，三个构造方法均可正常使用</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="done">
  <name>创建 ErrorResponse 错误响应体 record</name>
  <read_files>
    docs/项目规范.md
    .specs/init-platform/REQUIREMENT.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/exception/ErrorResponse.java
  </write_files>
  <action>
    创建 ErrorResponse record（Java 17 record，不可变）：
    - 字段：errorCode(String)、errorMessage(String)、userTip(String)、traceId(String)、timestamp(LocalDateTime)
    - 静态工厂：of(BusinessException ex, String traceId) —— 从异常提取字段
    - 静态工厂：of(String errorCode, String errorMessage, String traceId) —— 兜底场景
    - timestamp 缺省为 LocalDateTime.now()
    遵循 docs/项目规范.md §2.4.1 的设计。
  </action>
  <verify>mvn compile</verify>
  <done>ErrorResponse record 编译通过，两个 of() 静态工厂方法可用，5 个字段齐全</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="done">
  <name>创建 ApiResponse + PageResult 统一响应体 record</name>
  <read_files>
    docs/项目规范.md
    .specs/init-platform/REQUIREMENT.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/ApiResponse.java
    src/main/java/com/graphnexus/common/PageResult.java
  </write_files>
  <action>
    1. ApiResponse<T> record：
       - 字段：code(int)、message(String)、data(T)、traceId(String)、timestamp(long)
       - 静态工厂：success(T data) → code=200, message="success"
       - 静态工厂：error(ErrorResponse error) → code 取 errorCode 映射的 httpStatus, message 取 errorMessage
       - error() 方法从 ErrorCode 枚举的 httpStatus 推导 code 值（B0001 → 500, A0002 → 400, etc.）
    2. PageResult<T> record：
       - 字段：list(List<T>)、total(long)、pageNum(int)、pageSize(int)
       - 静态工厂：of(Page<T> page) —— 从 Spring Data Page 对象提取
       - 静态工厂：empty(int pageNum, int pageSize) —— 空结果
    遵循 docs/项目规范.md §2.4.3 和 §2.4.4 的设计。
  </action>
  <verify>mvn compile</verify>
  <done>ApiResponse 和 PageResult 编译通过，ApiResponse.success/error、PageResult.of/empty 均可调用</done>
  <depends_on></depends_on>
</task>

<task id="T05" parallel="true" status="done">
  <name>创建 TraceIdFilter 全链路追踪过滤器</name>
  <read_files>
    docs/项目规范.md
    src/main/resources/logback-spring.xml
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/logging/TraceIdFilter.java
  </write_files>
  <action>
    创建 TraceIdFilter extends OncePerRequestFilter：
    - 读取请求头 X-Trace-Id，存在则沿用，不存在则 UUID.randomUUID().toString()
    - 注入 MDC.put("traceId", traceId)
    - filterChain.doFilter() 后在 finally 中 MDC.remove("traceId")
    - 加 @Slf4j 注解，doFilterInternal 入口打一条 DEBUG 日志（含 method + uri + traceId）
    遵循 docs/项目规范.md §2.4.2 的设计。
    确认 logback-spring.xml 中已有 %X{traceId} 占位符（dev 的 CONSOLE_PLAIN pattern）和 LogstashEncoder 的 <includeMdcKeyName>traceId</includeMdcKeyName>（非 dev），无需修改 logback 配置。
  </action>
  <verify>mvn compile</verify>
  <done>TraceIdFilter 编译通过，自动注册为 Spring Bean（@Component），MDC traceId 在请求生命周期内可用</done>
  <depends_on></depends_on>
</task>

<task id="T06" status="done">
  <name>创建 GlobalExceptionHandler 全局异常处理器</name>
  <read_files>
    .specs/CONTEXT.md
    .specs/init-platform/DESIGN.md
    .specs/init-platform/REQUIREMENT.md
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    src/main/java/com/graphnexus/common/exception/BusinessException.java
    src/main/java/com/graphnexus/common/exception/ErrorResponse.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java
  </write_files>
  <action>
    创建 GlobalExceptionHandler（@RestControllerAdvice）：
    处理 4 种异常类型，按 DESIGN.md §2.2 的异常处理流：
    1. BusinessException → 调用 ErrorResponse.of(ex, MDC.get("traceId"))
       HTTP 状态码按 ErrorCode.httpStatus 映射：
       - A0001 → 404, A0002 → 400, A0003 → 403
       - B0001 → 500, B0002 → 503
       - C0001 → 502
    2. MethodArgumentNotValidException → 400，拼接字段校验失败信息为 userTip
    3. AccessDeniedException → 403，返回 A0003
    4. Exception（兜底）→ 500，返回 B0001，隐藏内部 detailMessage
    遵循 CONTEXT「分层异常传递」规则：L1 禁止向上抛，此 handler 是最后防线。
  </action>
  <verify>mvn compile</verify>
  <done>GlobalExceptionHandler 编译通过，4 个 @ExceptionHandler 方法覆盖全部异常路径</done>
  <depends_on>T02, T03</depends_on>
</task>

<task id="T07" parallel="true" status="done">
  <name>调整 GraphNexusApplication 启动类：无基础设施可启动</name>
  <read_files>
    src/main/java/com/graphnexus/GraphNexusApplication.java
    .specs/init-platform/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/GraphNexusApplication.java
  </write_files>
  <action>
    修改 GraphNexusApplication 的 @SpringBootApplication exclude 列表：
    - 保留已有的 OpenAiAutoConfiguration.class
    - 新增排除：Neo4jAutoConfiguration、Neo4jDataAutoConfiguration（需验证类名存在性）
    - 新增排除：HibernateJpaAutoConfiguration、JpaRepositoriesAutoConfiguration（需验证）
    - 新增排除：RedisAutoConfiguration、RedisRepositoriesAutoConfiguration（需验证）
    - 新增排除：RabbitAutoConfiguration
    - 新增排除：Neo4jHealthContributorAutoConfiguration
    由于多个 exclude 已很多（~10 个），可以考虑用 @SpringBootApplication + spring.autoconfigure.exclude 混合方式，
    或者在 application.yml 中集中声明排除列表（见 T08）。
    最终目标：mvn spring-boot:run 在无容器时正常启动，/actuator/health 返回 UP。
  </action>
  <verify>mvn spring-boot:run 启动，curl -s http://localhost:8080/actuator/health 返回 {"status":"UP"}；启动日志无 "Connection refused" 或 "Failed to connect" 错误</verify>
  <done>应用在无 Neo4j/MySQL/Redis/RabbitMQ 环境下成功启动，health 返回 UP</done>
  <depends_on></depends_on>
</task>

<task id="T08" parallel="true" status="done">
  <name>调整 application.yml / application-dev.yml：基础设施惰性连接</name>
  <read_files>
    src/main/resources/application.yml
    src/main/resources/application-dev.yml
    .specs/init-platform/DESIGN.md
  </read_files>
  <write_files>
    src/main/resources/application.yml
    src/main/resources/application-dev.yml
  </write_files>
  <action>
    1. application.yml：
       - 确认 management.health.neo4j/db/redis/rabbit.enabled 已设为 false ✅（已存在）
       - 新增 spring.autoconfigure.exclude 列表（和 T07 协调策略——如果 T07 用代码 exclude，这里就不加；若这里用 yml exclude，T07 就只保留 OpenAi）
       - 根据实际验证结果选最优方案：推荐 yml exclude 方式（更灵活，无需改代码）
    2. application-dev.yml：
       - 确认基础设施连接信息已配置但不会在 exclude 模式下触发
       - 无需修改（已在之前会话中写好，带占位值）
    参见 DESIGN.md D1 决策。
  </action>
  <verify>Spring Boot 启动日志中不出现 "Configuring Neo4j" / "Configuring JPA" / "Configuring Redis" / "Configuring RabbitMQ" 等自动配置日志</verify>
  <done>yml 配置与 T07 启动类配合，应用无基础设施启动成功</done>
  <depends_on></depends_on>
</task>

<task id="T09" parallel="true" status="done">
  <name>创建 ArchUnit 分层架构约束测试</name>
  <read_files>
    docs/项目规范.md
    .specs/init-platform/DESIGN.md
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/architecture/LayeredArchitectureTest.java
  </write_files>
  <action>
    创建 ArchUnit 测试类 LayeredArchitectureTest：
    1. 定义四层：L1(api..)、L2(application..)、L3(infrastructure..)、Common(common..)
    2. 测试规则：
       - L1 只应依赖 L2 + Common + Java stdlib + Spring Framework（不依赖 L3）
       - L2 只应依赖 L3 + Common + Java stdlib + Spring（不依赖 L1）
       - L3 只应依赖 Common + Java stdlib + Spring + 第三方 lib（不依赖 L1/L2）
       - Common 不依赖 L1/L2/L3（只依赖 Java stdlib + 第三方 lib）
    3. 使用 layeredArchitecture() API 或自定义 import 规则
    4. 因为本次无业务类，测试针对包 rule 校验而非具体类——当包内还没有类时，可先用空规则占位，标注 @Disabled 并加 TODO 注释："待业务类创建后启用"
    5. 至少保留一条可运行的规则（如 common 不依赖 api 层——即使包为空，ArchUnit 包级规则也能跑）
    参见 DESIGN.md D6 决策。
  </action>
  <verify>mvn test -Dtest="LayeredArchitectureTest" 通过</verify>
  <done>ArchUnit 测试编译通过，至少一条分层约束规则可运行并 PASS</done>
  <depends_on></depends_on>
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