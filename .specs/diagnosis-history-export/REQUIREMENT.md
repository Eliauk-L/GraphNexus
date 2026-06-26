# REQUIREMENT: 学情诊断历史记录查询与导出

- **Change ID**: `diagnosis-history-export`
- **关联**: `@.specs/diagnosis-history-export/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为教师/管理员，我想在学情诊断页面查看历史诊断记录列表，以便回溯之前的分析结论。
- **US-2**：作为教师/管理员，我想按学生、学科、状态、时间范围筛选历史记录，以便快速定位特定诊断。
- **US-3**：作为教师/管理员，我想导出单条诊断的完整分析报告为 HTML 文件，以便保存或分享给同事/家长。

## 验收准则（AC）

### AC-1 · 历史记录分页查询（后端）

- **Given** `query_task` 表中存在多条诊断记录
- **When** 前端调用 `GET /api/v1/query/history?pageNum=1&pageSize=10`
- **Then** 返回 HTTP 200，响应体包含分页列表（`list` + `total` + `pageNum` + `pageSize`），每条记录含 `taskId`、`question`、`studentName`、`studentNo`、`subject`、`status`、`createTime`，按 `createTime` 降序排列
- **验证方式**: `curl -s "http://localhost:8080/api/v1/query/history?pageNum=1&pageSize=10" | jq '.data.list | length'` 返回 `> 0`（需有历史数据）

### AC-2 · 历史记录筛选查询（后端）

- **Given** `query_task` 表中存在多条不同学生、不同学科的诊断记录
- **When** 前端调用 `GET /api/v1/query/history?subject=数学&status=COMPLETED&startDate=2026-06-01&endDate=2026-06-30`
- **Then** 返回仅包含"数学"学科 + "COMPLETED" 状态 + 指定时间范围内的记录，其他记录被过滤
- **验证方式**: `curl -s "http://localhost:8080/api/v1/query/history?subject=数学&status=COMPLETED&startDate=2026-06-01&endDate=2026-06-30" | jq '.data.list[].subject'` 全部为 `"数学"`

### AC-3 · 单条诊断报告 HTML 导出（后端）

- **Given** `query_task` 表中存在一条 COMPLETED 状态的诊断记录（含 `answer` 字段）
- **When** 前端调用 `GET /api/v1/query/history/{taskId}/export`
- **Then** 返回 HTTP 200，`Content-Type: text/html; charset=UTF-8`，`Content-Disposition: attachment; filename="diagnosis-{taskId前8位}.html"`，响应体为该记录的 `answer` 原始文本
- **验证方式**: `curl -O -J "http://localhost:8080/api/v1/query/history/{taskId}/export"` 下载文件，`diff <(cat 下载文件) <(数据库 answer 字段)` 无差异

### AC-4 · 历史记录查询空结果处理（后端）

- **Given** 筛选条件下无匹配记录
- **When** 前端调用 `GET /api/v1/query/history?studentName=不存在的学生`
- **Then** 返回 HTTP 200，`list` 为空数组 `[]`，`total` 为 0，不报错
- **验证方式**: `curl -s "http://localhost:8080/api/v1/query/history?studentName=noone" | jq '.data'` → `list: [], total: 0`

### AC-5 · 单条导出记录不存在处理（后端）

- **Given** 指定 `taskId` 在 `query_task` 表中不存在
- **When** 前端调用 `GET /api/v1/query/history/{taskId}/export`
- **Then** 返回 HTTP 404，错误码 `A0021`，提示"任务不存在"
- **验证方式**: `curl -s "http://localhost:8080/api/v1/query/history/nonexistent-id/export" | jq '.data.errorCode'` → `"A0021"`

### AC-8 · 历史面板展示（前端）

- **Given** 用户已进入学情诊断页面 `/diagnosis`
- **When** 用户点击输入框下方的"历史记录"折叠面板标题
- **Then** 面板展开，显示分页的历史记录列表，每行展示：提问时间（`YYYY-MM-DD HH:mm`）、问题文本（截断 ≤ 50 字符）、学生姓名、学科、状态标签（COMPLETED=绿色/F AILED=红色），默认按时间倒序，每页 10 条
- **验证方式**: 浏览器打开 `/diagnosis`，点击"历史记录"，肉眼确认列表字段、排序、状态颜色；或 E2E 测试断言 `.history-row` 元素数量 ≤ 10

### AC-9 · 历史面板筛选交互（前端）

- **Given** 用户展开历史记录面板
- **When** 用户在筛选栏输入"学生姓名 = 张三"、"学科 = 数学"，点击"查询"按钮
- **Then** 列表刷新，仅显示符合筛选条件的记录，分页重置到第 1 页
- **验证方式**: 浏览器操作：展开面板 → 输入筛选条件 → 点击查询 → 肉眼确认列表过滤结果；或 E2E 测试断言列表项均含"张三"

### AC-10 · 历史面板分页交互（前端）

- **Given** 历史记录总数 > 10 条
- **When** 用户点击"下一页"或页码按钮
- **Then** 列表切换到对应页，页码高亮更新，上一页/下一页按钮状态正确（第 1 页时"上一页"禁用，最后一页时"下一页"禁用）
- **验证方式**: 浏览器操作：点击第 2 页 → 肉眼确认列表变为第 11-20 条；或 E2E 测试断言分页组件 `currentPage == 2`

### AC-11 · 单条导出按钮交互（前端）

- **Given** 历史面板中某条 COMPLETED 状态的记录
- **When** 用户点击该行的"导出"按钮
- **Then** 浏览器触发文件下载（`.html` 文件），文件名格式 `diagnosis-{taskId前8位}.html`，文件内容为该条诊断的完整 LLM 分析报告
- **验证方式**: 浏览器操作：点击导出 → 检查下载目录存在 `.html` 文件，内容含 LLM 分析报告正文

### AC-12 · 单条记录展开详情（前端）

- **Given** 历史面板中某条 COMPLETED 状态的记录
- **When** 用户点击该行（或展开按钮）
- **Then** 该行下方展开显示完整诊断报告内容（复用现有 `MarkdownReport` 或 `HtmlSvgViewer` 组件渲染），再次点击收起
- **验证方式**: 浏览器操作：点击某行 → 肉眼确认展开显示完整报告；再次点击 → 报告收起

### AC-13 · 加载与空状态（前端）

- **Given** 用户展开历史面板
- **When** 数据正在加载中（网络请求未完成）
- **Then** 列表区域显示加载指示器（loading spinner）
- **When** 请求返回空列表
- **Then** 列表区域显示"暂无历史诊断记录"空状态提示
- **验证方式**: 浏览器操作：展开面板 → 短暂看到 loading → 数据展示；或构造无数据场景 → 看到空状态提示

---

## 范围切分

### v1（本次必做）

- 历史记录分页查询 API（`GET /api/v1/query/history`）含筛选参数
- 单条诊断报告导出 API（`GET /api/v1/query/history/{taskId}/export`）— HTML 下载
- 前端 HistoryPanel 组件：折叠面板 + 筛选栏 + 分页列表 + 单条展开详情 + 导出按钮
- 前端状态管理（queryStore 扩展 history 持久化查询能力）

### v2（下一轮考虑，不本次）

- **导出 PDF**：单条诊断报告渲染为 PDF（带页眉页脚、学校 Logo）后导出
- **收藏/标记**：用户可对重要诊断记录加星标，按标记筛选
- **诊断结论对比**：选择两条诊断记录并排对比分析结论差异
- **批量操作**：多选删除、批量重新诊断
- **诊断记录分享链接**：生成临时分享链接（token 过期），供家长端查看
- **统计面板**：诊断次数趋势图、学科分布饼图、高频问题词云

### out（永远不做）

- **历史记录编辑**：`query_task` 为日志类表，诊断结论不可篡改
- **实时 WebSocket 推送**：新诊断完成不推送到历史面板，用户手动刷新
- **跨租户/跨学校数据隔离**：v1 为单实例部署，不做多租户
- **语音输入/播报**：不支持语音交互

---

## 非功能性需求

- **性能**: 历史查询 API 响应时间 ≤ 500ms（P95，数据集 ≤ 10 万条记录）；前端面板展开加载首屏 ≤ 1s
- **可访问性**: 无特殊要求（v1 桌面端管理后台，参照现有页面标准）
- **安全**: 导出接口复用现有 Spring Security 认证体系（当前全放通，后续接入后自动受保护）；文件下载防止路径遍历攻击（taskId 只接受 UUID 格式）
- **兼容性**: 桌面端 ≥ 1280px 宽度（与现有前端 V1 分辨率一致），Chrome/Firefox/Edge 120+
- **可观测性**: 导出操作记录 INFO 日志（含 taskId/筛选条件/导出条数/耗时）；历史查询不额外记日志（查询类操作不产生业务日志）

## 依赖与假设

- **依赖**: 复用 `query_task` 表及其现有字段，不新增列；复用 `QueryTaskRepository` 并扩展查询方法；复用 `MarkdownReport.vue` / `HtmlSvgViewer.vue` 组件渲染诊断报告；复用 Apache POI 5.2.5（已在 `pom.xml` 中）
- **假设**: `query_task` 表中 `answer` 字段存储的是最终展示格式的文本（Markdown 或 HTML+SVG），导出时不需二次转换；前端 `IntelligentQAPage.vue` 结构允许在 ChatInput 下方插入新组件；用户有基本的 Excel 打开能力

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。