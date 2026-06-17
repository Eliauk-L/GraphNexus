## 学生信息
- **姓名**：{{studentName}}
- **学号**：{{studentNo}}
- **班级**：{{className}}
- **学科**：{{subject}}

## 子图数据

以下是系统从知识图谱中剪枝出的与本次诊断相关的子图数据。

**剪枝参数**：弱掌握度阈值 = {{weakThreshold}}（低于此值视为薄弱），前置依赖最大跳数 = {{maxHops}}

{{subgraphText}}

{{mastersWarning}}

## 用户问题

{{userQuestion}}

---

请基于以上子图数据，按照系统提示词中的分析框架和格式要求，生成该学生的薄弱点诊断报告。