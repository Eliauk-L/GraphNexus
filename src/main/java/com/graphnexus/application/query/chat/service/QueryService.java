package com.graphnexus.application.query.chat.service;

import com.graphnexus.api.query.dto.history.HistoryQueryRequest;
import com.graphnexus.api.query.dto.history.HistoryRecordVO;
import com.graphnexus.application.analysis.model.PrunedSubgraph;
import com.graphnexus.application.query.chat.model.QueryResultBO;
import com.graphnexus.common.PageResult;
import com.graphnexus.infrastructure.mysql.query.entity.QueryTaskDO;

/**
 * 智能问答服务接口 — 编排意图识别、图剪枝、Prompt 组装、LLM 调用的完整链路。
 *
 * @author Jay
 * @date 2026/06/17
 */
public interface QueryService {

    /**
     * 同步问答 — 阻塞等待完整链路执行完毕。
     *
     * @param question     用户自然语言问题
     * @param studentName  学生姓名（可为 null，与 studentNo 至少传一个）
     * @param studentNo    学号（可为 null）
     * @param subject      学科（必传）
     * @return 问答结果（含 LLM 答案或错误信息）
     */
    QueryResultBO ask(String question, String studentName, String studentNo, String subject);

    /**
     * 异步问答 — 立即返回 taskId，后台执行。
     *
     * @return 任务 UUID
     */
    String askAsync(String question, String studentName, String studentNo, String subject);

    /**
     * 查询异步任务结果（轮询用）。
     *
     * @param taskId 任务 UUID
     * @return 当前任务状态及结果
     */
    QueryResultBO getResult(String taskId);

    /**
     * 获取剪枝子图数据（调试/可视化用）。
     *
     * @param taskId 已完成任务的 UUID
     * @return 剪枝后的子图
     */
    PrunedSubgraph getSubgraph(String taskId);

    /**
     * 智能对话 — 接受用户原始提问，自动提取学生姓名、学科等实体信息，
     * 然后复用 {@link #ask} 链路完成分析。
     *
     * @param question 用户原始自然语言问题
     * @return 问答结果（含 LLM 答案或错误信息）
     */
    QueryResultBO chat(String question);

    /**
     * 分页查询历史诊断记录。
     *
     * @param req 筛选条件 + 分页参数（所有筛选字段可选）
     * @return 分页结果（不含 answer 正文，见 DESIGN §2.4）
     */
    PageResult<HistoryRecordVO> queryHistory(HistoryQueryRequest req);

    /**
     * 导出单条诊断报告 — 返回完整 QueryTaskDO（含 answer），由 Controller 流式写响应。
     *
     * @param taskId 任务 UUID
     * @return QueryTaskDO（含 answer）
     * @throws com.graphnexus.common.exception.BusinessException A0021 任务不存在
     */
    QueryTaskDO exportSingle(String taskId);
}