# ADR-045: chat() 实体提取扩展 — className 字段 + 正则兜底

## Context

`class-weakness-overview` 需要 `QueryServiceImpl.chat()` 从自然语言问题中同时支持两种实体提取模式：

- **学生模式**（现有）：`"分析张三数学薄弱点"` → `{studentName: "张三", subject: "数学"}`
- **班级模式**（新增）：`"分析初三(1)班数学薄弱知识点"` → `{className: "初三(1)班", subject: "数学"}`

当前实现仅支持学生模式（`extractViaLlm` prompt 输出 `studentName/studentNo/subject`，`extractViaRegex` 匹配学生名 pattern）。

问题：如何最小化改动支持班级名提取，同时保持 LLM-first + regex fallback 模式？

## Decision

### D0 · 扩展现有 prompt 和正则，而非新建独立提取方法

**选择**：在 `extractViaLlm()` 的 system prompt JSON 输出格式中新增可选 `className` 字段；在 `extractViaRegex()` 中新增班级名 pattern。

**理由**：
- 与现有 LLM-first → regex fallback 模式完全一致（ADR-025 的 fallback 范式）
- 同一个 LLM 调用即可完成提取（不增加 LLM 调用次数）
- LLM 自行判断返回学生信息还是班级信息（二选一，互斥）
- 正则兜底同样在一个方法内完成，不拆分多个方法

**代价**：LLM prompt 变长（新增 className 字段说明 + few-shot 示例），但增量 < 100 tokens，可忽略。

### D1 · LLM prompt 扩展设计

**选择**：在现有实体提取 system prompt 中新增：

```
从用户问题中提取学生标识（姓名或学号）或班级名称，以及学科名称。
仅返回一个 JSON 对象：
- 如果是学生诊断：{"studentName":"...","studentNo":"...","className":null,"subject":"..."}
- 如果是班级概览：{"studentName":null,"studentNo":null,"className":"...","subject":"..."}
studentName/studentNo 和 className 互斥（至少提取一组），无法确定的字段设为 null。
班级名称通常包含"班"字，如"初三(1)班"、"高一3班"。
```

**理由**：
- `className` 与 `studentName/studentNo` 互斥的约束，与意图识别结果形成双重校验（意图说 CLASS_WEAKNESS_OVERVIEW 但实体提取出了 studentName → 矛盾，降级 fallback）
- 班级名格式提示（含"班"字）帮助 LLM 准确提取

### D2 · 正则兜底扩展

**选择**：在 `extractViaRegex()` 中新增班级名提取逻辑：

```java
// 班级名 pattern：可选汉字 + 年级 + 可选汉字 + 数字 + 班
Pattern.compile("([\\u4e00-\\u9fa5]{0,6}年级?[\\u4e00-\\u9fa5]?\\d+班)");
```

匹配示例：初三(1)班 / 九年级1班 / 高一3班 / 初一(12)班

**理由**：
- 班级名格式比学生名更固定（必然含"班"字 + 数字），正则准确率高
- 与现有 `extractStudentName` 方法并列，不互相干扰

### D3 · chat() 流程调整

**选择**：`chat()` 方法在意图识别后，根据意图类型选择调用不同的实体提取 prompt（或同一 prompt 的不同字段校验）：

```
chat():
  1. intent = recognizeIntent(question)
  2. entities = extractEntities(question, subjects)  // LLM 返回含 className 和 studentName
  3. if intent == CLASS_WEAKNESS_OVERVIEW:
       if entities.className == null → 降级正则提取 className
       if 仍为 null → throw A0019
     else (STUDENT_DIAGNOSIS):
       现有逻辑不变（studentName/studentNo 校验）
  4. 继续对应流程
```

**理由**：
- LLM 一次提取返回全部可能字段，由 intent 决定使用哪些字段
- intent 与提取字段的交叉校验增强鲁棒性（防止"意图是班级概览但提取出学生名"的矛盾情况）

## Consequences

- **正向**：LLM 调用次数不变（实体提取仍为 1 次），扩展成本极低
- **正向**：正则兜底对班级名（格式固定）准确率高，LLM 不可用时仍可正常工作
- **负向**：LLM prompt 变长，可能略微增加 token 消耗（增量 < 100 tokens）
- **负向**：班级名格式多样化（"初三(1)班" / "九年级1班" / "9年级1班" / "初三一班"），正则可能漏提中文数字格式。v1 通过 error message 中展示数据库班级名列表引导用户，v2 再做模糊匹配