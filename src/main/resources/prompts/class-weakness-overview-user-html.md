## 班级信息
- **班级**：{{className}}
- **学生人数**：{{classSize}}
- **学科**：{{subject}}

## 子图数据

以下是系统从知识图谱中剪枝出的与本次诊断相关的班级聚合子图数据。

**剪枝参数**：弱掌握度阈值 = {{weakThreshold}}（低于此值视为薄弱），前置依赖最大跳数 = {{maxHops}}

{{subgraphText}}

{{mastersWarning}}

## 用户问题

{{userQuestion}}

---

请基于以上班级聚合子图数据，按照系统提示词中的分析框架和 HTML+SVG 格式要求，生成该班级的薄弱知识点概览报告。报告必须以 HTML 标签开头，包含至少一个 `<svg>` 图表元素。