# TASK — 系统运维功能（健康检测 + 日志查看）

- **Change ID**: `ops-health-log`
- **关联**: `@.specs/ops-health-log/REQUIREMENT.md`、`@.specs/ops-health-log/DESIGN.md`、`@.specs/ops-health-log/UI-DESIGN.md`

---

## 波次划分

```
Wave 1 (backend foundation · parallel):   T01[P], T02[P], T03[P], T04[P], T05[P]
Wave 2 (backend services · parallel):     T06[P], T07[P]           (depends on T01,T02,T03)
Wave 3 (backend controllers · parallel):  T08[P], T09[P]           (depends on T06,T07)
Wave 4 (backend verify):                  T10                      (depends on T08,T09)
Wave 5 (frontend foundation · parallel):  T11[P], T12[P]           (独立于后端)
Wave 6 (frontend pages · parallel):       T13[P], T14[P], T15[P]   (depends on T11,T12)
Wave 7 (frontend verify):                 T16                      (depends on T13,T14,T15)
```

> Wave 1–4（后端）与 Wave 5–7（前端）可并行执行（不同目录、无文件冲突）。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="pending">
  <name>RedisHealthIndicator 新增</name>
  <read_files>
    src/main/java/com/graphnexus/common/config/RedisConfig.java
    src/main/java/com/graphnexus/application/system/health/*
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/system/health/RedisHealthIndicator.java
  </write_files>
  <action>
    新建 RedisHealthIndicator，实现 org.springframework.boot.actuate.health.HealthIndicator 接口。
    注入 RedisConnectionFactory，health() 方法中：
    1. 记录 startTime = System.currentTimeMillis()
    2. 调用 connectionFactory.getConnection().ping()
    3. 计算 latency = System.currentTimeMillis() - startTime
    4. 返回 Health.up().withDetail("latency", latency).build()
    5. catch 异常返回 Health.down().withDetail("error", e.getMessage()).build()
    遵循项目构造器注入规范（@RequiredArgsConstructor + private final）。
  </action>
  <verify>mvn compile -pl . -q 2>&1 | grep -E "BUILD|ERROR"</verify>
  <done>编译通过；RedisHealthIndicator 被 Spring 自动发现为 HealthIndicator bean</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>MinIOHealthIndicator 新增</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/storage/config/MinioConfig.java
    src/main/java/com/graphnexus/infrastructure/storage/config/MinioProperties.java
    src/main/java/com/graphnexus/application/system/health/*
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/system/health/MinIOHealthIndicator.java
  </write_files>
  <action>
    新建 MinIOHealthIndicator，实现 HealthIndicator 接口。
    注入 MinioClient + MinioProperties，health() 方法中：
    1. 记录 startTime
    2. 调用 minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build())
    3. 计算 latency
    4. 返回 Health.up().withDetail("latency", latency).build()
    5. catch 异常返回 Health.down().withDetail("error", e.getMessage()).build()
    遵循项目构造器注入规范。
  </action>
  <verify>mvn compile -pl . -q 2>&1 | grep -E "BUILD|ERROR"</verify>
  <done>编译通过；MinIOHealthIndicator 被 Spring 自动发现为 HealthIndicator bean</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>ErrorCode 新增 A0023（文件名非法）</name>
  <read_files>
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
  </write_files>
  <action>
    在 ErrorCode 枚举中新增 A0023("文件名非法")。
    参数：A0023("FILE_NAME_ILLEGAL", "文件名包含非法字符", "请提供合法的文件名")。
    遵循既有枚举定义风格（来源 A=用户端，4 位数字，按序号递增）。
  </action>
  <verify>grep "A0023" src/main/java/com/graphnexus/common/exception/ErrorCode.java</verify>
  <done>A0023 枚举值已定义，可被 LogService 引用</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="pending">
  <name>启用 MySQL 健康检查</name>
  <read_files>
    src/main/resources/application.yml
  </read_files>
  <write_files>
    src/main/resources/application.yml
  </write_files>
  <action>
    修改 application.yml：
    management.health.db.enabled: false → true
    仅改一行，不动其他配置。
  </action>
  <verify>grep -A2 "health:" src/main/resources/application.yml | grep "db:"; grep "enabled: true" src/main/resources/application.yml</verify>
  <done>management.health.db.enabled=true；/actuator/health 响应中出现 db 组件</done>
  <depends_on></depends_on>
</task>

<task id="T05" parallel="true" status="pending">
  <name>SecurityConfig 运维端点权限收敛</name>
  <read_files>
    src/main/java/com/graphnexus/common/config/SecurityConfig.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/config/SecurityConfig.java
  </write_files>
  <action>
    修改 SecurityConfig.securityFilterChain() 的 authorizeHttpRequests 块：
    1. /actuator/** 从 permitAll() 改为 hasAnyRole("ADMIN", "OPS_MANAGER", "OPS_STAFF")
    2. 在 /api/v1/** 之前新增 .requestMatchers("/api/v1/system/**").hasAnyRole("ADMIN", "OPS_MANAGER", "OPS_STAFF")
    顺序必须：actuator → system → api/v1/** → anyRequest。
    见 DESIGN D6（规则顺序敏感）。
  </action>
  <verify>grep -n "actuator\|system\|api/v1" src/main/java/com/graphnexus/common/config/SecurityConfig.java</verify>
  <done>SecurityFilterChain 中 /actuator/** 和 /api/v1/system/** 均限制为运维三角色；/api/v1/system/** 在 /api/v1/** 之前</done>
  <depends_on></depends_on>
</task>

<task id="T06" parallel="true" status="pending">
  <name>SystemHealthService + SystemHealthVO 新增</name>
  <read_files>
    src/main/java/com/graphnexus/application/system/health/RedisHealthIndicator.java
    src/main/java/com/graphnexus/application/system/health/MinIOHealthIndicator.java
    src/main/java/com/graphnexus/common/ApiResult.java
    src/main/java/com/graphnexus/api/system/dto/*
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/system/service/SystemHealthService.java
    src/main/java/com/graphnexus/application/system/service/impl/SystemHealthServiceImpl.java
    src/main/java/com/graphnexus/api/system/dto/SystemHealthVO.java
  </write_files>
  <action>
    1. 新建 SystemHealthVO（api/system/dto/）：
       - List&lt;ComponentHealth&gt; components（ComponentHealth 为内部静态类：name/status/latency/error）
       - JvmMetrics jvm（内部静态类：heapUsed/heapMax/cpuUsage/threadCount/gcCount）
    2. 新建 SystemHealthService 接口 + SystemHealthServiceImpl：
       - 注入 List&lt;HealthIndicator&gt;（Spring 自动收集所有实现）
       - 注入 MetricsEndpoint（Spring Boot Actuator bean，见 DESIGN D7）
       - getSystemHealth()：遍历 HealthIndicator → 每个调 health() → 组装 ComponentHealth 列表
       - getJvmMetrics()：调 metricsEndpoint.metric("jvm.memory.used", null) 等 5 个指标
       - 每个 HealthIndicator 调用 try-catch 包裹，异常不中断整体（见 ADR-047）
  </action>
  <verify>mvn compile -pl . -q 2>&1 | grep -E "BUILD|ERROR"</verify>
  <done>编译通过；SystemHealthService 可聚合所有 HealthIndicator + JVM MetricsEndpoint 数据</done>
  <depends_on>T01, T02, T03</depends_on>
</task>

<task id="T07" parallel="true" status="pending">
  <name>LogService + LogFileVO + LogContentVO 新增</name>
  <read_files>
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    src/main/java/com/graphnexus/common/ApiResult.java
    src/main/java/com/graphnexus/api/system/dto/*
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/system/service/LogService.java
    src/main/java/com/graphnexus/application/system/service/impl/LogServiceImpl.java
    src/main/java/com/graphnexus/api/system/dto/LogFileVO.java
    src/main/java/com/graphnexus/api/system/dto/LogContentVO.java
  </write_files>
  <action>
    1. 新建 LogFileVO：fileName / fileSize (long) / fileSizeFormatted (String, 人类可读) / lastModified (String, yyyy-MM-dd HH:mm:ss)
    2. 新建 LogContentVO：fileName / lines (List&lt;String&gt;) / currentPage / totalPages / totalLines / pageSize
    3. 新建 LogService 接口：listFiles() / readContent(fileName, page, size) / getLogDir()
    4. 新建 LogServiceImpl：
       - @Value("${logging.file.dir:logs}") 注入日志目录
       - listFiles()：Files.list(logDir) → filter regular files → 按 lastModified 倒序 → map to LogFileVO
       - readContent()：
         a. 路径校验（DESIGN D4 双重校验：字符黑名单拒绝 .. / / \ + canonical path 前缀检查）
         b. 文件名不含 .. 或 / 或 \ → 否则抛 BusinessException(A0023)
         c. resolve path → toRealPath() → 验证 startsWith(logDirRealPath)
         d. totalLines = Files.lines(path).count()
         e. lines = Files.lines(path).skip((page-1)*size).limit(size).collect(toList())
         f. 返回 LogContentVO
       - page 默认 1，size 默认 200，最大 500（防滥用，见 ADR-048）
    5. 遵循四层架构：Service 只依赖接口，不 import Controller 层类
  </action>
  <verify>mvn compile -pl . -q 2>&1 | grep -E "BUILD|ERROR"</verify>
  <done>编译通过；LogService 可扫描 logs/ 目录、分页读取内容、防护路径遍历</done>
  <depends_on>T03</depends_on>
</task>

<task id="T08" parallel="true" status="pending">
  <name>SystemHealthController 新增</name>
  <read_files>
    src/main/java/com/graphnexus/application/system/service/SystemHealthService.java
    src/main/java/com/graphnexus/api/system/dto/SystemHealthVO.java
    src/main/java/com/graphnexus/common/ApiResult.java
    src/main/java/com/graphnexus/common/config/SecurityConfig.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/system/controller/SystemHealthController.java
  </write_files>
  <action>
    新建 SystemHealthController（api/system/controller/）：
    - @RestController + @RequestMapping("/api/v1/system")
    - 类级别 @PreAuthorize("hasAnyRole('ADMIN','OPS_MANAGER','OPS_STAFF')")（第二层防御，SecurityFilterChain 为第一层）
    - 注入 SystemHealthService
    - GET /api/v1/system/health → 调用 service.getSystemHealth() + service.getJvmMetrics() → 组装 SystemHealthVO → ApiResult.success(vo)
    - 遵循项目 Controller 规范：返回 ApiResult<T>，异常由 GlobalExceptionHandler 处理
  </action>
  <verify>mvn compile -pl . -q 2>&1 | grep -E "BUILD|ERROR"</verify>
  <done>编译通过；GET /api/v1/system/health 端点就绪</done>
  <depends_on>T06</depends_on>
</task>

<task id="T09" parallel="true" status="pending">
  <name>LogController 新增</name>
  <read_files>
    src/main/java/com/graphnexus/application/system/service/LogService.java
    src/main/java/com/graphnexus/api/system/dto/LogFileVO.java
    src/main/java/com/graphnexus/api/system/dto/LogContentVO.java
    src/main/java/com/graphnexus/common/ApiResult.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/system/controller/LogController.java
  </write_files>
  <action>
    新建 LogController（api/system/controller/）：
    - @RestController + @RequestMapping("/api/v1/system")
    - 类级别 @PreAuthorize("hasAnyRole('ADMIN','OPS_MANAGER','OPS_STAFF')")
    - 注入 LogService
    - GET /api/v1/system/logs → 返回 ApiResult.success(service.listFiles())
    - GET /api/v1/system/logs/{filename}?page=&size= → @RequestParam 默认 page=1, size=200 → 校验 size ≤ 500 → 返回 ApiResult.success(service.readContent(filename, page, size))
    - GET /api/v1/system/logs/{filename}/download → StreamingResponseBody + Content-Disposition: attachment; filename="..." → Content-Type: application/octet-stream
    - 遵循项目规范：返回 ApiResult<T>，异常由 GlobalExceptionHandler 处理
    - 下载端点复用项目首个文件下载模式（见 CONTEXT 「文件下载响应模式」— diagnosis-history-export）
  </action>
  <verify>mvn compile -pl . -q 2>&1 | grep -E "BUILD|ERROR"</verify>
  <done>编译通过；三个日志端点（列表/分页内容/下载）就绪</done>
  <depends_on>T07</depends_on>
</task>

<task id="T10" parallel="false" status="pending">
  <name>后端全量编译 + 启动验证</name>
  <read_files>
    src/main/java/com/graphnexus/**
    src/main/resources/**
  </read_files>
  <write_files>
    <!-- 仅验证，不写文件 -->
  </write_files>
  <action>
    全量 mvn compile 验证所有后端文件编译通过、Bean 注入正确、Spring Boot 启动无异常。
    1. mvn compile -pl .（确保 0 错误）
    2. 启动应用（mvn spring-boot:run 或 IDE 启动），检查：
       - 启动日志无异常
       - /actuator/health 返回 200（含 db/neo4j/redis/minIO 组件，ping 正常时为 UP）
       - curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/system/health 返回四组件 + JVM 数据
       - curl http://localhost:8080/api/v1/system/logs 返回文件列表
    3. 权限验证：无 Token 访问 /api/v1/system/health → 401；TEACHER Token → 403
  </action>
  <verify>mvn compile -pl . -q 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>mvn compile BUILD SUCCESS；应用启动正常；健康 API 返回组件状态；日志 API 返回文件列表；权限校验正确（AC-6 覆盖）</done>
  <depends_on>T08, T09</depends_on>
</task>

<task id="T11" parallel="true" status="pending">
  <name>前端 API 模块 system.ts 新增</name>
  <read_files>
    frontend/src/api/config.ts
    frontend/src/api/types.ts
  </read_files>
  <write_files>
    frontend/src/api/system.ts
  </write_files>
  <action>
    新建 frontend/src/api/system.ts，封装运维 API 调用：
    - getSystemHealth(): Promise&lt;SystemHealthVO&gt; — GET /api/v1/system/health
    - getLogFiles(): Promise&lt;LogFileVO[]&gt; — GET /api/v1/system/logs
    - getLogContent(filename: string, page?: number, size?: number): Promise&lt;LogContentVO&gt; — GET /api/v1/system/logs/{filename}
    - downloadLogFile(filename: string): void — 触发浏览器下载（window.open 或 <a> 标签点击，不经过 axios）
    复用项目既有 axios 实例（import client from './client' 或匹配既有 API 模块的 import 方式）。
    类型定义（SystemHealthVO / LogFileVO / LogContentVO 接口）写在 system.ts 文件内部，不修改共享 types.ts（避免并行冲突）。
  </action>
  <verify>npx vue-tsc --noEmit 2>&1 | head -5</verify>
  <done>vue-tsc 无新增类型错误；system.ts 可被其他模块 import</done>
  <depends_on></depends_on>
</task>

<task id="T12" parallel="true" status="pending">
  <name>前端 systemStore.ts Pinia store 新增</name>
  <read_files>
    frontend/src/stores/authStore.ts
    frontend/src/api/system.ts
  </read_files>
  <write_files>
    frontend/src/stores/systemStore.ts
  </write_files>
  <action>
    新建 frontend/src/stores/systemStore.ts，Pinia Options API 风格：
    - state: { health: SystemHealthVO | null, logFiles: LogFileVO[], currentLog: LogContentVO | null, loading: boolean, lastRefreshTime: string | null }
    - actions:
      - fetchHealth() → 调 systemApi.getSystemHealth() → 更新 health + lastRefreshTime
      - fetchLogFiles() → 调 systemApi.getLogFiles() → 更新 logFiles
      - fetchLogContent(filename, page, size) → 调 systemApi.getLogContent() → 更新 currentLog
      - downloadLog(filename) → 调 systemApi.downloadLogFile()
    遵循 authStore.ts 的 Pinia 写法风格。
  </action>
  <verify>npx vue-tsc --noEmit 2>&1 | head -5</verify>
  <done>vue-tsc 无新增类型错误；systemStore 可被页面组件使用</done>
  <depends_on>T11</depends_on>
</task>

<task id="T13" parallel="true" status="pending">
  <name>SystemHealthPage.vue 健康面板页面</name>
  <read_files>
    frontend/src/stores/systemStore.ts
    frontend/src/api/system.ts
    frontend/src/common/components/BaseCard.vue
    frontend/src/assets/tokens.css
    frontend/src/assets/global.css
    frontend/src/views/settings/SettingsConfigPage.vue
  </read_files>
  <write_files>
    frontend/src/views/system/SystemHealthPage.vue
  </write_files>
  <action>
    新建 SystemHealthPage.vue，路由 /system/health。
    布局（见 UI-DESIGN v0）：
    1. PageHeader：headline "系统健康" + supporting "最后刷新: HH:mm:ss"（from systemStore.lastRefreshTime）+ n-button "刷新"（secondary, size small, @click 触发 fetchHealth()）
    2. 四组件状态卡片行（flex row, gap=spacing-lg, flex-wrap）：
       - 每个 = n-card（复用 BaseCard 风格：surface bg + 1px border + rounded-md + padding lg）
       - 内容：micro-label 组件名 + n-tag（UP=success / DOWN=error）+ mono 延迟 ms 或 supporting 错误原因
    3. JVM 指标面板：n-card（同上风格），title "JVM 运行时指标"
       - n-progress 堆内存（percentage, brand 色, rail-color=border）
       - 4 个指标：CPU / 线程 / GC / 堆最大，2×2 grid，标签=micro-label，数值=mono
    4. onMounted 时调用 fetchHealth()，setInterval 30s 轮询，onUnmounted 清除定时器
    UI-DESIGN §6.2 组件状态卡片 + §6.3 JVM 指标面板。
    极简风格：无阴影 at rest，无渐变，无图表/环形图。
  </action>
  <verify>npx vue-tsc --noEmit 2>&1 | head -5</verify>
  <done>vue-tsc 无类型错误；页面渲染四组件卡片 + JVM 指标区 + 30s 自动轮询（AC-1, AC-2 覆盖）</done>
  <depends_on>T11, T12</depends_on>
</task>

<task id="T14" parallel="true" status="pending">
  <name>SystemLogPage.vue 日志查看页面</name>
  <read_files>
    frontend/src/stores/systemStore.ts
    frontend/src/api/system.ts
    frontend/src/common/components/BaseCard.vue
    frontend/src/assets/tokens.css
    frontend/src/assets/global.css
  </read_files>
  <write_files>
    frontend/src/views/system/SystemLogPage.vue
  </write_files>
  <action>
    新建 SystemLogPage.vue，路由 /system/logs。
    布局（见 UI-DESIGN v0）：
    1. PageHeader：headline "系统日志"
    2. 左右分栏（flex row, height: calc(100vh - 200px)）：
       左栏（280px 固定宽, border-right, overflow-y:auto）：
         - n-list hoverable，每项显示文件名(.body 14px) + 大小·时间(.supporting 12px, tertiary色)
         - 选中项高亮：background oklch(0.55 0.18 250 / 0.06)（brand veil）
         - .gz 文件淡化文件名
         - @click 触发 fetchLogContent(filename, 1, 200)
       右栏（flex-1, padding-left: lg）：
         - 空状态（无选中文件）：居中显示 "选择一个日志文件查看内容"(.supporting, tertiary色)
         - 有选中文件时：
           - 顶部：分页控件行（n-button secondary "上一页"/"下一页" + "第 X/Y 页" supporting）
           - n-button secondary "下载"（Download 图标, @click 触发 downloadLog）
           - <pre> 块：font-family: var(--font-mono), font-size: 13px, line-height:1.5, bg: var(--color-bg), padding: md, rounded: sm, overflow-x: auto, white-space: pre
    3. onMounted 调用 fetchLogFiles()
    UI-DESIGN §6.4 日志文件列表 + §6.5 日志内容区。
  </action>
  <verify>npx vue-tsc --noEmit 2>&1 | head -5</verify>
  <done>vue-tsc 无类型错误；页面渲染文件列表 + 分页内容 + 下载按钮（AC-3, AC-4, AC-5 覆盖）</done>
  <depends_on>T11, T12</depends_on>
</task>

<task id="T15" parallel="true" status="pending">
  <name>AppLayout 菜单 + router 路由 运维入口</name>
  <read_files>
    frontend/src/common/components/AppLayout.vue
    frontend/src/router/index.ts
    frontend/src/router/authGuard.ts
    frontend/src/stores/authStore.ts
  </read_files>
  <write_files>
    frontend/src/common/components/AppLayout.vue
    frontend/src/router/index.ts
  </write_files>
  <action>
    1. AppLayout.vue：
       - navItems 数组追加两项：
         { path: '/system/health', label: '系统健康', icon: Activity, roles: ['ADMIN', 'OPS_MANAGER', 'OPS_STAFF'] }
         { path: '/system/logs', label: '系统日志', icon: FileText, roles: ['ADMIN', 'OPS_MANAGER', 'OPS_STAFF'] }
       - 从 lucide-vue 新增 import Activity, FileText
       - visibleNavItems computed 自动按角色过滤（无需改逻辑）
    2. router/index.ts：
       - 新增路由 { path: '/system/health', name: 'system-health', component: () => import('@/views/system/SystemHealthPage.vue'), meta: { roles: ['ADMIN', 'OPS_MANAGER', 'OPS_STAFF'] } }
       - 新增路由 { path: '/system/logs', name: 'system-logs', component: () => import('@/views/system/SystemLogPage.vue'), meta: { roles: ['ADMIN', 'OPS_MANAGER', 'OPS_STAFF'] } }
       - meta.roles 由既有 authGuard.ts 自动校验（无需改守卫逻辑）
    UI-DESIGN §6.6 侧边栏新增菜单项。
  </action>
  <verify>grep "system/health\|system/logs" frontend/src/router/index.ts && grep "Activity\|FileText" frontend/src/common/components/AppLayout.vue</verify>
  <done>运维角色可见侧边栏「系统健康」「系统日志」菜单；路由受 authGuard meta.roles 保护（AC-7, AC-8 覆盖）</done>
  <depends_on></depends_on>
</task>

<task id="T16" parallel="false" status="pending">
  <name>前端全量类型检查 + 构建验证</name>
  <read_files>
    frontend/src/**
  </read_files>
  <write_files>
    <!-- 仅验证，不写文件 -->
  </write_files>
  <action>
    全量前端验证：
    1. npx vue-tsc --noEmit → 确保 0 类型错误
    2. npx vite build → 确保构建成功
    3. 启动前端 dev server（npm run dev），手动验证：
       - ADMIN 登录 → 侧边栏可见「系统运维」→ 点击「系统健康」→ 四组件卡片 + JVM 数据
       - 点击「系统日志」→ 文件列表 → 点击文件 → 分页内容 → 下载
       - TEACHER 登录 → 侧边栏不可见运维菜单 → 手动输入 /system/health → 403
  </action>
  <verify>npx vue-tsc --noEmit 2>&1 | tail -3; npx vite build 2>&1 | tail -5</verify>
  <done>vue-tsc 0 错误 + vite build 成功；手动验证 AC-1~AC-8 全部通过</done>
  <depends_on>T13, T14, T15</depends_on>
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
|------|---------|------------|------|
|  |  |  |  |

---

## Fix 任务（来自 REVIEW / INTEGRATION）

> 此区域由 review/integration 阶段自动追加，编号 `T-FIX-XX`。

```xml
<!-- 占位 -->
```