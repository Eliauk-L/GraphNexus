# ADR-048 · 日志 API 设计与路径安全

> **状态**: proposed
> **日期**: 2026-06-23
> **Change**: `ops-health-log`
> **关联**: DESIGN.md § D3, D4, D8

---

## Context

需要提供日志文件列表、分页内容查看、文件下载的 REST API。日志文件存储在应用工作目录下的 `logs/` 目录中，文件名为 `graphnexus-dev.log`、`graphnexus-error.log` 及对应的 `.gz` 滚动归档。

关键安全约束：API 接受用户提供的 `filename` 参数，必须防止路径遍历攻击（如 `../../etc/passwd`）。

---

## Decision

**1. API 端点设计**

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/system/logs` | 返回日志文件列表（文件名、大小、可读大小、最后修改时间），按修改时间倒序 |
| `GET` | `/api/v1/system/logs/{filename}` | 分页返回日志内容。Query: `page`(默认 1), `size`(默认 200)。响应：当前页行数组 + 分页元数据 |
| `GET` | `/api/v1/system/logs/{filename}/download` | 下载日志文件。Content-Type: `application/octet-stream`，Content-Disposition: `attachment; filename="..."` |

**2. 日志文件目录**

日志目录路径通过 `@Value("${logging.file.dir:logs}")` 注入，默认 `logs/`（与 logback-spring.xml 的 `LOG_DIR` 一致）。不硬编码路径。

**3. 文件读取方式**

使用 `Files.lines(Path)` 流式读取：
- 总行数：`Files.lines(path).count()`
- 页面内容：`Files.lines(path).skip((page-1)*size).limit(size).collect(toList())`

**4. 路径遍历防护（双重校验）**

```java
// 第一层：字符级黑名单
if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
    throw new BusinessException(ErrorCode.A0023, "文件名非法");
}

// 第二层：OS 级路径解析验证
Path logDir = Path.of(logDirectory).toRealPath();
Path filePath = logDir.resolve(filename).toRealPath();  // 文件不存在时用 toAbsolutePath().normalize()
if (!filePath.startsWith(logDir)) {
    throw new BusinessException(ErrorCode.A0023, "路径越权");
}
```

第一层快速拒绝明显攻击，第二层利用 OS 的路径规范化确保不会逃逸出 `logs/` 目录。

**5. 分页参数**

- `page` 从 1 开始（前端 Naive UI `n-pagination` 默认约定）
- `size` 默认 200 行，单次最大 500 行（防滥用）
- 总页数 = `ceil(totalLines / size)`
- 请求的页号超出范围时返回空列表（不报错）

---

## Consequences

**正面**：
- 流式读取 O(1) 内存，10MB 文件不会导致 heap 压力
- 双重路径校验提供纵深防御（即使一层被绕过仍有第二层）
- 分页参数与前端 Naive UI `n-pagination` 组件协议一致
- API 设计简洁，三个端点覆盖运维场景

**负面**：
- 每页请求读两次文件（count + content），文件 >100MB 时可能变慢；但日志滚动策略限制单文件 ≤10MB，风险可控
- 不支持反向读取（tail -f），想看最新日志必须先知道总行数然后跳到最后几页——v2 可加 `?tail=100` 参数支持
- 不支持跨文件搜索/过滤，运维人员需先下载再用 grep——v2 可加 `?filter=ERROR` 参数

**约束**：
- `logs/` 目录必须在应用工作目录下可读
- 日志文件名不能含路径分隔符（现有 logback 配置已满足）
- 每次请求都重新读取文件（不缓存行偏移索引），文件被 logback 滚动删除时当前页请求返回 404