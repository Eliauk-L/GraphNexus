package com.graphnexus.application.query.chat.service.impl;

import com.graphnexus.application.analysis.model.PrunedSubgraph;
import com.graphnexus.application.analysis.model.PruningRequest;
import com.graphnexus.application.query.chat.intent.IntentRecognitionService;
import com.graphnexus.application.query.chat.registry.PruningStrategyRegistry;
import com.graphnexus.api.query.dto.history.HistoryQueryRequest;
import com.graphnexus.api.query.dto.history.HistoryRecordVO;
import com.graphnexus.common.LlmGateway;
import com.graphnexus.application.query.chat.config.QueryProperties;
import com.graphnexus.application.query.chat.model.QueryIntent;
import com.graphnexus.application.query.chat.model.QueryResultBO;
import com.graphnexus.application.query.chat.model.QueryResultBO.TokenUsage;
import com.graphnexus.application.query.prompt.service.PromptTemplateService;
import com.graphnexus.application.query.chat.service.QueryService;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.common.PageResult;
import com.graphnexus.application.graph.construction.model.GraphNodeData;
import com.graphnexus.infrastructure.neo4j.node.StudentNode;
import com.graphnexus.infrastructure.neo4j.repository.QueryGraphRepository;
import com.graphnexus.infrastructure.mysql.query.entity.QueryTaskDO;
import com.graphnexus.infrastructure.mysql.query.repository.QueryTaskRepository;
import com.graphnexus.infrastructure.mysql.query.entity.QueryTaskStatus;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能问答服务实现 — 编排意图识别、图剪枝、Prompt 组装、LLM 调用的完整链路。
 *
 * <p>核心流程见 DESIGN §2.1。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryServiceImpl implements QueryService {

    private final QueryGraphRepository queryGraphRepository;
    private final ExamRecordRepository examRecordRepository;
    private final IntentRecognitionService intentRecognitionService;
    private final PruningStrategyRegistry pruningStrategyRegistry;
    private final PromptTemplateService promptTemplateService;
    private final LlmGateway llmGateway;
    private final QueryTaskRepository queryTaskRepository;
    private final QueryProperties queryProperties;
    private final ObjectMapper objectMapper;

    // ======================== 意图识别 ========================

    QueryIntent recognizeIntent(String question) {
        return intentRecognitionService.recognize(question);
    }

    // ======================== 公共 API ========================

    @Override
    public QueryResultBO ask(String question, String studentName, String studentNo, String subject) {
        long startTime = System.currentTimeMillis();
        String taskId = UUID.randomUUID().toString();
        log.info("同步问答开始: taskId={}, question={}, studentName={}, studentNo={}, subject={}",
                taskId, question, studentName, studentNo, subject);

        try {
            // 1. 意图识别
            QueryIntent intent = recognizeIntent(question);

            // 2. 实体解析
            StudentNode student = resolveStudent(studentName, studentNo);

            // 3. 图剪枝
            Map<String, Object> params = Map.of(
                    "weakThreshold", queryProperties.getPruning().getWeakThreshold(),
                    "maxHops", (double) queryProperties.getPruning().getMaxPrerequisiteHops()
            );
            PruningRequest pruningRequest = new PruningRequest(
                    intent.name(), student.getStudentNo(), subject, params);
            PrunedSubgraph subgraph = pruningStrategyRegistry.get(intent.name()).prune(pruningRequest);

            if (subgraph.nodes().isEmpty() && "STUDENT_NOT_FOUND".equals(subgraph.meta().strategy())) {
                throw new BusinessException(ErrorCode.A0006, "未找到学生: " + studentName);
            }

            // 4. 子图序列化
            String subgraphText = serializeSubgraph(subgraph, student);

            // 5. Token 预算控制
            int maxInputTokens = queryProperties.getTokenBudget().getMaxInputTokens();
            int charsPerToken = queryProperties.getTokenBudget().getCharsPerToken();
            boolean truncated = false;
            List<String> truncatedNames = Collections.emptyList();
            if (subgraphText.length() > maxInputTokens * charsPerToken) {
                var result = truncateSubgraph(subgraphText, maxInputTokens * charsPerToken);
                subgraphText = result.text();
                truncated = result.truncated();
                truncatedNames = result.truncatedNames();
            }
            int estimatedTokens = subgraphText.length() / charsPerToken;

            // 6. Prompt 组装（格式感知）
            String outputFormat = queryProperties.getOutput().getFormat();
            Map<String, String> templateVars = new HashMap<>();
            templateVars.put("studentName", student.getName() != null ? student.getName() : "");
            templateVars.put("studentNo", student.getStudentNo());
            templateVars.put("className", student.getClassName() != null ? student.getClassName() : "");
            templateVars.put("subject", subject);
            templateVars.put("subgraphText", subgraphText);
            templateVars.put("userQuestion", question);
            templateVars.put("weakThreshold", String.valueOf(queryProperties.getPruning().getWeakThreshold()));
            templateVars.put("maxHops", String.valueOf(queryProperties.getPruning().getMaxPrerequisiteHops()));
            templateVars.put("mastersAvailable", String.valueOf(subgraph.meta().mastersAvailable()));

            var promptPair = promptTemplateService.buildPrompt(intent, templateVars, outputFormat);

            // 7. LLM 调用（含重试 + 按格式校验）
            String answer = callLlmWithRetry(promptPair.systemPrompt(), promptPair.userMessage(), outputFormat);

            // 8. 持久化
            long elapsedMs = System.currentTimeMillis() - startTime;
            persistTask(taskId, question, studentName, studentNo, subject, intent,
                    QueryTaskStatus.COMPLETED, answer, subgraph, estimatedTokens,
                    (int) (promptPair.systemPrompt().length() / charsPerToken + estimatedTokens),
                    estimatedTokens, null, elapsedMs);

            return new QueryResultBO(taskId, "COMPLETED", question, intent.name(),
                    answer, outputFormat, new TokenUsage(subgraph.meta().totalNodes(), subgraph.meta().totalEdges(),
                    estimatedTokens, null, null), null, LocalDateTime.now(), LocalDateTime.now());

        } catch (BusinessException e) {
            log.error("同步问答失败: taskId={}, {}", taskId, e.getMessage());
            long elapsedMs = System.currentTimeMillis() - startTime;
            persistTask(taskId, question, studentName, studentNo, subject, null,
                    QueryTaskStatus.FAILED, null, null, 0, 0, 0, e.getMessage(), elapsedMs);
            throw e;
        } catch (Exception e) {
            log.error("同步问答异常: taskId={}", taskId, e);
            long elapsedMs = System.currentTimeMillis() - startTime;
            persistTask(taskId, question, studentName, studentNo, subject, null,
                    QueryTaskStatus.FAILED, null, null, 0, 0, 0,
                    e.getMessage() != null ? e.getMessage() : "未知异常", elapsedMs);
            throw new BusinessException(ErrorCode.C0001, "问答失败: " + e.getMessage());
        }
    }

    @Override
    public String askAsync(String question, String studentName, String studentNo, String subject) {
        String taskId = UUID.randomUUID().toString();
        persistTask(taskId, question, studentName, studentNo, subject, null,
                QueryTaskStatus.PENDING, null, null, 0, 0, 0, null, 0L);
        log.info("异步问答已提交: taskId={}", taskId);
        // 触发异步执行
        executeAsync(taskId, question, studentName, studentNo, subject);
        return taskId;
    }

    @Override
    public QueryResultBO getResult(String taskId) {
        QueryTaskDO task = queryTaskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0021, "问答任务不存在: " + taskId));

        TokenUsage tokenUsage = parseTokenUsage(task.getTokenUsageJson());
        return new QueryResultBO(
                task.getTaskId(), task.getStatus().name(), task.getQuestion(),
                task.getIntent(), task.getAnswer(), null, tokenUsage,
                task.getErrorMessage(), task.getCreateTime(), task.getUpdateTime()
        );
    }

    @Override
    public PrunedSubgraph getSubgraph(String taskId) {
        QueryTaskDO task = queryTaskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0021, "问答任务不存在: " + taskId));

        if (task.getStatus() != QueryTaskStatus.COMPLETED) {
            return new PrunedSubgraph(Collections.emptyList(), Collections.emptyList(),
                    new PrunedSubgraph.PruningMeta("NOT_READY", false, 0, 0, 0, 0, false, Collections.emptyList()));
        }
        try {
            return objectMapper.readValue(task.getSubgraphJson(), PrunedSubgraph.class);
        } catch (Exception e) {
            log.error("反序列化 subgraph_json 失败: taskId={}", taskId, e);
            return new PrunedSubgraph(Collections.emptyList(), Collections.emptyList(),
                    new PrunedSubgraph.PruningMeta("DESERIALIZE_ERROR", false, 0, 0, 0, 0, false, Collections.emptyList()));
        }
    }

    // ======================== 异步执行 ========================

    @Async("queryAsyncExecutor")
    protected void executeAsync(String taskId, String question, String studentName, String studentNo, String subject) {
        try {
            updateTaskStatus(taskId, QueryTaskStatus.PROCESSING);
            // 异步模式直接委托给 ask() 的逻辑，但 taskId 已预生成
            QueryResultBO result = askInternal(taskId, question, studentName, studentNo, subject);
            log.info("异步问答完成: taskId={}, status={}", taskId, result.status());
        } catch (Exception e) {
            log.error("异步问答失败: taskId={}", taskId, e);
            String errorMsg = e instanceof BusinessException ? e.getMessage() : ("异步执行异常: " + e.getMessage());
            queryTaskRepository.findByTaskId(taskId).ifPresent(task -> {
                task.setStatus(QueryTaskStatus.FAILED);
                task.setErrorMessage(errorMsg);
                task.setUpdateTime(LocalDateTime.now());
                queryTaskRepository.save(task);
            });
        }
    }

    private QueryResultBO askInternal(String taskId, String question, String studentName, String studentNo, String subject) {
        // 与 ask() 几乎相同，但复用预生成的 taskId
        long startTime = System.currentTimeMillis();
        try {
            QueryIntent intent = recognizeIntent(question);
            StudentNode student = resolveStudent(studentName, studentNo);
            Map<String, Object> params = Map.of(
                    "weakThreshold", queryProperties.getPruning().getWeakThreshold(),
                    "maxHops", (double) queryProperties.getPruning().getMaxPrerequisiteHops()
            );
            PruningRequest pruningRequest = new PruningRequest(intent.name(), student.getStudentNo(), subject, params);
            PrunedSubgraph subgraph = pruningStrategyRegistry.get(intent.name()).prune(pruningRequest);
            String subgraphText = serializeSubgraph(subgraph, student);

            int charsPerToken = queryProperties.getTokenBudget().getCharsPerToken();
            String outputFormat = queryProperties.getOutput().getFormat();
            Map<String, String> templateVars = buildTemplateVars(question, student, subject, subgraphText, subgraph);
            var promptPair = promptTemplateService.buildPrompt(intent, templateVars, outputFormat);
            String answer = callLlmWithRetry(promptPair.systemPrompt(), promptPair.userMessage(), outputFormat);

            long elapsedMs = System.currentTimeMillis() - startTime;
            updateCompletedTask(taskId, answer, subgraph, charsPerToken, elapsedMs);
            return new QueryResultBO(taskId, "COMPLETED", question, intent.name(),
                    answer, outputFormat, null, null, LocalDateTime.now(), LocalDateTime.now());
        } catch (Exception e) {
            throw e;
        }
    }

    // ======================== 智能对话（chat） ========================

    /** 学科列表缓存（从 Neo4j KnowledgePoint 查询，volatile 保证可见性） */
    private volatile List<String> cachedSubjects;

    /** LLM 实体提取结果 */
    private record ExtractedEntities(String studentName, String studentNo, String subject) {}

    @Override
    public QueryResultBO chat(String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(ErrorCode.A0002, "问题不能为空");
        }

        List<String> subjects = getKnownSubjects();
        if (subjects.isEmpty()) {
            throw new BusinessException(ErrorCode.A0019,
                    "系统中暂无学科数据，请先导入考试记录或文档图谱");
        }

        // 1. 先意图识别（避免非目标意图浪费后续 LLM 实体提取调用）
        QueryIntent intent = recognizeIntent(question);

        // 2. LLM 实体提取（仅在意图可识别时进行）
        ExtractedEntities entities = extractViaLlm(question, subjects);

        // 3. LLM 失败 → 正则兜底
        if (entities == null) {
            log.info("LLM 实体提取失败，降级为规则提取");
            entities = extractViaRegex(question, subjects);
        }

        if (entities == null || entities.subject() == null
                || (entities.studentName() == null && entities.studentNo() == null)) {
            throw new BusinessException(ErrorCode.A0019,
                    "无法从问题中识别学生（姓名或学号）和学科，当前系统已有学科：" + String.join("、", subjects)
                    + "。示例：分析学生张三的数学薄弱点 或 查询S2024001的数学掌握情况");
        }

        // 校验提取的学科在系统中是否存在
        if (!subjects.contains(entities.subject())) {
            throw new BusinessException(ErrorCode.A0019,
                    "学科\"" + entities.subject() + "\"在系统中暂无数据，当前已有学科："
                    + String.join("、", subjects)
                    + "。请先导入对应学科的考试记录或文档图谱");
        }

        log.info("chat 完成: intent={}, studentName={}, studentNo={}, subject={}",
                intent, entities.studentName(), entities.studentNo(), entities.subject());
        // 使用已识别的意图，避免 ask() 内部重复识别
        return askWithIntent(question, entities.studentName(), entities.studentNo(),
                entities.subject(), intent);
    }

    /**
     * 使用预识别意图执行问答，跳过 ask() 内部的意图识别步骤。
     */
    private QueryResultBO askWithIntent(String question, String studentName,
                                        String studentNo, String subject, QueryIntent intent) {
        long startTime = System.currentTimeMillis();
        String taskId = UUID.randomUUID().toString();
        log.info("同步问答开始(预识别意图): taskId={}, intent={}, studentName={}, subject={}",
                taskId, intent, studentName, subject);

        try {
            // 实体解析（跳过意图识别）
            StudentNode student = resolveStudent(studentName, studentNo);

            // 图剪枝
            Map<String, Object> params = Map.of(
                    "weakThreshold", queryProperties.getPruning().getWeakThreshold(),
                    "maxHops", (double) queryProperties.getPruning().getMaxPrerequisiteHops()
            );
            PruningRequest pruningRequest = new PruningRequest(
                    intent.name(), student.getStudentNo(), subject, params);
            PrunedSubgraph subgraph = pruningStrategyRegistry.get(intent.name()).prune(pruningRequest);

            if (subgraph.nodes().isEmpty() && "STUDENT_NOT_FOUND".equals(subgraph.meta().strategy())) {
                throw new BusinessException(ErrorCode.A0006, "未找到学生: " + studentName);
            }

            // 子图序列化
            String subgraphText = serializeSubgraph(subgraph, student);

            // Token 预算控制
            int maxInputTokens = queryProperties.getTokenBudget().getMaxInputTokens();
            int charsPerToken = queryProperties.getTokenBudget().getCharsPerToken();
            if (subgraphText.length() > maxInputTokens * charsPerToken) {
                var result = truncateSubgraph(subgraphText, maxInputTokens * charsPerToken);
                subgraphText = result.text();
            }
            int estimatedTokens = subgraphText.length() / charsPerToken;

            // Prompt 组装（格式感知）
            String outputFormat = queryProperties.getOutput().getFormat();
            Map<String, String> templateVars = buildTemplateVars(question, student, subject, subgraphText, subgraph);
            var promptPair = promptTemplateService.buildPrompt(intent, templateVars, outputFormat);

            // LLM 调用
            String answer = callLlmWithRetry(promptPair.systemPrompt(), promptPair.userMessage(), outputFormat);

            // 持久化
            long elapsedMs = System.currentTimeMillis() - startTime;
            persistTask(taskId, question, studentName, studentNo, subject, intent,
                    QueryTaskStatus.COMPLETED, answer, subgraph, estimatedTokens,
                    (int) (promptPair.systemPrompt().length() / charsPerToken + estimatedTokens),
                    estimatedTokens, null, elapsedMs);

            return new QueryResultBO(taskId, "COMPLETED", question, intent.name(),
                    answer, outputFormat, new TokenUsage(subgraph.meta().totalNodes(), subgraph.meta().totalEdges(),
                    estimatedTokens, null, null), null, LocalDateTime.now(), LocalDateTime.now());

        } catch (BusinessException e) {
            log.error("同步问答失败: taskId={}, {}", taskId, e.getMessage());
            long elapsedMs = System.currentTimeMillis() - startTime;
            persistTask(taskId, question, studentName, studentNo, subject, null,
                    QueryTaskStatus.FAILED, null, null, 0, 0, 0, e.getMessage(), elapsedMs);
            throw e;
        } catch (Exception e) {
            log.error("同步问答异常: taskId={}", taskId, e);
            long elapsedMs = System.currentTimeMillis() - startTime;
            persistTask(taskId, question, studentName, studentNo, subject, null,
                    QueryTaskStatus.FAILED, null, null, 0, 0, 0,
                    e.getMessage() != null ? e.getMessage() : "未知异常", elapsedMs);
            throw new BusinessException(ErrorCode.C0001, "问答失败: " + e.getMessage());
        }
    }

    /** LLM 提取：调用大模型从问题中抽取学生姓名/学号和学科，返回 JSON */
    private ExtractedEntities extractViaLlm(String question, List<String> subjects) {
        String systemPrompt = """
                你是一个信息提取助手。从用户问题中提取学生标识（姓名或学号）和学科名称。
                仅返回一个 JSON 对象，格式为 {"studentName":"...","studentNo":"...","subject":"..."}，
                不要返回其他内容。studentName 和 studentNo 至少提取一个，无法确定的字段设为 null。
                学号通常是字母+数字组合（如 S2024001），姓名通常是2-4个中文字符。
                学科必须是以下之一：%s
                """.formatted(String.join("、", subjects));

        try {
            String response = llmGateway.chat(systemPrompt, question);
            if (response == null || response.isBlank()) return null;

            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json?\\s*", "").replaceAll("```\\s*$", "").trim();
            }
            if (json.contains("\n")) {
                json = json.substring(0, json.indexOf('\n')).trim();
            }

            var node = objectMapper.readTree(json);
            String studentName = node.has("studentName") && !node.get("studentName").isNull()
                    ? node.get("studentName").asText() : null;
            String studentNo = node.has("studentNo") && !node.get("studentNo").isNull()
                    ? node.get("studentNo").asText() : null;
            String subject = node.has("subject") && !node.get("subject").isNull()
                    ? node.get("subject").asText() : null;

            if (subject != null && subjects.contains(subject)
                    && (studentName != null || studentNo != null)) {
                log.debug("LLM 实体提取成功: studentName={}, studentNo={}, subject={}",
                        studentName, studentNo, subject);
                return new ExtractedEntities(studentName, studentNo, subject);
            }
        } catch (Exception e) {
            log.warn("LLM 实体提取异常，将降级为规则提取: {}", e.getMessage());
        }
        return null;
    }

    /** 正则提取：手动规则兜底 */
    private ExtractedEntities extractViaRegex(String question, List<String> subjects) {
        List<String> sorted = subjects.stream()
                .sorted((a, b) -> b.length() - a.length()).toList();
        String subjectOr = String.join("|", sorted);
        java.util.regex.Pattern subjectRegex = java.util.regex.Pattern.compile("(" + subjectOr + ")");
        String boundaryTokens = subjectOr + "|薄弱|掌握|诊断|分析|的";

        String subject = extractSubject(question, subjectRegex);
        String studentName = extractStudentName(question, boundaryTokens, subjects);
        String studentNo = extractStudentNo(question);

        if (subject == null || (studentName == null && studentNo == null)) return null;
        return new ExtractedEntities(studentName, studentNo, subject);
    }

    /** 正则提取学号（字母+数字组合，如 S2024001、2024001） */
    String extractStudentNo(String question) {
        var matcher = java.util.regex.Pattern.compile("\\b([A-Za-z]?\\d{5,12})\\b").matcher(question);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 从 MySQL exam_record 获取学科列表（带缓存） */
    List<String> getKnownSubjects() {
        if (cachedSubjects == null || cachedSubjects.isEmpty()) {
            synchronized (this) {
                if (cachedSubjects == null || cachedSubjects.isEmpty()) {
                    cachedSubjects = examRecordRepository.findDistinctSubjectBySubjectIsNotNullOrderBySubject();
                    log.info("从 MySQL exam_record 加载学科列表: {}", cachedSubjects);
                }
            }
        }
        return cachedSubjects;
    }

    String extractSubject(String question, java.util.regex.Pattern pattern) {
        var matcher = pattern.matcher(question);
        return matcher.find() ? matcher.group(1) : null;
    }

    String extractStudentName(String question, String boundaryTokens, List<String> subjects) {
        java.util.regex.Pattern namePattern = java.util.regex.Pattern.compile(
                "学生(.+?)(?:" + boundaryTokens + ")");
        var matcher = namePattern.matcher(question);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            if (name.length() >= 2 && name.length() <= 10 && !subjects.contains(name)) {
                return name;
            }
        }
        return null;
    }

    // ======================== 实体解析 ========================

    StudentNode resolveStudent(String studentName, String studentNo) {
        // 1. MySQL 查找学生身份（exam_record 是学生数据的权威来源）
        if (studentNo != null && !studentNo.isBlank()) {
            return resolveByStudentNo(studentNo);
        }
        if (studentName != null && !studentName.isBlank()) {
            return resolveByStudentName(studentName);
        }
        throw new BusinessException(ErrorCode.A0002, "请提供学生姓名或学号");
    }

    /** 按学号从 MySQL 精确查找，再从 Neo4j 获取图节点 */
    private StudentNode resolveByStudentNo(String studentNo) {
        List<Object[]> mysqlRows = examRecordRepository.findStudentByNo(studentNo);
        if (mysqlRows.isEmpty()) {
            throw new BusinessException(ErrorCode.A0006, "未找到学生，学号: " + studentNo);
        }
        Object[] row = mysqlRows.get(0);
        String name = (String) row[1];
        String className = (String) row[2];

        // 从 Neo4j 获取 StudentNode（用于后续 MASTERS 剪枝）
        var neo4jRow = queryGraphRepository.findStudentByNo(studentNo);
        if (neo4jRow.isPresent()) {
            return buildStudentNode(neo4jRow.get());
        }
        // Neo4j 中不存在（融合未执行或 CSV 未导入图谱），构建最小 StudentNode
        return new StudentNode(studentNo, name, className, null);
    }

    /** 按姓名从 MySQL 模糊查找，再从 Neo4j 获取图节点 */
    private StudentNode resolveByStudentName(String studentName) {
        List<Object[]> mysqlRows = examRecordRepository.findStudentByName(studentName);
        if (mysqlRows.isEmpty()) {
            throw new BusinessException(ErrorCode.A0006, "未找到学生: " + studentName);
        }
        if (mysqlRows.size() > 1) {
            // 检查是否所有匹配行都是同一个 studentNo（同一人多次考试）
            long distinctCount = mysqlRows.stream().map(r -> (String) r[0]).distinct().count();
            if (distinctCount > 1) {
                List<Map<String, String>> candidates = mysqlRows.stream()
                        .map(r -> Map.of(
                                "studentNo", (String) r[0],
                                "name", (String) r[1],
                                "className", (String) r[2]
                        ))
                        .distinct()
                        .collect(Collectors.toList());
                try {
                    throw new BusinessException(ErrorCode.A0020,
                            "存在多个同名或相似学生: " + objectMapper.writeValueAsString(candidates));
                } catch (JsonProcessingException e) {
                    throw new BusinessException(ErrorCode.A0020, "存在多个同名或相似学生");
                }
            }
        }
        // 取第一个匹配的 studentNo
        Object[] row = mysqlRows.get(0);
        String studentNo = (String) row[0];
        String name = (String) row[1];
        String className = (String) row[2];

        var neo4jRow = queryGraphRepository.findStudentByNo(studentNo);
        if (neo4jRow.isPresent()) {
            return buildStudentNode(neo4jRow.get());
        }
        return new StudentNode(studentNo, name, className, null);
    }

    // ======================== 子图序列化 ========================

    String serializeSubgraph(PrunedSubgraph subgraph, StudentNode student) {
        StringBuilder sb = new StringBuilder();

        // 学生信息
        sb.append("## 学生信息\n");
        sb.append("- 姓名: ").append(student.getName()).append("\n");
        sb.append("- 学号: ").append(student.getStudentNo()).append("\n");
        sb.append("- 班级: ").append(student.getClassName() != null ? student.getClassName() : "未知").append("\n");
        sb.append("- 年级: ").append(student.getGrade() != null ? student.getGrade() : "未知").append("\n\n");

        // 分类节点
        var nodeMap = subgraph.nodes().stream()
                .collect(Collectors.toMap(GraphNodeData::id, n -> n, (a, b) -> a));
        var studentNode = subgraph.nodes().stream()
                .filter(n -> "Student".equals(n.nodeType())).findFirst();
        var kpNodes = subgraph.nodes().stream()
                .filter(n -> !"Student".equals(n.nodeType())).collect(Collectors.toList());

        // 薄弱知识点
        var mastersEdges = subgraph.edges().stream()
                .filter(e -> "MASTERS".equals(e.edgeType())).collect(Collectors.toList());
        var prereqEdges = subgraph.edges().stream()
                .filter(e -> "PREREQUISITE_OF".equals(e.edgeType())).collect(Collectors.toList());

        if (!mastersEdges.isEmpty()) {
            sb.append("## 知识点掌握度\n\n");
            // 按 weight 升序排列（越弱越靠前）
            mastersEdges.sort(Comparator.comparingDouble(e -> e.weight() != null ? e.weight() : 1.0));
            for (var edge : mastersEdges) {
                var kpNode = nodeMap.get(edge.targetNodeId());
                String kpName = kpNode != null ? extractKpName(kpNode) : edge.targetNodeId();
                double weight = edge.weight() != null ? edge.weight() : 0;
                int pct = (int) (weight * 100);
                String level = weight < 0.4 ? "严重薄弱" : weight < 0.6 ? "中等薄弱" : "已掌握";
                sb.append("- **").append(kpName).append("** — 掌握度：").append(pct)
                        .append("%（").append(level).append("）\n");
            }
            sb.append("\n");
        }

        // 前置依赖关系
        if (!prereqEdges.isEmpty()) {
            sb.append("## 前置依赖关系\n\n");
            for (var edge : prereqEdges) {
                var fromNode = nodeMap.get(edge.sourceNodeId());
                var toNode = nodeMap.get(edge.targetNodeId());
                String fromName = fromNode != null ? extractKpName(fromNode) : edge.sourceNodeId();
                String toName = toNode != null ? extractKpName(toNode) : edge.targetNodeId();
                double strength = edge.weight() != null ? edge.weight() : 0;
                sb.append("- **").append(fromName).append("** → **").append(toName)
                        .append("**（依赖强度: ").append(String.format("%.2f", strength)).append("）\n");
            }
            sb.append("\n");
        }

        // MASTERS 数据质量标记
        if (!subgraph.meta().mastersAvailable()) {
            sb.append("⚠️ 以上掌握度为原始考试得分率（融合数据不可用），未做时间衰减加权。\n\n");
        }
        return sb.toString();
    }

    // ======================== Token 预算控制 ========================

    record TruncationResult(String text, boolean truncated, List<String> truncatedNames) {}

    TruncationResult truncateSubgraph(String text, int maxChars) {
        // 找到最后一个完整的知识点条目处截断
        if (text.length() <= maxChars) {
            return new TruncationResult(text, false, Collections.emptyList());
        }
        // 在 maxChars 位置向前找最近的换行
        int cutPos = text.lastIndexOf('\n', maxChars);
        if (cutPos < maxChars / 2) {
            cutPos = maxChars; // fallback: 硬截断
        }
        String truncated = text.substring(0, cutPos);
        String remaining = text.substring(cutPos);

        // 提取被截断的知识点名称
        List<String> names = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\*\\*(.+?)\\*\\*")
                .matcher(remaining);
        while (m.find() && names.size() < 10) {
            names.add(m.group(1));
        }

        truncated += "\n\n⚠️ 因长度限制，以下 " + names.size() + " 个知识点已省略："
                + String.join(", ", names) + "\n";
        return new TruncationResult(truncated, true, names);
    }

    // ======================== LLM 调用 + 重试 ========================

    String callLlmWithRetry(String systemPrompt, String userMessage, String format) {
        int maxRetries = queryProperties.getRetry().getMaxRetries();
        long retryDelay = queryProperties.getRetry().getRetryDelayMs();
        boolean isMarkdown = "markdown".equals(format);
        String lastResponse = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String currentSystemPrompt = systemPrompt;
                if (attempt > 0) {
                    String hint = isMarkdown
                            ? "请务必以 ## 标题开头，直接开始报告正文，不要有任何前导语。确保包含至少一个列表（- 或 1. ）。"
                            : "请务必以 HTML 标签开头（如 <h2>），包含至少一个 <svg> 元素。禁止 Markdown 标记和前导语。";
                    currentSystemPrompt = systemPrompt + "\n\n【重要】上次输出格式不正确。" + hint;
                }

                String response = llmGateway.chat(currentSystemPrompt, userMessage);
                lastResponse = response;

                boolean valid = isMarkdown
                        ? validateMarkdownResponse(response)
                        : validateHtmlSvgResponse(response);

                if (valid) return response;

                log.warn("LLM 格式校验未通过 (attempt {}/{}), retry after {}ms",
                        attempt + 1, maxRetries + 1, retryDelay);

                if (attempt < maxRetries) {
                    Thread.sleep(retryDelay);
                }
            } catch (BusinessException e) {
                if (attempt >= maxRetries) throw e;
                log.warn("LLM 调用失败 (attempt {}/{}): {}", attempt + 1, maxRetries + 1, e.getMessage());
                try { Thread.sleep(retryDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.warn("LLM 格式校验重试耗尽，降级返回原始文本");
        return lastResponse != null ? lastResponse : "";
    }

    /** Markdown 格式校验（保留原有逻辑） */
    boolean validateMarkdownResponse(String response) {
        if (response == null || response.isBlank()) return false;
        String trimmed = response.trim();
        if (!trimmed.startsWith("#")) return false;
        if (!trimmed.contains("- ") && !trimmed.matches(".*\\d\\.\\s.*")) return false;
        String lower = trimmed.substring(0, Math.min(100, trimmed.length()));
        return !lower.contains("根据提供") && !lower.contains("以下是") && !lower.contains("根据子图");
    }

    /** HTML+SVG 格式校验（新增） */
    boolean validateHtmlSvgResponse(String response) {
        if (response == null || response.isBlank()) return false;
        String trimmed = response.trim();
        // 必须以 HTML 标签开头
        if (!trimmed.startsWith("<")) return false;
        // 必须包含至少 1 个 <svg> 元素
        String lower = trimmed.toLowerCase();
        if (!lower.contains("<svg")) return false;
        // SVG 必须含 xmlns
        if (lower.contains("<svg") && !lower.contains("xmlns")) return false;
        // 不含 Markdown 标记
        if (trimmed.startsWith("##") || trimmed.startsWith("# ")) return false;
        // 不含常见前导语
        String first100 = lower.substring(0, Math.min(100, lower.length()));
        return !first100.contains("根据提供") && !first100.contains("以下是");
    }

    // ======================== 持久化辅助 ========================

    @Transactional
    void persistTask(String taskId, String question, String studentName, String studentNo,
                     String subject, QueryIntent intent, QueryTaskStatus status, String answer,
                     PrunedSubgraph subgraph, int estimatedTokens, int promptTokens, int completionTokens,
                     String errorMessage, long elapsedMs) {
        QueryTaskDO task = QueryTaskDO.builder()
                .taskId(taskId)
                .question(question)
                .studentName(studentName)
                .studentNo(studentNo)
                .subject(subject)
                .intent(intent != null ? intent.name() : null)
                .status(status)
                .answer(answer)
                .subgraphJson(subgraph != null ? toJson(subgraph) : null)
                .tokenUsageJson(buildTokenUsageJson(subgraph, estimatedTokens, promptTokens, completionTokens))
                .errorMessage(errorMessage)
                .retryCount(0)
                .elapsedMs(elapsedMs)
                .build();
        queryTaskRepository.save(task);
    }

    @Transactional
    void updateTaskStatus(String taskId, QueryTaskStatus status) {
        queryTaskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus(status);
            task.setUpdateTime(LocalDateTime.now());
            queryTaskRepository.save(task);
        });
    }

    @Transactional
    void updateCompletedTask(String taskId, String answer, PrunedSubgraph subgraph,
                             int charsPerToken, long elapsedMs) {
        queryTaskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus(QueryTaskStatus.COMPLETED);
            task.setAnswer(answer);
            task.setSubgraphJson(toJson(subgraph));
            task.setTokenUsageJson(buildTokenUsageJson(subgraph,
                    answer.length() / charsPerToken, 0, 0));
            task.setElapsedMs(elapsedMs);
            task.setUpdateTime(LocalDateTime.now());
            queryTaskRepository.save(task);
        });
    }

    // ======================== 工具方法 ========================

    Map<String, String> buildTemplateVars(String question, StudentNode student, String subject,
                                          String subgraphText, PrunedSubgraph subgraph) {
        Map<String, String> vars = new HashMap<>();
        vars.put("studentName", student.getName() != null ? student.getName() : "");
        vars.put("studentNo", student.getStudentNo());
        vars.put("className", student.getClassName() != null ? student.getClassName() : "");
        vars.put("subject", subject);
        vars.put("subgraphText", subgraphText);
        vars.put("userQuestion", question);
        vars.put("weakThreshold", String.valueOf(queryProperties.getPruning().getWeakThreshold()));
        vars.put("maxHops", String.valueOf(queryProperties.getPruning().getMaxPrerequisiteHops()));
        vars.put("mastersAvailable", String.valueOf(subgraph.meta().mastersAvailable()));
        return vars;
    }

    StudentNode buildStudentNode(Map<String, Object> row) {
        StudentNode node = new StudentNode(
                (String) row.get("studentNo"),
                (String) row.get("name"),
                (String) row.get("className"),
                (String) row.get("grade")
        );
        // Neo4j Cypher 返回的 s.id 是节点内部 ID（UUID），必须设置
        Object id = row.get("id");
        if (id != null) {
            node.setId(id.toString());
        }
        return node;
    }

    String extractKpName(GraphNodeData node) {
        // 优先使用 GraphNodeData 顶层 label 字段（从原始节点推导的显示名）
        if (node.label() != null && !node.label().isBlank()) return node.label();
        // 其次从 properties 中查找
        if (node.properties() != null) {
            Object name = node.properties().get("name");
            if (name != null && !name.toString().isBlank()) return name.toString();
        }
        // 兜底
        String id = node.id();
        return id != null && id.length() > 8 ? id.substring(0, 8) + "..." : (id != null ? id : "未知知识点");
    }

    TokenUsage parseTokenUsage(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, TokenUsage.class);
        } catch (Exception e) {
            return null;
        }
    }

    String buildTokenUsageJson(PrunedSubgraph subgraph, int estimatedTokens, int promptTokens, int completionTokens) {
        try {
            Map<String, Object> usage = new HashMap<>();
            usage.put("prunedNodes", subgraph != null ? subgraph.meta().totalNodes() : 0);
            usage.put("prunedEdges", subgraph != null ? subgraph.meta().totalEdges() : 0);
            usage.put("estimatedTokens", estimatedTokens);
            usage.put("promptTokens", promptTokens);
            usage.put("completionTokens", completionTokens);
            return objectMapper.writeValueAsString(usage);
        } catch (Exception e) {
            return "{}";
        }
    }

    String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ======================== 历史查询与导出 ========================

    @Override
    public PageResult<HistoryRecordVO> queryHistory(HistoryQueryRequest req) {
        Specification<QueryTaskDO> spec = buildHistorySpec(req);
        PageRequest pageable = PageRequest.of(req.pageNum() - 1, req.pageSize());
        Page<QueryTaskDO> page = queryTaskRepository.findAll(spec, pageable);

        List<HistoryRecordVO> records = page.getContent().stream()
                .map(HistoryRecordVO::from)
                .collect(Collectors.toList());

        return new PageResult<>(records, page.getTotalElements(),
                page.getNumber() + 1, page.getSize());
    }

    @Override
    public QueryTaskDO exportSingle(String taskId) {
        // UUID 格式校验（防路径遍历）
        if (taskId == null || !taskId.matches("^[0-9a-fA-F-]{36}$")) {
            throw new BusinessException(ErrorCode.A0021, "任务不存在: " + taskId);
        }
        return queryTaskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0021, "问答任务不存在: " + taskId));
    }

    @Override
    @Transactional
    public void deleteHistory(String taskId) {
        if (taskId == null || !taskId.matches("^[0-9a-fA-F-]{36}$")) {
            throw new BusinessException(ErrorCode.A0021, "任务不存在: " + taskId);
        }
        QueryTaskDO task = queryTaskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0021, "问答任务不存在: " + taskId));

        queryTaskRepository.delete(task);
        log.info("历史记录已删除: taskId={}, question={}", taskId,
                task.getQuestion() != null ? task.getQuestion().substring(0, Math.min(30, task.getQuestion().length())) : "");
    }

    /**
     * 根据筛选请求构建 JPA Specification 动态 where 链。
     *
     * <p>所有筛选参数可选；未传则不加该条件。按 createTime 降序排列。</p>
     */
    private Specification<QueryTaskDO> buildHistorySpec(HistoryQueryRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (req.studentName() != null && !req.studentName().isBlank()) {
                predicates.add(cb.like(root.get("studentName"),
                        "%" + req.studentName() + "%"));
            }
            if (req.studentNo() != null && !req.studentNo().isBlank()) {
                predicates.add(cb.equal(root.get("studentNo"), req.studentNo()));
            }
            if (req.subject() != null && !req.subject().isBlank()) {
                predicates.add(cb.equal(root.get("subject"), req.subject()));
            }
            if (req.status() != null && !req.status().isBlank()) {
                try {
                    QueryTaskStatus status = QueryTaskStatus.valueOf(req.status().toUpperCase());
                    predicates.add(cb.equal(root.get("status"), status));
                } catch (IllegalArgumentException ignored) {
                    // 无效状态值：不加条件，查询返回空（因无匹配 status）
                    predicates.add(cb.isNull(root.get("status")));
                }
            }
            if (req.startDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"),
                        req.startDate().atStartOfDay()));
            }
            if (req.endDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"),
                        req.endDate().atTime(LocalTime.MAX)));
            }

            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}