# T02-SUMMARY: 移动 L3 配置类到 config/ 子包

- **Task ID**: T02
- **Change ID**: refine-package-structure
- **完成时间**: 2026-06-12

---

## 做了什么

将 3 个 L3 配置类按 DESIGN § D3 移动到 config/ 子包：

1. `JpaAuditConfig.java`: `infrastructure.mysql` → `infrastructure.mysql.config`（git mv）
2. `MinioConfig.java`: `infrastructure.storage` → `infrastructure.storage.config`（git mv）
3. `MinioProperties.java`: `infrastructure.storage` → `infrastructure.storage.config`（git mv）

每个文件只改了 package 声明。此外 `FileStorageService.java` 需新增一条 import（`MinioProperties` 从同包引用变为跨包子包引用，已获用户授权扩写 write_files）。

## 改动文件

| 文件 | 变更 |
|------|------|
| `mysql/JpaAuditConfig.java` → `mysql/config/JpaAuditConfig.java` | rename + package 声明 |
| `storage/MinioConfig.java` → `storage/config/MinioConfig.java` | rename + package 声明 |
| `storage/MinioProperties.java` → `storage/config/MinioProperties.java` | rename + package 声明 |
| `storage/FileStorageService.java` | +1 import（MinioProperties 新路径） |

## verify 输出

```
mvn compile -q → 零错误通过
```

## 6 维自查

- **R1 认知过载**：N/A（每个文件只改 1 行 package 声明）
- **R2 变更传播**：⚠️ 越界 1 处（FileStorageService.java），已获用户授权扩写 write_files
- **R3 知识重复**：N/A
- **R4 偶然复杂**：✅ 3 个 config/ 子包均对应 DESIGN.md 明确需求
- **R5 依赖混乱**：✅ MinioProperties 作为配置类被 FileStorageService 引用是正常的 L3 内部依赖
- **R6 领域扭曲**：✅ config/ 子包命名符合 Spring 惯例

## 沿用既有抽象 grep（R6.4）

- JpaAuditConfig：无外部 import（Spring `@Configuration` 由组件扫描发现）→ 仅改自身 package
- MinioConfig：无外部 import（同上）→ 仅改自身 package
- MinioProperties：由 FileStorageService 同包引用 → 需新增 import（已加）

## 越界检查（R6.5）

- TASK write_files：7 项（含用户授权追加的 FileStorageService.java）
- 实际 diff 涉及：4 files（3 rename + 1 modify）
- 越界：0 ✅（经用户确认扩范围）

## 提交

`feat(refine-package-structure): T02 移动L3配置类到config子包` (`45a7214`)