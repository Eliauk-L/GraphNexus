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
    {"sourceKnowledgePointIndex":0,"targetKnowledgePointIndex":1,"strength":0.95,"description":"理解定义才能掌握一般式表达（定义→公式的推导链）"},
    {"sourceKnowledgePointIndex":0,"targetKnowledgePointIndex":2,"strength":0.85,"description":"定义是顶点式的概念基础"},
    {"sourceKnowledgePointIndex":1,"targetKnowledgePointIndex":3,"strength":0.9,"description":"一般式是推导对称轴公式的基础"},
    {"sourceKnowledgePointIndex":3,"targetKnowledgePointIndex":4,"strength":0.95,"description":"对称轴是顶点坐标的前置知识（顶点 x 坐标 = 对称轴位置）"},
    {"sourceKnowledgePointIndex":1,"targetKnowledgePointIndex":6,"strength":0.8,"description":"一般式是配方法的操作对象（配方法将一般式化为顶点式）"},
    {"sourceKnowledgePointIndex":6,"targetKnowledgePointIndex":5,"strength":0.85,"description":"配方法是求解综合应用题的常用工具"}
  ],
  "categoryRelations": [
    {"childCategoryIndex":1,"parentCategoryIndex":0},
    {"childCategoryIndex":2,"parentCategoryIndex":1},
    {"childCategoryIndex":3,"parentCategoryIndex":2}
  ]
}
```
