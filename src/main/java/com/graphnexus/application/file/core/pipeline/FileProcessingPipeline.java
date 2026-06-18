package com.graphnexus.application.file.core.pipeline;

import com.graphnexus.application.file.parse.model.FileParseType;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件处理 Pipeline 抽象 — 封装"上传→解析→入库→图谱"全链路。
 *
 * <p>新增文件类型只需实现此接口并注册为 Spring Bean，
 * Controller 层通过 supportedType() 自动路由，核心代码零改动。
 * 见 ADR-001。</p>
 *
 * @author Jay
 * @date 2026/06/18
 */
public interface FileProcessingPipeline {

    /**
     * 返回本 Pipeline 处理的文件类型。
     */
    FileParseType supportedType();

    /**
     * 执行全链路同步处理。
     *
     * @param file    上传文件
     * @param subject 学科
     * @return 处理结果 VO（类型由各 Pipeline 定义）
     */
    Object process(MultipartFile file, String subject);

    /**
     * 从当前状态断点续跑至完成。
     *
     * @param documentId 文档 ID
     * @return 处理结果
     */
    Object retry(Long documentId);
}