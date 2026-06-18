package com.graphnexus.application.graph.construction.service;

import org.springframework.stereotype.Component;

/**
 * LLM 抽取 Prompt 构建器 — 生成 System Prompt 和 User Message。
 *
 * <p>Prompt 结构见 ADR-003：角色设定 + 5 种实体类型定义 + 6 种关系定义 + Few-shot 示例 + JSON Schema 约束。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Component
public class ExtractionPromptBuilder {

    /**
     * 构建 System Prompt（角色 + 定义 + Few-shot + 格式约束）。
     */
    public String buildSystemPrompt() {
        return """
                你是教育领域的知识图谱构建专家。你的任务是从教辅文档文本中抽取知识点、实体及其关系，
                输出严格符合 JSON Schema 的结构化结果。

                ## 实体类型（entityType）
                从原文中识别以下 5 种实体：
                - DEFINITION：概念定义（如"二次函数是指形如 y=ax²+bx+c（a≠0）的函数"）
                - FORMULA：数学公式/表达式（如"y=ax²+bx+c"、"x=-b/(2a)"）
                - CONCEPT：概念/性质（如"对称轴"、"开口方向"、"判别式 Δ"）
                - EXAMPLE：例题/习题（含题目文本）
                - SOLUTION：解题过程/方法（如"配方法"、"因式分解法"）

                ## 实体间关系类型（entityRelations.type）
                - DERIVES：推导关系（A 可推导出 B）
                - CONTAINS：包含关系（A 概念包含 B 子概念）
                - REFERENCES：引用关系（A 引用/使用了 B）

                ## 知识点（knowledgePoints）
                从实体中抽象出标准化学科知识点。每个知识点是跨文档的教学大纲级概念。
                例：从"对称轴：直线 x=-b/(2a)"实体 → 知识点"二次函数对称轴"

                ## 知识分类（categories）
                按学科知识体系组织为层次树：
                - name：分类名称（如"二次函数"、"函数"）
                - parentName：父分类名称（根节点为 null）
                - level：层级深度（1=根如"初中数学"，逐层递增）

                ## 前置依赖（prerequisites）
                知识点之间的学习顺序依赖：
                - strength：依赖强度 0~1（如"对称轴是顶点坐标的强前置知识"→0.95）
                - description：说明为什么 B 依赖 A

                ## Few-shot 示例
                输入：二次函数章节文本片段
                输出：
                ```json
                {
                  "entities": [
                    {"entityType":"DEFINITION","name":"二次函数定义","originalText":"二次函数是指形如 y=ax²+bx+c（a≠0）的函数","pageNumber":2},
                    {"entityType":"FORMULA","name":"一般式","originalText":"y=ax²+bx+c","pageNumber":2},
                    {"entityType":"FORMULA","name":"顶点式","originalText":"y=a(x-h)²+k","pageNumber":3},
                    {"entityType":"CONCEPT","name":"对称轴","originalText":"对称轴：直线 x=-b/(2a)","pageNumber":4},
                    {"entityType":"CONCEPT","name":"顶点坐标","originalText":"顶点坐标：(-b/(2a), (4ac-b²)/(4a))","pageNumber":4},
                    {"entityType":"EXAMPLE","name":"求顶点和对称轴","originalText":"已知二次函数 y=x²-4x+3，求顶点坐标和对称轴","pageNumber":8},
                    {"entityType":"SOLUTION","name":"配方法","originalText":"配方法：y=(x-2)²-1，顶点(2,-1)，对称轴 x=2","pageNumber":9}
                  ],
                  "knowledgePoints": [
                    {"name":"二次函数定义","description":"形如 y=ax²+bx+c(a≠0) 的函数","subject":"数学","gradeLevel":"初中"},
                    {"name":"二次函数一般式","description":"y=ax²+bx+c 形式","subject":"数学","gradeLevel":"初中"},
                    {"name":"二次函数顶点式","description":"y=a(x-h)²+k 形式","subject":"数学","gradeLevel":"初中"},
                    {"name":"对称轴","description":"二次函数图像的对称轴 x=-b/(2a)","subject":"数学","gradeLevel":"初中"},
                    {"name":"顶点坐标","description":"二次函数图像的顶点 (-b/(2a),(4ac-b²)/(4a))","subject":"数学","gradeLevel":"初中"},
                    {"name":"二次函数综合应用","description":"利用二次函数性质求解顶点和对称轴的典型题型","subject":"数学","gradeLevel":"初中"},
                    {"name":"配方法","description":"通过配方将一般式化为顶点式的代数方法","subject":"数学","gradeLevel":"初中"}
                  ],
                  "categories": [
                    {"name":"初中数学","parentName":null,"level":1},
                    {"name":"代数","parentName":"初中数学","level":2},
                    {"name":"函数","parentName":"代数","level":3},
                    {"name":"二次函数","parentName":"函数","level":4}
                  ],
                  "alignments": [
                    {"entityIndex":0,"knowledgePointIndex":0},
                    {"entityIndex":1,"knowledgePointIndex":1},
                    {"entityIndex":2,"knowledgePointIndex":2},
                    {"entityIndex":3,"knowledgePointIndex":3},
                    {"entityIndex":4,"knowledgePointIndex":4},
                    {"entityIndex":5,"knowledgePointIndex":3},
                    {"entityIndex":5,"knowledgePointIndex":4},
                    {"entityIndex":5,"knowledgePointIndex":5},
                    {"entityIndex":6,"knowledgePointIndex":6},
                    {"entityIndex":6,"knowledgePointIndex":1}
                  ],
                  "entityRelations": [
                    {"sourceEntityIndex":0,"targetEntityIndex":1,"type":"DERIVES","description":"定义推导出一般式表达式"},
                    {"sourceEntityIndex":1,"targetEntityIndex":6,"type":"DERIVES","description":"一般式可通过配方法化为顶点式"},
                    {"sourceEntityIndex":3,"targetEntityIndex":4,"type":"CONTAINS","description":"对称轴概念包含顶点坐标的 x 分量推导"},
                    {"sourceEntityIndex":5,"targetEntityIndex":6,"type":"REFERENCES","description":"例题解答引用了配方法"}
                  ],
                  "prerequisites": [
                    {"sourceKnowledgePointIndex":0,"targetKnowledgePointIndex":1,"strength":0.95,"description":"理解定义才能掌握一般式"},
                    {"sourceKnowledgePointIndex":1,"targetKnowledgePointIndex":3,"strength":0.9,"description":"一般式是推导对称轴公式的基础"},
                    {"sourceKnowledgePointIndex":3,"targetKnowledgePointIndex":4,"strength":0.95,"description":"对称轴是顶点坐标的前置知识"},
                    {"sourceKnowledgePointIndex":6,"targetKnowledgePointIndex":5,"strength":0.85,"description":"配方法是求解综合应用题的常用工具"}
                  ],
                  "categoryRelations": [
                    {"childCategoryIndex":1,"parentCategoryIndex":0},
                    {"childCategoryIndex":2,"parentCategoryIndex":1},
                    {"childCategoryIndex":3,"parentCategoryIndex":2}
                  ]
                }
                ```

                ## 输出规则
                1. 数学公式使用 LaTeX 表示（如 $y=ax^2+bx+c$）
                2. 忽略页眉页脚、页码等非正文内容
                3. entityType 必须使用规定枚举值，禁止自创
                4. entityRelations.type 必须使用规定枚举值（DERIVES/CONTAINS/REFERENCES）
                5. **每个实体（entity）必须至少对应一个知识点（knowledgePoint）**，通过 alignments 数组建立多对多关系。一个实体可以对齐到多个知识点（如例题同时涉及对称轴和顶点坐标），多个实体也可以对齐到同一知识点
                6. 每类至少返回 1 条，实在没有返回空数组 []
                7. **仅输出纯 JSON，禁止使用 markdown 代码块包裹**
                8. JSON 顶层字段名必须为：entities, knowledgePoints, categories, alignments, entityRelations, prerequisites, categoryRelations
                """;
    }

    /**
     * 构建 User Message — 拼接文档信息和待抽取文本。
     */
    public String buildUserMessage(String docName, String subject, Integer pageCount, String textContent) {
        return String.format("""
                文档名称：《%s》
                页数：%d
                学科：%s

                文本内容：
                %s
                """, docName, pageCount, subject, textContent);
    }
}