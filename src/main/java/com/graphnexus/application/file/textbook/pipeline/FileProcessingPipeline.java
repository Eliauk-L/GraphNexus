package com.graphnexus.application.file.textbook.pipeline;

import com.graphnexus.application.file.parse.FileParseType;

/**
 * 文件处理 Pipeline 抽象 — 仅负责对已入库文件执行处理链路。
 *
 * <p>上传由 {@link com.graphnexus.application.file.upload.UploadService} 负责，
 * 本接口只关注 解析→抽取→融合 等后续处理。
 * 支持处理在线文件（通过 ID 送入 processStored）。</p>
 *
 * <p>新增文件类型只需实现此接口并注册为 Spring Bean。见 ADR-001。</p>
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
     * 对已入库文件执行全链路处理（解析→抽取→融合）。
     *
     * @param documentId 文档 ID
     * @return 处理结果
     */
    Object processStored(Long documentId);
}