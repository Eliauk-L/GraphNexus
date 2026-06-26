# T03-SUMMARY: fusion 全栈搬迁 graph→analysis + 删除 GradeUploadedEventListener

- **Task ID**: T03
- **Change ID**: fusion-to-analysis-event-driven
- **状态**: ✅ 完成

---

## 做了什么

纯机械搬迁 + package 重命名 + import 更新 + 删除 `GradeUploadedEventListener`，不改业务逻辑。

### 1. application 层搬迁（21 个文件）

从 `application/graph/fusion/` 迁至 `application/analysis/fusion/`，子包结构不变（config/model/service/service-impl/strategy）。

每文件：package 声明 `graph.fusion.*` → `analysis.fusion.*`；内部互引 import 同步更新。

**排除**: `GradeUploadedEventListener.java` — 直接删除，不搬迁（D5）。

### 2. api 层搬迁（4 个文件）

- 3 个 VO：`api/graph/dto/fusion/Fusion{Execute,Status,Rollback}VO.java` → `api/analysis/dto/fusion/`
- `FusionController.java` → `api/analysis/controller/FusionController.java`
  - `@RequestMapping("/api/v1/graph/fusion")` → `"/api/v1/analysis/fusion"`（D8）
  - import `FusionService` → `analysis.fusion.service`
  - import model 类 → `analysis.fusion.model`

### 3. 外部 import 更新

- `ConstructionServiceImpl.java`: import `FusionService` 路径从 `graph.fusion.service` → `analysis.fusion.service`（仅 import，不删注入/调用——T05 执行）

### 4. 旧文件删除

- 删除 `application/graph/fusion/` 全部文件（含 `GradeUploadedEventListener.java`）
- 删除 `api/graph/dto/fusion/` 下 3 个 VO
- 删除 `api/graph/controller/FusionController.java`

---

## 改动文件

| 文件 | 操作 |
|------|------|
| `application/analysis/fusion/**` (21 files) | 新建（迁入，package 重命名） |
| `api/analysis/dto/fusion/*` (3 files) | 新建（迁入，package 重命名） |
| `api/analysis/controller/FusionController.java` | 新建（迁入，URL + import 更新） |
| `application/graph/fusion/**` (22 files) | 删除（含 GradeUploadedEventListener） |
| `api/graph/dto/fusion/*` (3 files) | 删除 |
| `api/graph/controller/FusionController.java` | 删除 |
| `application/graph/construction/service/impl/ConstructionServiceImpl.java` | 修改（仅 import 路径） |

---

## verify 输出

```
mvn compile: EXIT 0 ✅
AC-1 grep: 主代码无 application.graph.fusion / api.graph.dto.fusion / api.graph.controller.FusionController 残留 ✅
analysis.fusion 路径确认：21 个文件在新位 ✅
GradeUploadedEventListener 已删除 ✅
```

---

## 6 维自查（R6.4）

- ✅ 沿用既有抽象 grep：搬迁为纯机械操作，不改业务逻辑；FusionController 的 `@RequestMapping` 改为 `/api/v1/analysis/fusion`（D8 决策）；ConstructionServiceImpl import 路径更新
- R1 认知过载：纯搬迁，无逻辑复杂度 ✅
- R2 变更传播：严格在 DESIGN 触碰模块范围内 ✅
- R3 知识重复：无新增重复 ✅
- R4 偶然复杂：无新增扩展点 ✅
- R5 依赖混乱：外部 import（如 GraphChangedEvent）保持不变 ✅
- R6 领域扭曲：package 命名 `analysis.fusion` 对齐模块归属 ✅

---

## 越界检查（R6.5）

- TASK write_files 覆盖：fusion 全栈 21+3+1 文件迁入 + 旧文件删除 + ConstructionServiceImpl import 更新
- 实际 diff 涉及：上述文件
- 越界：0 ✅（未触碰 metrics / neo4j infra / pom.xml / 禁动清单）

---

## 破坏性变更

命中（D8 REST 路径变更 `/api/v1/graph/fusion/*` → `/api/v1/analysis/fusion/*`），但：
- 前端是唯一消费者（管理后台内部使用）
- 前端同步更新（T02/T07 配合）
- 无外部 API 消费者
- 按 DESIGN D8 执行，不保留别名

---

## 数据库迁移

不涉及。
