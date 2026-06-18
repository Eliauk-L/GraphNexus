package com.graphnexus.application.file.core.service.impl;

import com.graphnexus.application.file.core.model.FileBO;
import com.graphnexus.application.file.core.model.DeleteResultBO;
import com.graphnexus.application.file.parse.model.ParseResult;
import com.graphnexus.application.file.core.model.UpdateFileBO;
import com.graphnexus.application.file.core.pipeline.DocumentProcessingPipeline;
import com.graphnexus.application.file.core.service.FileService;
import com.graphnexus.application.file.parse.model.FileParseType;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.file.entity.FileDO;
import com.graphnexus.infrastructure.mysql.file.repository.FileRepository;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.neo4j.repository.GraphNodeRepository;
import com.graphnexus.infrastructure.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档处理业务服务实现（v2 — Pipeline 适配）。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final GraphNodeRepository graphNodeRepository;
    private final DocumentProcessingPipeline documentProcessingPipeline;

    // ======================== 上传 ========================

    @Override
    @Transactional
    public FileBO upload(MultipartFile file, String subject) {
        return (FileBO) documentProcessingPipeline.process(file, subject);
    }

    // ======================== 处理 ========================

    @Override
    @Transactional
    public ParseResult process(Long documentId) {
        documentProcessingPipeline.retry(documentId);
        FileDO doc = fileRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006, "文档不存在: id=" + documentId));
        return new ParseResult(doc.getTextContent(), doc.getPageCount() != null ? doc.getPageCount() : 0, null);
    }

    // ======================== 查询 ========================

    @Override
    @Transactional(readOnly = true)
    public Page<FileBO> listDocuments(int pageNum, int pageSize, String fileType, String name) {
        FileParseType type = null;
        if (fileType != null && !fileType.isBlank()) {
            type = FileParseType.valueOf(fileType);
        }
        return fileRepository
                .findByConditions(type, name, PageRequest.of(pageNum - 1, pageSize))
                .map(this::toBO);
    }

    @Override
    @Transactional(readOnly = true)
    public FileBO getDocument(Long id) {
        FileDO doc = fileRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                        "文档不存在: id=" + id));
        return toBO(doc);
    }

    // ======================== 更新 ========================

    @Override
    @Transactional
    public FileBO updateDocument(Long id, UpdateFileBO bo) {
        FileDO doc = fileRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                        "文档不存在: id=" + id));

        if (bo.getName() != null && !bo.getName().isBlank()) {
            doc.setName(bo.getName().trim());
        }

        doc = fileRepository.save(doc);
        log.info("文档已更新: id={}, name={}", doc.getId(), doc.getName());
        return toBO(doc);
    }

    // ======================== 删除 ========================

    @Override
    @Transactional
    public void deleteDocument(Long id) {
        FileDO doc = fileRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                        "文档不存在: id=" + id));

        if (doc.getIsDeleted() == 1) {
            log.info("文档已删除（幂等跳过）: id={}", id);
            return;
        }

        if (doc.getStatus() != FileStatus.DELETING) {
            doc.setStatus(FileStatus.DELETING);
            fileRepository.saveAndFlush(doc);
            log.info("文档进入 DELETING 状态: id={}", id);
        } else {
            log.info("文档已在 DELETING 状态，从中断点继续: id={}", id);
        }

        try {
            fileStorageService.deleteFile(doc.getMinioPath());
        } catch (Exception e) {
            log.warn("MinIO 文件删除失败（可能已被删除，忽略继续）: path={}, error={}",
                    doc.getMinioPath(), e.getMessage());
        }

        graphNodeRepository.deleteByDocumentId(String.valueOf(id));

        doc.markDeleted();
        fileRepository.save(doc);

        log.info("文档已删除: id={}, minioPath={}", doc.getId(), doc.getMinioPath());
    }

    // ======================== 工具方法 ========================

    private FileBO toBO(FileDO doc) {
        return FileBO.builder()
                .id(doc.getId())
                .documentNo(doc.getDocumentNo())
                .name(doc.getName())
                .subject(doc.getSubject())
                .fileSize(doc.getFileSize())
                .minioPath(doc.getMinioPath())
                .pageCount(doc.getPageCount())
                .textContent(doc.getTextContent())
                .metadataJson(doc.getMetadataJson())
                .status(doc.getStatus() != null ? doc.getStatus().name() : null)
                .failReason(doc.getFailReason())
                .fileType(doc.getFileType() != null ? doc.getFileType().name() : null)
                .uploadedBy(doc.getUploadedBy())
                .createTime(doc.getCreateTime())
                .updateTime(doc.getUpdateTime())
                .build();
    }
}