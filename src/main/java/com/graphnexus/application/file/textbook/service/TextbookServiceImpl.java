package com.graphnexus.application.file.textbook.service;

import com.graphnexus.application.file.textbook.model.FileBO;
import com.graphnexus.application.file.parse.ParseResult;
import com.graphnexus.application.file.textbook.model.UpdateFileBO;
import com.graphnexus.application.file.textbook.pipeline.DocumentProcessingPipeline;
import com.graphnexus.application.file.textbook.service.TextbookService;
import com.graphnexus.application.file.textbook.upload.TextBookUploadService;
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
 * 文档处理业务服务实现（v2 — Pipeline 适配，上传/处理解耦）。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextbookServiceImpl implements TextbookService {

    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final GraphNodeRepository graphNodeRepository;
    private final TextBookUploadService uploadService;
    private final DocumentProcessingPipeline documentProcessingPipeline;

    // ======================== 上传（仅存储 + 入库） ========================

    @Override
    @Transactional
    public FileBO upload(MultipartFile file, String subject) {
        return (FileBO) uploadService.upload(file, subject);
    }

    // ======================== 处理（对已入库文件执行全链路） ========================

    @Override
    @Transactional
    public ParseResult process(Long documentId) {
        documentProcessingPipeline.processStored(documentId);
        FileDO doc = fileRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006, "文档不存在: id=" + documentId));
        return new ParseResult(doc.getTextContent(), doc.getPageCount() != null ? doc.getPageCount() : 0, null);
    }

    // ======================== 查询 ========================

    @Override
    @Transactional(readOnly = true)
    public Page<FileBO> listDocuments(int pageNum, int pageSize, String fileType, String name) {
        return fileRepository
                .findByConditions(fileType, name, PageRequest.of(pageNum - 1, pageSize))
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
            fileStorageService.deleteFile(
                    fileStorageService.extractObjectKey(doc.getFilePath()));
        } catch (Exception e) {
            log.warn("文件删除失败（可能已被删除，忽略继续）: path={}, error={}",
                    doc.getFilePath(), e.getMessage());
        }

        graphNodeRepository.deleteByDocumentId(String.valueOf(id));

        doc.markDeleted();
        fileRepository.save(doc);

        log.info("文档已删除: id={}, filePath={}", doc.getId(), doc.getFilePath());
    }

    // ======================== 工具方法 ========================

    private FileBO toBO(FileDO doc) {
        return FileBO.builder()
                .id(doc.getId())
                .documentNo(doc.getDocumentNo())
                .name(doc.getName())
                .subject(doc.getSubject())
                .fileSize(doc.getFileSize())
                .filePath(doc.getFilePath())
                .pageCount(doc.getPageCount())
                .textContent(doc.getTextContent())
                .status(doc.getStatus() != null ? doc.getStatus().name() : null)
                .failReason(doc.getFailReason())
                .fileType(doc.getFileType())
                .uploadedBy(doc.getUploadedBy())
                .createTime(doc.getCreateTime())
                .build();
    }
}