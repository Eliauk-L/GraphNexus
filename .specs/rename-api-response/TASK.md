# TASK — rename-api-response

> 重命名系统响应体 `ApiResponse` → `ApiResult`，消除与 Swagger `@ApiResponse` 注解的命名冲突。

- **Change ID**: `rename-api-response`
- **创建日期**: 2026-06-17
- **上游参考**: `CHANGE.md`

---

## 目标名称决策

| 选项 | 评估 | 结论 |
|------|------|------|
| `ApiResult` | 与原名称 `ApiResponse` 语义接近，与 Swagger 的 `@ApiResponse` 清晰区分，不会与其他 JDK/Spring 类冲突 | ✅ 采用 |
| `Result` | 过于泛化，可能与 `java.sql.ResultSet` 等在 import 时产生心理混淆 | ❌ 排除 |

---

## 波次划分

```
Wave 1:            T01 (rename class)
Wave 2 (parallel): T02[P], T03[P] (update refs | update docs)
Wave 3:            T04 (verify, depends on T02, T03)
```

---

<task id="T01">
  <name>重命名 ApiResponse.java → ApiResult.java</name>
  <read_files>
    src/main/java/com/graphnexus/common/ApiResponse.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/ApiResult.java
    src/main/java/com/graphnexus/common/ApiResponse.java
  </write_files>
  <action>
    1. 将 ApiResponse.java 中的 record 名从 `ApiResponse` 改为 `ApiResult`
    2. 更新类内所有自引用（构造器调用、静态工厂方法返回类型、Javadoc 中的类名引用）
    3. 将文件重命名为 ApiResult.java（删除旧文件 ApiResponse.java）
    4. 包名和字段结构（code/message/data/traceId/timestamp）保持不变
  </action>
  <verify>grep -r "class ApiResponse\|record ApiResponse" src/main/java/com/graphnexus/common/ 返回空（确认旧类名已不存在）</verify>
  <done>ApiResult.java 编译就绪，旧 ApiResponse.java 已删除</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true">
  <name>更新所有 Controller 的 import 和返回类型，简化 Swagger 注解 import</name>
  <read_files>
    src/main/java/com/graphnexus/api/document/controller/DocumentController.java
    src/main/java/com/graphnexus/api/graph/controller/GraphController.java
    src/main/java/com/graphnexus/api/graph/controller/FusionController.java
    src/main/java/com/graphnexus/api/graph/controller/MetricsController.java
    src/main/java/com/graphnexus/api/analysis/controller/AnalysisController.java
    src/main/java/com/graphnexus/api/query/controller/QueryController.java
    src/test/java/com/graphnexus/api/graph/controller/FusionControllerIntegrationTest.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/document/controller/DocumentController.java
    src/main/java/com/graphnexus/api/graph/controller/GraphController.java
    src/main/java/com/graphnexus/api/graph/controller/FusionController.java
    src/main/java/com/graphnexus/api/graph/controller/MetricsController.java
    src/main/java/com/graphnexus/api/analysis/controller/AnalysisController.java
    src/main/java/com/graphnexus/api/query/controller/QueryController.java
    src/test/java/com/graphnexus/api/graph/controller/FusionControllerIntegrationTest.java
  </write_files>
  <action>
    对每个 Controller 文件：
    1. 将 `import com.graphnexus.common.ApiResponse;` 改为 `import com.graphnexus.common.ApiResult;`
    2. 将所有 `ApiResponse<...>` 返回类型改为 `ApiResult<...>`
    3. 将所有 `ApiResponse.success(...)` / `ApiResponse.error(...)` 调用改为 `ApiResult.success(...)` / `ApiResult.error(...)`
    4. 将 `import io.swagger.v3.oas.annotations.responses.ApiResponses;` 改为同时 import `ApiResponse` 注解：
       `import io.swagger.v3.oas.annotations.responses.ApiResponse;`
       `import io.swagger.v3.oas.annotations.responses.ApiResponses;`
    5. 将所有 `@io.swagger.v3.oas.annotations.responses.ApiResponse(...)` 全限定名替换为简洁的 `@ApiResponse(...)`
    6. 对 FusionControllerIntegrationTest.java 同样更新 import
  </action>
  <verify>grep -r "com.graphnexus.common.ApiResponse" src/ --include="*.java" 返回空（确认无残留旧 import）</verify>
  <done>所有 Java 源文件中不再出现 com.graphnexus.common.ApiResponse，Swagger @ApiResponse 使用简洁 import</done>
  <depends_on>T01</depends_on>
</task>

<task id="T03" parallel="true">
  <name>更新文档中 ApiResponse 类名引用</name>
  <read_files>
    src/main/java/com/graphnexus/api/gateway/config/OpenApiConfig.java
    .specs/CONTEXT.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/gateway/config/OpenApiConfig.java
    .specs/CONTEXT.md
  </write_files>
  <action>
    1. OpenApiConfig.java 第 41 行：将描述文本中的 `ApiResponse<T>` 改为 `ApiResult<T>`
    2. .specs/CONTEXT.md 第 182 行：将 `common/ApiResponse.java` 改为 `common/ApiResult.java`
  </action>
  <verify>grep -r "ApiResponse" src/main/java/com/graphnexus/api/gateway/config/OpenApiConfig.java .specs/CONTEXT.md 返回空或仅剩 Swagger 注解引用（确认旧类名文档引用已更新）</verify>
  <done>OpenApiConfig 和 CONTEXT.md 中使用新类名 ApiResult</done>
  <depends_on>T01</depends_on>
</task>

<task id="T04">
  <name>编译验证 + 运行测试</name>
  <read_files>
    src/main/java/com/graphnexus/common/ApiResult.java
    src/main/java/com/graphnexus/api/**/*.java
    src/test/java/com/graphnexus/api/**/*.java
  </read_files>
  <write_files>
  </write_files>
  <action>
    1. 执行 `mvn compile -q` 确保编译无错误
    2. 执行 `mvn test -q` 确保所有现有测试通过
    3. 如有编译错误或测试失败，回溯修复对应任务
  </action>
  <verify>mvn compile -q && mvn test -q</verify>
  <done>mvn compile 和 mvn test 均通过，无回归</done>
  <depends_on>T02, T03</depends_on>
</task>