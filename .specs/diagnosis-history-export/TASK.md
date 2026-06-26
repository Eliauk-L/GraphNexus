# TASK: 学情诊断历史记录查询与导出

- **Change ID**: `diagnosis-history-export`
- **关联**: `@.specs/diagnosis-history-export/REQUIREMENT.md`、`@.specs/diagnosis-history-export/DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P] ErrorCode,  T02[P] Repository,  T03[P] DTOs/Interface
Wave 2:            T04 ServiceImpl + 单测  (depends on T01, T02, T03)
                   T05 Controller             (depends on T03 — 可写代码；验证依赖 T04)
Wave 3 (parallel): T06[P] API types,  T07[P] queryStore  (depends on Wave 2 完成)
Wave 4:            T08 HistoryPanel           (depends on T06, T07)
                   T09 集成 + 全量验证         (depends on T08)
```

| Wave | 任务 | 类型 | 说明 |
|------|------|------|------|
| 1 | T01, T02, T03 | 并行 | 后端基础：错误码 + Repository + DTOs/接口 |
| 2 | T04, T05 | 串行→并行 | 后端核心：Service 实现（T04）+ Controller（T05 写代码可与 T04 并行，验证等 T04） |
| 3 | T06, T07 | 并行 | 前端基础：API 函数 + Store 状态 |
| 4 | T08, T09 | 串行 | 前端 UI：组件 + 集成验证 |

---

## 任务清单

