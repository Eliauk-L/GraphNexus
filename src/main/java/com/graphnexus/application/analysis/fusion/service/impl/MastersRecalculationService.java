package com.graphnexus.application.analysis.fusion.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.analysis.fusion.config.FusionProperties;
import com.graphnexus.application.analysis.fusion.model.TestedRecord;
import com.graphnexus.application.analysis.fusion.model.WeightResult;
import com.graphnexus.application.analysis.fusion.strategy.WeightCalculationStrategy;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import com.graphnexus.infrastructure.neo4j.repository.FusionGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MASTERS 掌握度重算服务 — 从 MySQL 读成绩 → 策略计算 → Neo4j 批量写 MASTERS 边。
 *
 * <p>从 {@link FusionServiceImpl} 中提取，职责单一：给定学生列表 → 产出 MASTERS 边。</p>
 *
 * @author Jay
 * @date 2026/06/16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MastersRecalculationService {

    private final FusionGraphRepository fusionGraphRepository;
    private final ExamRecordRepository examRecordRepository;
    private final FusionProperties fusionProperties;
    private final Map<String, WeightCalculationStrategy> weightStrategies;
    private final ObjectMapper objectMapper;

    /**
     * 全量重算指定 subject 的所有学生 MASTERS。
     */
    public int recalculateAll(String subject) {
        List<Map<String, Object>> allStudents =
                fusionGraphRepository.findAllStudentsBySubject(subject);
        return recalculate(allStudents, subject);
    }

    /**
     * 重算指定学生列表的 MASTERS。
     *
     * @param students Neo4j 查询结果，每项含 studentNo + studentNodeId
     * @param subject  学科，用于 KP 查重时限定范围
     * @return 创建的 MASTERS 边总数
     */
    public int recalculate(List<Map<String, Object>> students, String subject) {
        if (students == null || students.isEmpty()) return 0;

        WeightCalculationStrategy weightCalc = getWeightStrategy();
        int totalEdges = 0;

        for (Map<String, Object> student : students) {
            String studentNo = (String) student.get("studentNo");
            String studentNodeId = (String) student.get("studentNodeId");

            Map<String, List<TestedRecord>> byKp = groupScoresByKp(studentNo);

            List<FusionGraphRepository.MastersEdgeData> edges = new ArrayList<>();
            for (var entry : byKp.entrySet()) {
                String kpName = entry.getKey();
                WeightResult result = weightCalc.calculate(entry.getValue());
                String kpId = findKpId(kpName, subject);
                if (kpId != null) {
                    edges.add(new FusionGraphRepository.MastersEdgeData(
                            studentNodeId, kpId, result.weight(), result.summaryJson()));
                }
            }
            fusionGraphRepository.batchUpsertMastersEdges(studentNodeId, edges);
            totalEdges += edges.size();
        }
        return totalEdges;
    }

    // ======================== 私有方法 ========================

    private WeightCalculationStrategy getWeightStrategy() {
        String name = fusionProperties.getWeight().getStrategy();
        WeightCalculationStrategy strategy = weightStrategies.get(name);
        if (strategy == null) {
            throw new BusinessException(ErrorCode.B0001,
                    "未找到权重计算策略: " + name + "，可用: " + weightStrategies.keySet());
        }
        return strategy;
    }

    /** 从 MySQL exam_record 按 kpName 分组提取成绩 */
    private Map<String, List<TestedRecord>> groupScoresByKp(String studentNo) {
        List<ExamRecordDO> records = examRecordRepository.findByStudentNo(studentNo);

        Map<String, List<TestedRecord>> byKp = new HashMap<>();
        for (ExamRecordDO rec : records) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> details = objectMapper.readValue(rec.getScoreDetails(), List.class);
                for (Map<String, Object> d : details) {
                    List<String> kpNames = extractKpNames(d);
                    if (kpNames.isEmpty()) continue;
                    Double rawScore = toDouble(d.get("rawScore"));
                    Double maxScore = toDouble(d.get("maxScore"));
                    if (maxScore == null || maxScore == 0) maxScore = 1.0;
                    for (String kpName : kpNames) {
                        byKp.computeIfAbsent(kpName, k -> new ArrayList<>())
                                .add(new TestedRecord(rec.getExamDate(), rawScore, maxScore));
                    }
                }
            } catch (Exception e) {
                log.warn("解析 score_details JSON 失败 studentNo={}: {}", studentNo, e.getMessage());
            }
        }
        return byKp;
    }

    /** 从成绩明细中提取知识点名称列表（一题多 KP 时全部返回） */
    private List<String> extractKpNames(Map<String, Object> detail) {
        String kpName = (String) detail.get("kpName");
        if (kpName != null) return List.of(kpName);
        @SuppressWarnings("unchecked")
        List<String> kpNames = (List<String>) detail.get("kpNames");
        return kpNames != null ? kpNames : List.of();
    }

    private Double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        return null;
    }

    private String findKpId(String kpName, String subject) {
        List<Map<String, Object>> kps =
                fusionGraphRepository.findKnowledgePointsByNamesAndSubject(List.of(kpName), subject);
        return kps.stream().map(m -> (String) m.get("id")).findFirst().orElse(null);
    }
}