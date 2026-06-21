package com.graphnexus.application.graph.construction.listener;

import com.graphnexus.application.file.grade.event.GradeDeletedEvent;
import com.graphnexus.application.file.grade.event.GradeUploadedEvent;
import com.graphnexus.application.graph.construction.event.GraphConstructedEvent;
import com.graphnexus.common.event.GraphChangedEvent;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import com.graphnexus.infrastructure.neo4j.edge.AttendedEdge;
import com.graphnexus.infrastructure.neo4j.edge.BelongsToSubjectEdge;
import com.graphnexus.infrastructure.neo4j.edge.TestedEdge;
import com.graphnexus.infrastructure.neo4j.node.ExamNode;
import com.graphnexus.infrastructure.neo4j.node.KnowledgePointNode;
import com.graphnexus.infrastructure.neo4j.node.StudentNode;
import com.graphnexus.infrastructure.neo4j.node.SubjectNode;
import com.graphnexus.infrastructure.neo4j.repository.ConstructionGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 成绩事件 → Neo4j 图谱操作监听器。
 *
 * <p>监听成绩上传/删除事件，负责图谱节点的创建和清理。
 * 与成绩处理模块完全解耦，仅依赖 MySQL（只读）+ Neo4j（写入）。
 * 构建完成后发布 GraphConstructedEvent 触发下游融合。</p>
 *
 * @author Jay
 * @date 2026/06/19
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradeGraphEventListener {

    private final ExamRecordRepository examRecordRepository;
    private final ConstructionGraphRepository constructionGraphRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 成绩上传 → 构建 Neo4j 图。
     */
    @EventListener
    public void onGradeUploaded(GradeUploadedEvent event) {
        String examNo = event.getExamNo();
        String subject = event.getSubject();

        List<ExamRecordDO> records = examRecordRepository.findByExamNoAndIsDeleted(examNo, 0);
        if (records.isEmpty()) {
            log.warn("收到 GradeUploadedEvent 但 MySQL 中无记录: examNo={}", examNo);
            return;
        }

        ExamRecordDO first = records.get(0);

        // ① 查找或创建 SubjectNode + BELONGS_TO_SUBJECT 边
        SubjectNode subjectNode = constructionGraphRepository.findOrCreateSubject(subject);

        // ② MERGE ExamNode + BELONGS_TO_SUBJECT
        ExamNode examNode = new ExamNode(examNo, first.getExamName(), first.getExamDate());
        examNode = constructionGraphRepository.save(examNode);
        constructionGraphRepository.saveEdge(new BelongsToSubjectEdge(examNode.getId(), subjectNode.getId()));

        // ③ MERGE StudentNode + AttendedEdge
        for (ExamRecordDO rec : records) {
            StudentNode studentNode = new StudentNode(
                    rec.getStudentNo(), rec.getName(), rec.getClassName(), null);
            studentNode = constructionGraphRepository.save(studentNode);
            constructionGraphRepository.saveEdge(new AttendedEdge(studentNode.getId(), examNode.getId()));
        }

        // ④ MERGE KnowledgePointNode + TestedEdge + BELONGS_TO_SUBJECT
        for (String kpName : event.getKnowledgePoints()) {
            KnowledgePointNode kpNode = new KnowledgePointNode(kpName);
            kpNode = constructionGraphRepository.save(kpNode);
            constructionGraphRepository.saveEdge(new TestedEdge(examNode.getId(), kpNode.getId()));
            constructionGraphRepository.saveEdge(new BelongsToSubjectEdge(kpNode.getId(), subjectNode.getId()));
        }

        // 发布 GraphConstructedEvent（构建完成后触发融合 · DESIGN D5）
        eventPublisher.publishEvent(new GraphConstructedEvent(
                this, GraphConstructedEvent.SOURCE_CSV, GraphConstructedEvent.MODE_FULL,
                event.getSubject(), event.getKnowledgePoints(), null, event.getExamNo()));

        // 图结构已变更（新节点/边已写入），触发指标缓存失效
        eventPublisher.publishEvent(new GraphChangedEvent(this));

        log.info("图谱构建完成: examNo={}, students={}, kps={}",
                examNo, records.size(), event.getKnowledgePoints().size());
    }

    /**
     * 成绩删除 → 清理 Neo4j 图。
     */
    @EventListener
    public void onGradeDeleted(GradeDeletedEvent event) {
        String examNo = event.getExamNo();

        int attendEdges = constructionGraphRepository.deleteEdgesByExamNo(examNo, "ATTENDED");
        int testedEdges = constructionGraphRepository.deleteEdgesByExamNo(examNo, "TESTED");
        int deletedNodes = constructionGraphRepository.deleteExamNode(examNo);

        // 图结构已变更（节点/边已删除），触发指标缓存失效
        eventPublisher.publishEvent(new GraphChangedEvent(this));

        log.info("图谱清理完成: examNo={}, attendEdges={}, testedEdges={}, examNodes={}",
                examNo, attendEdges, testedEdges, deletedNodes);
    }
}