```xml
<task id="T01" parallel="true" status="pending">
  <name>ErrorCode A0023 新增 + 前端 FRIENDLY_TIPS 同步</name>
  <read_files>
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    frontend/src/api/client.ts
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    frontend/src/api/client.ts
  </write_files>
  <action>
    1. 在 ErrorCode.java 的 A 类错误码段（A0022 之后）新增：
       A0023("A0023", HttpStatus.BAD_REQUEST, "导出记录数超过上限（5000 条），请缩小筛选范围")
    2. 在 client.ts 的 FRIENDLY_TIPS 映射中追加 A0023 条目
    3. A0021 已存在（问答任务不存在），A0022 已存在（考试编号已存在），确认 A0023 不冲突
  </action>
  <verify>grep "A0023" src/main/java/com/graphnexus/common/exception/ErrorCode.java && grep "A0023" frontend/src/api/client.ts</verify>
  <done>AC-5 错误码 A0023 后端 + 前端均可用</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>QueryTaskRepository 扩展 JpaSpecificationExecutor + @DataJpaTest</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mysql/query/repository/QueryTaskRepository.java
    src/main/java/com/graphnexus/infrastructure/mysql/query/entity/QueryTaskDO.java
    .specs/adr/030-jpa-specification-dynamic-query.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/mysql/query/repository/QueryTaskRepository.java
    src/test/java/com/graphnexus/infrastructure/mysql/query/repository/QueryTaskRepositoryTest.java
  </write_files>
  <action>
    1. QueryTaskRepository 接口增加继承 JpaSpecificationExecutor&lt;QueryTaskDO&gt;
    2. 新建 @DataJpaTest 测试类，验证 Specification 按 status/subject/studentName/时间范围筛选各能返回正确结果
    3. 测试使用 @ActiveProfiles("dev") 直连 podman MySQL
    4. 沿用既有 Repository 方法命名规范，不改已有 findByTaskId
  </action>
  <verify>mvn test -pl . -Dtest="QueryTaskRepositoryTest" -DfailIfNoTests=false -q</verify>
  <done>AC-1/AC-2/AC-6 的 Repository 层支撑就绪；JpaSpecificationExecutor 可用</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>新增 DTOs/VOs + QueryService 接口声明 3 个新方法</name>
  <read_files>
    src/main/java/com/graphnexus/api/query/dto/chat/QueryAskResponse.java
    src/main/java/com/graphnexus/api/query/dto/chat/QueryResultResponse.java
    src/main/java/com/graphnexus/application/query/chat/service/QueryService.java
    src/main/java/com/graphnexus/application/query/chat/model/QueryResultBO.java
    src/main/java/com/graphnexus/common/PageResult.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/query/dto/history/HistoryQueryRequest.java
    src/main/java/com/graphnexus/api/query/dto/history/HistoryRecordVO.java
    src/main/java/com/graphnexus/application/query/chat/service/QueryService.java
  </write_files>
  <action>
    1. 新建 HistoryQueryRequest（record）：studentName, studentNo, subject, status, startDate, endDate, pageNum(默认1), pageSize(默认10)
    2. 新建 HistoryRecordVO（record → 静态工厂 from(QueryTaskDO)）：taskId, question, studentName, studentNo, subject, status, intent, tokenUsage, elapsedMs, createTime
       —— 注意：不含 answer 字段（列表不返回大文本，按 DESIGN §2.4）
    3. QueryService 接口新增 3 个方法签名：
       - PageResult&lt;HistoryRecordVO&gt; queryHistory(HistoryQueryRequest req)
       - QueryTaskDO exportSingle(String taskId)（返回 DO 供 Controller 读取 answer 流式写）
       - List&lt;QueryTaskDO&gt; exportBatch(HistoryQueryRequest req)（不加分页，上限校验在 Service 内）
    4. import 对齐既有风格（jakarta.validation.constraints / java.time.LocalDate）
  </action>
  <verify>mvn compile -pl . -q 2>&1 | grep -v "WARNING"</verify>
  <done>编译通过；QueryService 接口可见 3 个新方法；DTOs 含必要字段和校验注解</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="false" status="pending">
  <name>QueryServiceImpl 实现历史查询 + 导出方法 + 单元测试</name>
  <read_files>
    src/main/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImpl.java
    src/main/java/com/graphnexus/application/query/chat/config/QueryProperties.java
    src/main/java/com/graphnexus/infrastructure/mysql/query/repository/QueryTaskRepository.java
    src/main/java/com/graphnexus/infrastructure/mysql/query/entity/QueryTaskDO.java
    src/main/java/com/graphnexus/infrastructure/mysql/query/entity/QueryTaskStatus.java
    src/main/java/com/graphnexus/api/query/dto/history/HistoryQueryRequest.java
    src/main/java/com/graphnexus/api/query/dto/history/HistoryRecordVO.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    src/main/java/com/graphnexus/common/PageResult.java
    .specs/diagnosis-history-export/DESIGN.md
    .specs/adr/030-jpa-specification-dynamic-query.md
    .specs/adr/031-diagnosis-export-format.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImpl.java
    src/test/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImplHistoryTest.java
  </write_files>
  <action>
    实现 QueryService 新增的 3 个方法：

    1. **queryHistory(req)**：
       - 构建 Specification 动态 where 链（见 ADR-030 伪代码）
       - studentName → LIKE %val%，studentNo/subject/status → 精确匹配，startDate/endDate → >= / <=
       - 按 createTime 降序
       - 调用 queryTaskRepository.findAll(spec, pageable)
       - 映射为 PageResult&lt;HistoryRecordVO&gt;

    2. **exportSingle(taskId)**：
       - UUID 格式校验（防路径遍历）
       - findByTaskId，不存在抛 BusinessException(A0021)
       - 返回 QueryTaskDO（answer 原样由 Controller 流式写）

    3. **exportBatch(req)**：
       - 先 count：queryTaskRepository.count(spec)
       - count > 5000 → throw BusinessException(A0023)
       - count == 0 → 返回空列表（Controller 生成空 Excel）
       - 否则 Stream&lt;QueryTaskDO&gt; 逐条收集为 List 返回

    4. 新建 QueryServiceImplHistoryTest（@SpringBootTest + @ActiveProfiles("dev")）：
       - 验证分页查询返回条数正确
       - 验证筛选条件独立生效（studentName/status/subject 各 1 条用例）
       - 验证超限抛 A0023
       - 验证空结果返回空列表不报错

    5. 复杂度控制：`buildHistorySpec` 私有方法 ≤ 30 行；日志：INFO 级记导出操作（taskId/筛选条件/条数/耗时）
  </action>
  <verify>mvn test -pl . -Dtest="QueryServiceImplHistoryTest" -DfailIfNoTests=false -q</verify>
  <done>AC-1/AC-2/AC-5/AC-6 的 Service 层实现 + 单元测试通过</done>
  <depends_on>T01, T02, T03</depends_on>
</task>

<task id="T05" parallel="false" status="pending">
  <name>QueryController 新增 3 个历史查询/导出端点</name>
  <read_files>
    src/main/java/com/graphnexus/api/query/controller/QueryController.java
    src/main/java/com/graphnexus/api/query/dto/history/HistoryQueryRequest.java
    src/main/java/com/graphnexus/api/query/dto/history/HistoryRecordVO.java
    src/main/java/com/graphnexus/application/query/chat/service/QueryService.java
    src/main/java/com/graphnexus/infrastructure/mysql/query/entity/QueryTaskDO.java
    .specs/diagnosis-history-export/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/query/controller/QueryController.java
  </write_files>
  <action>
    在 QueryController 中新增 3 个端点（Swagger 注解对齐已有风格）：

    1. **GET /api/v1/query/history**：
       - 接收 HistoryQueryRequest（用 @ModelAttribute 或直接 @RequestParam 逐个声明，对齐项目风格）
       - 查询参数均 optional，默认 pageNum=1, pageSize=10
       - 调用 queryService.queryHistory(req)，返回 ApiResult&lt;PageResult&lt;HistoryRecordVO&gt;&gt;
       - Swagger @Operation: "历史诊断记录分页查询"

    2. **GET /api/v1/query/history/{taskId}/export**：
       - 调用 queryService.exportSingle(taskId) 获取 QueryTaskDO
       - 按 ADR-031 判定 answer 首字符 → Content-Type + 文件名后缀
       - 返回 ResponseEntity&lt;StreamingResponseBody&gt;，异步写 answer.getBytes(UTF-8)
       - Swagger @Operation: "导出单条诊断报告"
       - taskId 做 UUID 格式校验（@Pattern regexp）

    3. **GET /api/v1/query/history/export**：
       - 接收同 history 的筛选参数（不含 pageNum/pageSize）
       - 调用 queryService.exportBatch(req) 获取 List&lt;QueryTaskDO&gt;
       - SXSSFWorkbook(100) 写 Excel：表头行 → 逐行写数据（9 列，见 DESIGN §2.3）
       - Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
       - filename: diagnosis-history-{yyyyMMdd}.xlsx
       - 返回 ResponseEntity&lt;StreamingResponseBody&gt;
       - Swagger @Operation: "批量导出历史记录为 Excel"

    4. 已有 ask/chat/getResult 方法不变，不引入新 Controller 类
  </action>
  <verify>
    # 需先通过 T04 启动服务，再跑 curl：
    # curl -s "http://localhost:8080/api/v1/query/history?pageNum=1&pageSize=3" | jq '.data.list | length'
    # curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/api/v1/query/history/{已有taskId}/export"
    # curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/api/v1/query/history/export"
  </verify>
  <done>3 个新端点可访问；/history 返回分页 JSON；/history/{taskId}/export 返回文件下载；/history/export 返回 Excel</done>
  <depends_on>T03</depends_on>
</task>

<task id="T06" parallel="true" status="pending">
  <name>前端 API types + query.ts 新增历史查询/导出函数</name>
  <read_files>
    frontend/src/api/types.ts
    frontend/src/api/query.ts
    frontend/src/api/client.ts
    .specs/diagnosis-history-export/DESIGN.md
  </read_files>
  <write_files>
    frontend/src/api/types.ts
    frontend/src/api/query.ts
  </write_files>
  <action>
    1. types.ts 新增：
       - HistoryQueryParams 接口（studentName?, studentNo?, subject?, status?, startDate?, endDate?, pageNum?, pageSize?）
       - HistoryRecordVO 接口（taskId, question, studentName, studentNo, subject, status, intent, tokenUsage: TokenUsageVO | null, elapsedMs, createTime）
       —— 注意：不含 answer 字段（列表不返回大文本）
    2. query.ts 新增 3 个函数：
       - getHistory(params: HistoryQueryParams): Promise<PageResult<HistoryRecordVO>> — GET /query/history，params 序列化为 query string（过滤 null/undefined）
       - exportSingle(taskId: string): Promise<Blob> — GET /query/history/{taskId}/export { responseType: 'blob' }，绕过 JSON 拦截器
       - exportBatch(params: HistoryQueryParams): Promise<Blob> — GET /query/history/export { responseType: 'blob' }
    3. exportSingle/exportBatch 使用独立的 axios 实例或直接 fetch，避免 client 拦截器 unwrap blob 响应
    4. 对齐已有 API 函数风格（函数签名 + JSDoc 注释）
  </action>
  <verify>npx vue-tsc --noEmit --project frontend 2>&1 | head -5</verify>
  <done>vue-tsc 类型检查通过；API 函数签名与后端 DESIGN 对齐</done>
  <depends_on>T05</depends_on>
</task>

<task id="T07" parallel="true" status="pending">
  <name>queryStore 扩展 history 状态切片</name>
  <read_files>
    frontend/src/views/query/queryStore.ts
    frontend/src/api/query.ts
    frontend/src/api/types.ts
  </read_files>
  <write_files>
    frontend/src/views/query/queryStore.ts
  </write_files>
  <action>
    在 queryStore.ts 中追加 history 状态切片（与现有 chat 状态共存，按 DESIGN D8）：

    1. 新增状态：
       - historyRecords: ref&lt;HistoryRecordVO[]&gt;([])
       - historyTotal: ref&lt;number&gt;(0)
       - historyLoading: ref&lt;boolean&gt;(false)
       - historyPage: ref&lt;number&gt;(1)
       - historyPageSize: ref&lt;number&gt;(10)
       - historyFilters: ref&lt;HistoryQueryParams&gt;({})
    2. 新增方法：
       - loadHistory(page?, pageSize?) — 调用 getHistory，更新 records + total + loading
       - resetHistoryFilters() — 清空筛选 + 重置分页 + 重新加载
       - downloadSingleExport(taskId) — 调用 exportSingle，创建 Blob URL → a.click() 下载
       - downloadBatchExport() — 调用 exportBatch(当前筛选参数)，同上
    3. 下载方法使用工具函数 createDownloadLink(blob, filename)，在 store 内实现
    4. 不改动已有 chat 相关的 sendChat/sendAsync/handleResult 方法
  </action>
  <verify>npx vue-tsc --noEmit --project frontend 2>&1 | head -5</verify>
  <done>vue-tsc 类型检查通过；queryStore 导出 history 相关状态和方法</done>
  <depends_on>T06</depends_on>
</task>

<task id="T08" parallel="false" status="pending">
  <name>HistoryPanel.vue 历史记录面板组件</name>
  <read_files>
    frontend/src/views/query/components/ChatInput.vue
    frontend/src/views/query/components/MarkdownReport.vue
    frontend/src/views/query/components/TokenUsageBar.vue
    frontend/src/common/components/DataTable.vue
    frontend/src/common/components/StatusBadge.vue
    frontend/src/common/components/HtmlSvgViewer.vue
    frontend/src/views/query/queryStore.ts
    frontend/src/api/types.ts
    .specs/diagnosis-history-export/DESIGN.md
  </read_files>
  <write_files>
    frontend/src/views/query/components/HistoryPanel.vue
  </write_files>
  <action>
    新建 HistoryPanel.vue，结构见 DESIGN §2.5：

    1. **折叠面板**：使用 Naive UI NCollapse，默认折叠，标题"历史记录"
    2. **筛选栏**（面板展开后显示）：
       - 学生姓名：NInput（placeholder "学生姓名"）
       - 学科：NSelect（选项从既有学科列表获取，或无选项时用 NInput）
       - 状态：NSelect（COMPLETED / FAILED，加"全部"选项）
       - 时间范围：NDatePicker range 模式
       - 查询按钮：NButton（点击 → store.resetHistoryFilters() 或 loadHistory）
    3. **列表**：
       - 复用 DataTable.vue，列定义：提问时间（formatted）、问题（截断 ≤50 字符 + ellipsis tooltip）、学生姓名、学科、状态（复用 StatusBadge.vue）、操作（导出按钮）
       - 分页：DataTable 自带 NPagination
    4. **展开详情**：
       - 点击行 → emit expand，父组件或本组件内调用 GET /query/result/{taskId} 获取完整 answer
       - 按 outputFormat 选 MarkdownReport 或 HtmlSvgViewer 渲染
       - 下方显示 TokenUsageBar
    5. **工具栏**：
       - "批量导出"按钮（NButton + Download 图标），点击 → store.downloadBatchExport()
       - 若筛选结果为空 → 按钮禁用 + tooltip
    6. 加载态用 NSpin，空态用 NEmpty（"暂无历史诊断记录"）
    7. CSS 作用域：复用项目 CSS 变量（--spacing-*, --color-*, --rounded-*）
  </action>
  <verify>npx vue-tsc --noEmit --project frontend 2>&1 | head -5</verify>
  <done>vue-tsc 类型检查通过；HistoryPanel.vue 含筛选栏 + 分页列表 + 展开详情 + 导出按钮</done>
  <depends_on>T06, T07</depends_on>
</task>

<task id="T09" parallel="false" status="pending">
  <name>IntelligentQAPage.vue 集成 HistoryPanel + 全量验证</name>
  <read_files>
    frontend/src/views/query/IntelligentQAPage.vue
    frontend/src/views/query/components/HistoryPanel.vue
    frontend/src/views/query/queryStore.ts
    .specs/diagnosis-history-export/REQUIREMENT.md
  </read_files>
  <write_files>
    frontend/src/views/query/IntelligentQAPage.vue
  </write_files>
  <action>
    1. 在 IntelligentQAPage.vue 中 ChatInput 下方（loading/error state 之后、qa-history 之前）插入 HistoryPanel
    2. import HistoryPanel，注册组件
    3. 不改动 ChatInput / qa-history / loading/error 区的已有模板和逻辑
    4. 全量验证：
       - vue-tsc 类型检查
       - 逐条核验 AC-8~AC-14：
         AC-8: 面板展开 → 列表显示
         AC-9: 筛选 → 列表刷新
         AC-10: 翻页 → 列表切换
         AC-11: 单条导出 → 文件下载
         AC-12: 批量导出 → Excel 下载
         AC-13: 展开详情 → LLM 报告渲染
         AC-14: loading/空状态
  </action>
  <verify>
    npx vue-tsc --noEmit --project frontend 2>&1 | grep -c "error" | xargs -I{} sh -c 'test {} -eq 0'
  </verify>
  <done>vue-tsc 零错误；AC-8~AC-14 核验通过（手动浏览器验证，见 AC 验证方式列）</done>
  <depends_on>T08</depends_on>
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

> 此区域由 review/integration 阶段自动追加。

```xml
<!-- 占位 -->
```