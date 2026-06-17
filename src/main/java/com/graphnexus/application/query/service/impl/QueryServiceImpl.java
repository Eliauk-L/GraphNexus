package com.graphnexus.application.query.service.impl;

import com.graphnexus.application.analysis.model.PrunedSubgraph;
import com.graphnexus.application.analysis.model.PruningRequest;
import com.graphnexus.application.analysis.strategy.StudentDiagnosisStrategy;
import com.graphnexus.application.llmgateway.service.LlmGateway;
import com.graphnexus.application.query.config.QueryProperties;
import com.graphnexus.application.query.model.QueryIntent;
import com.graphnexus.application.query.model.QueryResultBO;
import com.graphnexus.application.query.model.QueryResultBO.TokenUsage;
import com.graphnexus.application.query.prompt.PromptTemplateService;
import com.graphnexus.application.query.service.QueryService;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import com.graphnexus.infrastructure.neo4j.node.GraphNode;
import com.graphnexus.infrastructure.neo4j.node.StudentNode;
import com.graphnexus.infrastructure.neo4j.repository.GraphNodeRepository;
import com.graphnexus.infrastructure.mysql.query.QueryTaskDO;
import com.graphnexus.infrastructure.mysql.query.QueryTaskRepository;
import com.graphnexus.infrastructure.mysql.query.QueryTaskStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final GraphNodeRepository graphNodeRepository;
    private final StudentDiagnosisStrategy diagnosisStrategy;
    private final PromptTemplateService promptTemplateService;
    private final LlmGateway llmGateway;
    private final QueryTaskRepository queryTaskRepository;
    private final QueryProperties queryProperties;
    private final ObjectMapper objectMapper;

    // ======================== 意图识别关键词 ========================

    private static final Map<String, QueryIntent> INTENT_KEYWORDS = Map.of(
            "薄弱", QueryIntent.STUDENT_DIAGNOSIS,
            "加强", QueryIntent.STUDENT_DIAGNOSIS,
            "掌握", QueryIntent.STUDENT_DIAGNOSIS,
            "诊断", QueryIntent.STUDENT_DIAGNOSIS,
            "分析学生", QueryIntent.STUDENT_DIAGNOSIS
    );

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
                    intent, student.getStudentNo(), subject, params);
            PrunedSubgraph subgraph = diagnosisStrategy.prune(pruningRequest);

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

            // 6. Prompt 组装
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

            var promptPair = promptTemplateService.buildPrompt(intent, templateVars);

            // 7. LLM 调用（含重试 + 格式校验）
            String answer = callLlmWithRetry(promptPair.systemPrompt(), promptPair.userMessage());

            // 8. 持久化
            long elapsedMs = System.currentTimeMillis() - startTime;
            persistTask(taskId, question, studentName, studentNo, subject, intent,
                    QueryTaskStatus.COMPLETED, answer, subgraph, estimatedTokens,
                    (int) (promptPair.systemPrompt().length() / charsPerToken + estimatedTokens),
                    estimatedTokens, null, elapsedMs);

            return new QueryResultBO(taskId, "COMPLETED", question, intent.name(),
                    answer, new TokenUsage(subgraph.meta().totalNodes(), subgraph.meta().totalEdges(),
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
                task.getIntent(), task.getAnswer(), tokenUsage,
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
            PruningRequest pruningRequest = new PruningRequest(intent, student.getStudentNo(), subject, params);
            PrunedSubgraph subgraph = diagnosisStrategy.prune(pruningRequest);
            String subgraphText = serializeSubgraph(subgraph, student);

            int charsPerToken = queryProperties.getTokenBudget().getCharsPerToken();
            Map<String, String> templateVars = buildTemplateVars(question, student, subject, subgraphText, subgraph);
            var promptPair = promptTemplateService.buildPrompt(intent, templateVars);
            String answer = callLlmWithRetry(promptPair.systemPrompt(), promptPair.userMessage());

            long elapsedMs = System.currentTimeMillis() - startTime;
            updateCompletedTask(taskId, answer, subgraph, charsPerToken, elapsedMs);
            return new QueryResultBO(taskId, "COMPLETED", question, intent.name(),
                    answer, null, null, LocalDateTime.now(), LocalDateTime.now());
        } catch (Exception e) {
            throw e;
        }
    }

    // ======================== 智能对话（chat） ========================

    /** 支持的学科关键词 */
    private static final List<String> KNOWN_SUBJECTS = List.of(
            "数学", "语文", "英语", "物理", "化学", "生物", "历史", "地理", "政治");

    /** 提取学生姓名：匹配 "学生XXX"、"XXX的数学/物理..." 等模式 */
    private static final java.util.regex.Pattern STUDENT_NAME_PATTERN =
            java.util.regex.Pattern.compile("学生(.+?)(?:的|数学|语文|英语|物理|化学|生物|历史|地理|政治|薄弱|掌握|诊断|分析|$)");

    /** 提取学科 */
    private static final java.util.regex.Pattern SUBJECT_PATTERN =
            java.util.regex.Pattern.compile("(数学|语文|英语|物理|化学|生物|历史|地理|政治)");

    @Override
    public QueryResultBO chat(String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(ErrorCode.A0002, "问题不能为空");
        }
        String subject = extractSubject(question);
        if (subject == null) {
            throw new BusinessException(ErrorCode.A0019,
                    "无法从问题中识别学科，请明确提及学科名称（如数学、物理等）。示例：分析学生张三的数学薄弱点");
        }
        String studentName = extractStudentName(question);
        if (studentName == null || studentName.isBlank()) {
            throw new BusinessException(ErrorCode.A0019,
                    "无法从问题中识别学生姓名，请以\"学生XXX\"格式描述。示例：分析学生张三的数学薄弱点");
        }
        log.info("chat 实体提取: question={}, studentName={}, subject={}", question, studentName, subject);
        return ask(question, studentName.trim(), null, subject);
    }

    String extractSubject(String question) {
        var matcher = SUBJECT_PATTERN.matcher(question);
        return matcher.find() ? matcher.group(1) : null;
    }

    String extractStudentName(String question) {
        var matcher = STUDENT_NAME_PATTERN.matcher(question);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            if (name.length() >= 2 && name.length() <= 10 && !KNOWN_SUBJECTS.contains(name)) {
                return name;
            }
        }
        return null;
    }

    // ======================== 意图识别 ========================

    QueryIntent recognizeIntent(String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(ErrorCode.A0019, "问题不能为空");
        }
        for (var entry : INTENT_KEYWORDS.entrySet()) {
            if (question.contains(entry.getKey())) {
                log.debug("意图识别: keyword={}, intent={}", entry.getKey(), entry.getValue());
                return entry.getValue();
            }
        }
        throw new BusinessException(ErrorCode.A0019,
                "无法识别查询意图，请更明确地描述问题。当前支持：学生薄弱点诊断（含\"薄弱\"/\"加强\"/\"掌握\"/\"诊断\"等关键词）");
    }

    // ======================== 实体解析 ========================

    StudentNode resolveStudent(String studentName, String studentNo) {
        if (studentNo != null && !studentNo.isBlank()) {
            var row = graphNodeRepository.findStudentByNo(studentNo)
                    .orElseThrow(() -> new BusinessException(ErrorCode.A0006, "未找到学生，学号: " + studentNo));
            return buildStudentNode(row);
        }
        if (studentName != null && !studentName.isBlank()) {
            var rows = graphNodeRepository.findStudentByName(studentName);
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.A0006, "未找到学生: " + studentName);
            }
            if (rows.size() > 1) {
                List<Map<String, String>> candidates = rows.stream()
                        .map(r -> Map.of(
                                "studentNo", (String) r.get("studentNo"),
                                "name", (String) r.get("name"),
                                "className", (String) r.get("className"),
                                "grade", (String) r.get("grade")
                        ))
                        .collect(Collectors.toList());
                try {
                    throw new BusinessException(ErrorCode.A0020,
                            "存在多个同名或相似学生: " + objectMapper.writeValueAsString(candidates));
                } catch (JsonProcessingException e) {
                    throw new BusinessException(ErrorCode.A0020, "存在多个同名或相似学生");
                }
            }
            return buildStudentNode(rows.get(0));
        }
        throw new BusinessException(ErrorCode.A0002, "请提供学生姓名或学号");
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
                .collect(Collectors.toMap(GraphNode::getId, n -> n, (a, b) -> a));
        var studentNode = subgraph.nodes().stream()
                .filter(n -> n instanceof StudentNode).findFirst();
        var kpNodes = subgraph.nodes().stream()
                .filter(n -> !(n instanceof StudentNode)).collect(Collectors.toList());

        // 薄弱知识点
        var mastersEdges = subgraph.edges().stream()
                .filter(e -> "MASTERS".equals(e.getEdgeType())).collect(Collectors.toList());
        var prereqEdges = subgraph.edges().stream()
                .filter(e -> "PREREQUISITE_OF".equals(e.getEdgeType())).collect(Collectors.toList());

        if (!mastersEdges.isEmpty()) {
            sb.append("## 知识点掌握度\n\n");
            // 按 weight 升序排列（越弱越靠前）
            mastersEdges.sort(Comparator.comparingDouble(e -> e.getWeight() != null ? e.getWeight() : 1.0));
            for (var edge : mastersEdges) {
                var kpNode = nodeMap.get(edge.getTargetNodeId());
                String kpName = kpNode != null ? extractKpName(kpNode) : edge.getTargetNodeId();
                double weight = edge.getWeight() != null ? edge.getWeight() : 0;
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
                var fromNode = nodeMap.get(edge.getSourceNodeId());
                var toNode = nodeMap.get(edge.getTargetNodeId());
                String fromName = fromNode != null ? extractKpName(fromNode) : edge.getSourceNodeId();
                String toName = toNode != null ? extractKpName(toNode) : edge.getTargetNodeId();
                double strength = edge.getWeight() != null ? edge.getWeight() : 0;
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

    String callLlmWithRetry(String systemPrompt, String userMessage) {
        int maxRetries = queryProperties.getRetry().getMaxRetries();
        long retryDelay = queryProperties.getRetry().getRetryDelayMs();
        String lastResponse = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String currentSystemPrompt = systemPrompt;
                if (attempt > 0) {
                    currentSystemPrompt = systemPrompt + "\n\n【重要】上次输出格式不正确。"
                            + "请务必以 ## 标题开头，直接开始报告正文，不要有任何前导语。"
                            + "确保包含至少一个列表（- 或 1. ）。";
                }

                String response = llmGateway.chat(currentSystemPrompt, userMessage);
                lastResponse = response;

                if (validateLlmResponse(response)) {
                    return response;
                }
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
        // 重试耗尽，降级返回原始文本
        log.warn("LLM 格式校验重试耗尽，降级返回原始文本");
        return lastResponse != null ? lastResponse : "";
    }

    boolean validateLlmResponse(String response) {
        if (response == null || response.isBlank()) return false;
        String trimmed = response.trim();
        // 必须以 # 开头（标题，无前导语）
        if (!trimmed.startsWith("#")) return false;
        // 必须含列表标记
        if (!trimmed.contains("- ") && !trimmed.matches(".*\\d\\.\\s.*")) return false;
        // 不含常见前导语
        String lower = trimmed.substring(0, Math.min(100, trimmed.length()));
        return !lower.contains("根据提供") && !lower.contains("以下是") && !lower.contains("根据子图");
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
        return new StudentNode(
                (String) row.get("studentNo"),
                (String) row.get("name"),
                (String) row.get("className"),
                (String) row.get("grade")
        );
    }

    String extractKpName(GraphNode node) {
        if (node instanceof com.graphnexus.infrastructure.neo4j.node.KnowledgePointNode kp) {
            return kp.getName() != null ? kp.getName() : node.getId();
        }
        return node.getId();
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
}