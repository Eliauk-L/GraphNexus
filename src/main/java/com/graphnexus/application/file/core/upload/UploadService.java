package com.graphnexus.application.file.core.upload;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传服务抽象 — 仅负责文件存储入库，不做后续处理。
 *
 * <p>教材上传走 {@code textbook} 表，成绩上传走 {@code exam_record} 表，
 * 各自实现本接口。处理链路通过 {@code FileProcessingPipeline.processStored()} 单独触发。</p>
 *
 * @author Jay
 * @date 2026/06/18
 */
public interface UploadService {

    /**
     * 上传文件 — 校验 + 存储 + 入库。
     *
     * @param file    上传文件
     * @param subject 学科
     * @return 入库后的业务对象
     */
    Object upload(MultipartFile file, String subject);
}