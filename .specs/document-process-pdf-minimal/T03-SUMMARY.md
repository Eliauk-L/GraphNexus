# T03-SUMMARY: MinioConfig + MinioProperties + FileStorageService

- **任务**: T03 — MinIO 存储基础设施
- **状态**: ✅ done
- **提交**: `9d658cb`

---

## 做了什么

1. **MinioProperties**：`@ConfigurationProperties(prefix="minio")` 绑定 dev.yml 中的 MinIO 配置（endpoint/accessKey/secretKey/bucket）
2. **MinioConfig**：创建 `MinioClient` Bean，启动时自动检查 Bucket 是否存在，不存在则 `makeBucket()`
3. **FileStorageService**：薄封装三个基础操作——`uploadFile(InputStream, objectKey, contentType)`、`getFile(objectKey)`、`deleteFile(objectKey)`，异常时抛 `BusinessException`

## 改动文件

- `infrastructure/storage/MinioProperties.java` (新增)
- `infrastructure/storage/MinioConfig.java` (新增)
- `infrastructure/storage/FileStorageService.java` (新增)

## verify 输出

```
BUILD SUCCESS (33 source files, 1.698s)
```

## 6 维自查

- R1 认知过载：无，三个类合计 < 100 行
- R2 变更传播：仅 infrastructure/storage/ 目录
- R3 知识重复：无
- R4 偶然复杂：无
- R5 依赖混乱：L3 → common(BusinessException) ✅；无反向依赖
- R6 领域扭曲：`objectKey` 而非 `fileName`——MinIO 术语准确

## 越界检查

```
✅ TASK write_files：3 项
✅ 实际 diff 涉及：3 项
✅ 越界：0
```

## 沿用既有抽象 grep（R6.4）

- BusinessException：找到 `common/exception/BusinessException.java` → 沿用
- ErrorCode：找到 `common/exception/ErrorCode.java` → 沿用
- @Service/@Configuration 模式：项目中 `package-info.java` 定义了该包的 Spring 组件角色 → 沿用

## TDD 豁免

纯基础设施配置类，逻辑为 MinioClient API 调用封装，无独立业务逻辑可单元测试。集成测试会在 T10 中覆盖。