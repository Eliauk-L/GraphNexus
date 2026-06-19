package com.graphnexus.application.file.upload;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传服务抽象 — 仅负责文件存储入库，不做后续处理。
 *
 * <p>教材上传走 {@code text_book} 表，成绩上传走 {@code exam_record} 表，
 * 各自实现本接口。解析由前端主动调用 {@code POST /api/v1/file/textbooks/parse/{id}}，
 * 抽取走 {@code POST /api/v1/graph/extract/{id}}，融合走 {@code POST /api/v1/graph/fusion/execute}。</p>
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