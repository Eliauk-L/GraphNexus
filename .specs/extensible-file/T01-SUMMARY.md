# T01-SUMMARY — 枚举与接口基础

- **Task**: T01 — FileParseType 重命名 + FileStatus 8 状态 + FileParser.priority()
- **日期**: 2026-06-18
- **状态**: done

---

## 做了什么

1. **FileParseType**：枚举值已在上次重构完成（`PDF_DOCUMENT → DOCUMENT` + 新增 `TXT`）。T01 无新改动。
2. **FileStatus**：5 状态 → 8 状态扩展。
   - 新增 `PARSING / PARSED / EXTRACTING / EXTRACTED / FUSING` 五个状态
   - 保留 `UPLOADED / COMPLETED / FAILED / DELETING`
   - 移除 `PROCESSING`（被 `PARSING` 替代）
   - 重写 `getAllowedTargets()`：支持失败回退（`*ING → *ED`）+ 手动重新处理（`COMPLETED → PARSING/EXTRACTING/FUSING`）
3. **FileParser**：新增 `default int priority()` 方法（默认 0）。
4. **编译兼容修正**（T01 超范围但必需，否则编译不过）：
   - `PdfBoxDocumentParser` / `MinerUDocumentParser`：`PDF_DOCUMENT` → `DOCUMENT`
   - `FileServiceImpl.process()`：`PROCESSING` → `PARSING`
   - `FileStatusTest`：重写 25 个测试覆盖 v2 状态机

## 改动的文件

| 文件 | 改动 |
|------|------|
| `FileParser.java` | 新增 `priority()` default 方法 |
| `FileStatus.java` | 5→8 状态 + 重写转换规则 |
| `PdfBoxDocumentParser.java` | `PDF_DOCUMENT` → `DOCUMENT` |
| `MinerUDocumentParser.java` | `PDF_DOCUMENT` → `DOCUMENT` |
| `FileServiceImpl.java` | `PROCESSING` → `PARSING` |
| `FileStatusTest.java` | 重写为 25 个 v2 测试 |

## verify 输出

```
mvn clean compile → BUILD SUCCESS (155 files compiled)
mvn test -Dtest="FileStatusTest" → Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
```

## 6 维自查（R6.4）

```
✅ 沿用既有抽象 grep：
  - 策略模式：找到 KpMatchingStrategy/WeightCalculationStrategy/SubgraphPruningStrategy → 沿用
  - FileParser 接口：已有 → 扩展 priority() default 方法
  - FileStatus 枚举：已有 validateTransition 模式 → 扩展重写
  - 未引入新模式
```

## LESSONS 扫描（R1.8）

```
grep LESSONS.md: "FileParseType|FileStatus|FileParser" → 无命中
```

## 越界检查（R6.5）

```
✅ TASK write_files（3 项）：
  - application/file/parse/model/FileParseType.java（无实际改动）
  - infrastructure/mysql/file/entity/FileStatus.java ✅
  - application/file/parse/parser/FileParser.java ✅

⚠️ 编译兼容追加（4 项，为保持编译通过的必需改动）：
  - PdfBoxDocumentParser.java（PDF_DOCUMENT → DOCUMENT，一行）
  - MinerUDocumentParser.java（PDF_DOCUMENT → DOCUMENT，一行）
  - FileServiceImpl.java（PROCESSING → PARSING，两行）
  - FileStatusTest.java（重写测试匹配 v2）

→ 0 无关越界。追加文件均为枚举重命名的直接编译依赖。
```

## 未涉及

- 破坏性变更：无 ≥5 行删除（FileStatusTest 是重写，旧测试被完整替换）
- Schema 变更：T01 不涉及 DDL
- UI 变更：纯后端