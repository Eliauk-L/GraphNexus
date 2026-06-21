package com.graphnexus.application.graph.construction.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 图谱构建完成事件 —— 图谱构建阶段全部完成后发布，触发下游融合。
 *
 * <p>发布方：graph 模块（{@code ConstructionServiceImpl} / {@code GradeGraphEventListener}）
 * 消费方：analysis 模块（{@code GraphConstructedEventListener}）</p>
 *
 * <p>载荷自包含，发布方不引用消费方类型（ADR-018 / ADR-024）。
 * 同步事件（plain {@code @EventListener}，同线程），join 发布方 {@code @Transactional}。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@Getter
public class GraphConstructedEvent extends ApplicationEvent {

    /** 构建来源：文档路径 */
    public static final String SOURCE_DOCUMENT = "DOCUMENT";
    /** 构建来源：成绩路径 */
    public static final String SOURCE_CSV = "CSV";

    /** 融合模式：全量融合 */
    public static final String MODE_FULL = "FULL";
    /** 融合模式：增量融合 */
    public static final String MODE_INCREMENTAL = "INCREMENTAL";

    /** 构建来源（DOCUMENT / CSV） */
    private final String source;
    /** 融合模式（FULL / INCREMENTAL） */
    private final String mode;
    /** 学科 */
    private final String subject;
    /** 涉及的知识点名称列表 */
    private final List<String> kpNames;
    /** 文档 ID（仅 DOCUMENT 路径，CSV 时为 null） */
    private final Long documentId;
    /** 考试编号（仅 CSV 路径，DOCUMENT 时为 null） */
    private final String examNo;

    public GraphConstructedEvent(Object source, String src, String mode, String subject,
                                  List<String> kpNames, Long documentId, String examNo) {
        super(source);
        this.source = src;
        this.mode = mode;
        this.subject = subject;
        this.kpNames = kpNames;
        this.documentId = documentId;
        this.examNo = examNo;
    }
}
