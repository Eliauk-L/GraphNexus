package com.graphnexus.application.file.textbook.service;

import com.graphnexus.application.file.textbook.model.TextbookBO;
import com.graphnexus.application.file.parse.ParseResult;
import com.graphnexus.application.file.textbook.pipeline.TextbookProcessingPipeline;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
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

    private final TextbookRepository textbookRepository;
    private final FileStorageService fileStorageService;
    private final GraphNodeRepository graphNodeRepository;
    private final TextbookUploadService uploadService;
    private final TextbookProcessingPipeline textbookProcessingPipeline;

    // ======================== 上传（仅存储 + 入库） ========================

    @Override
    @Transactional
    public TextbookBO upload(MultipartFile file, String subject) {
        return (TextbookBO) uploadService.upload(file, subject);
    }

    // ======================== 处理（对已入库文件执行全链路） ========================

    @Override
    @Transactional
    public ParseResult process(Long documentId) {
        textbookProcessingPipeline.processStored(documentId);
        TextbookDO doc = textbookRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006, "文档不存在: id=" + documentId));
        return new ParseResult(doc.getTextContent(), doc.getPageCount() != null ? doc.getPageCount() : 0, null);
    }

    // ======================== 查询 ========================

    @Override
    @Transactional(readOnly = true)
    public Page<TextbookBO> listTextBooks(int pageNum, int pageSize, String fileType, String name) {
        return textbookRepository
                .findByConditions(fileType, name, PageRequest.of(pageNum - 1, pageSize))
                .map(this::toBO);
    }

    @Override
    @Transactional(readOnly = true)
    public TextbookBO getTextBook(Long id) {
        TextbookDO doc = textbookRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                        "文档不存在: id=" + id));
        return toBO(doc);
    }

    // ======================== 删除 ========================

    @Override
    @Transactional
    public void deleteTextBook(Long id) {
        TextbookDO doc = textbookRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                        "文档不存在: id=" + id));

        if (doc.getIsDeleted() == 1) {
            log.info("文档已删除（幂等跳过）: id={}", id);
            return;
        }

        // 检查是否有其他记录引用同一文件（引用计数）
        String objectKey = fileStorageService.extractObjectKey(doc.getFilePath());
        long refCount = textbookRepository.countByFilePathAndNotDeleted(doc.getFilePath());
        boolean isLastReference = (refCount <= 1); // refCount 包含当前记录

        if (doc.getStatus() != FileStatus.DELETING) {
            doc.setStatus(FileStatus.DELETING);
            textbookRepository.saveAndFlush(doc);
            log.info("文档进入 DELETING 状态: id={}", id);
        } else {
            log.info("文档已在 DELETING 状态，从中断点继续: id={}", id);
        }

        if (isLastReference) {
            try {
                fileStorageService.deleteFile(objectKey);
                log.info("MinIO 文件已删除（最后引用）: id={}, filePath={}", id, doc.getFilePath());
            } catch (Exception e) {
                log.warn("文件删除失败（可能已被删除，忽略继续）: path={}, error={}",
                        doc.getFilePath(), e.getMessage());
            }
        } else {
            log.info("文件仍被 {} 条其他记录引用，跳过 MinIO 删除: id={}, filePath={}",
                    refCount - 1, id, doc.getFilePath());
        }

        graphNodeRepository.deleteByDocumentId(String.valueOf(id));

        doc.markDeleted();
        textbookRepository.save(doc);

        log.info("文档已删除: id={}, filePath={}", doc.getId(), doc.getFilePath());
    }

    // ======================== 工具方法 ========================

    private TextbookBO toBO(TextbookDO doc) {
        return TextbookBO.builder()
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