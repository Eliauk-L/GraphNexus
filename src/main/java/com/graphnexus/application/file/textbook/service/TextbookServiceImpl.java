package com.graphnexus.application.file.textbook.service;

import com.graphnexus.application.file.textbook.event.TextbookDeletedEvent;
import com.graphnexus.application.file.textbook.event.TextbookParsedEvent;
import com.graphnexus.application.file.textbook.model.TextbookBO;
import com.graphnexus.application.file.parse.model.FileParseRequest;
import com.graphnexus.application.file.textbook.model.ParseResult;
import com.graphnexus.application.file.parse.FileParser;
import com.graphnexus.application.file.parse.FileParserRegistry;
import com.graphnexus.application.file.textbook.parser.TextbookParser;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 教材业务服务实现 — 事件驱动，解析完成后发布 {@link TextbookParsedEvent}。
 *
 * <p>教材模块不直接依赖图谱模块（单向依赖原则）。解析成功后发布事件，
 * 由图谱构建模块的 {@code TextbookParsedEventListener} 监听并自动触发
 * 两阶段流水线（构建→融合）。教材模块仅依赖自身事件类 + ApplicationEventPublisher。</p>
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
    private final TextbookUploadService uploadService;
    private final FileParserRegistry fileParserRegistry;
    private final ApplicationEventPublisher eventPublisher;

    // ======================== 上传（仅存储 + 入库） ========================

    @Override
    @Transactional
    public TextbookBO upload(MultipartFile file, String subject) {
        return (TextbookBO) uploadService.upload(file, subject);
    }

    // ======================== 解析（前端主动调用） ========================

    @Override
    @Transactional
    public ParseResult parse(Long documentId) {
        TextbookDO doc = textbookRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006, "文档不存在: id=" + documentId));

        FileStatus status = doc.getStatus();
        log.info("parse 触发: id={}, currentStatus={}", documentId, status);

        if (status == FileStatus.PARSING) {
            throw new BusinessException(ErrorCode.A0009, "文档正在解析中，请稍后再试");
        }
        if (status == FileStatus.DELETING) {
            throw new BusinessException(ErrorCode.A0006, "文档正在删除中，无法解析: id=" + documentId);
        }

        // 从 MinIO 读取文件
        byte[] rawBytes;
        try (InputStream is = fileStorageService.getFile(
                fileStorageService.extractObjectKey(doc.getFilePath()))) {
            rawBytes = toByteArray(is);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.B0001, "文件读取失败", "文件存储服务暂时不可用");
        }

        // 获取解析器链
        String filename = doc.getName() + (doc.getFileType() != null && !doc.getFileType().isEmpty()
                ? "." + doc.getFileType() : "");
        List<FileParser> parsers = fileParserRegistry.getParsers(filename, FileParser.BIZ_TEXTBOOK);
        if (parsers.isEmpty()) {
            throw new BusinessException(ErrorCode.A0004, "未找到文件解析器: " + filename);
        }

        // 更新状态为 PARSING，重新持有托管实体
        doc.setStatus(FileStatus.PARSING);
        doc.setFailReason(null);
        doc = textbookRepository.saveAndFlush(doc);

        // 遍历解析器链（主→兜底）
        ParseResult parseResult = null;
        String lastParserName = "unknown";
        Exception lastError = null;
        for (FileParser parser : parsers) {
            lastParserName = parser.getClass().getSimpleName();
            try {
                FileParseRequest request = new FileParseRequest(null, filename, null, rawBytes);
                parseResult = ((TextbookParser) parser).parse(request.rawBytes());
                lastError = null;
                break;
            } catch (Exception e) {
                lastError = e;
                log.warn("解析器 {} 失败: id={}, error={}", lastParserName, documentId, e.getMessage());
            }
        }

        if (parseResult == null) {
            // 所有解析器都失败 → 回退到 UPLOADED
            doc.setStatus(FileStatus.UPLOADED);
            doc.setFailReason("all parsers failed: " + truncate(
                    lastError != null ? lastError.getMessage() : "unknown", 300));
            textbookRepository.saveAndFlush(doc);
            throw new BusinessException(ErrorCode.A0004, "文档解析失败（所有解析器均失败）");
        }

        // 解析成功 → PARSED
        doc.setStatus(FileStatus.PARSED);
        doc.setTextContent(parseResult.textContent());
        doc.setPageCount(parseResult.pageCount());
        doc.setFailReason(null);
        doc = textbookRepository.saveAndFlush(doc);

        // 发布解析完成事件 → 图谱构建模块监听并自动触发抽取（事件驱动，单向依赖）
        // ★ 使用 TransactionSynchronization.afterCommit() 确保事务提交后 PARSED 已落库才发布事件，
        //    避免 @Async 监听器在新线程中读到 UPLOADED 旧状态。
        log.info("解析完成: id={}, parser={}, textLength={}",
                documentId, lastParserName,
                parseResult.textContent() != null ? parseResult.textContent().length() : 0);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("事务已提交(PARSED已落库)，发布 TextbookParsedEvent: id={}", documentId);
                eventPublisher.publishEvent(new TextbookParsedEvent(this, documentId));
            }
        });
        return parseResult;
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
        TextbookDO doc = textbookRepository.findByIdAndStatusNot(id, FileStatus.DELETING)
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

        // 幂等检查：已在 DELETING 状态则跳过
        if (doc.getStatus() == FileStatus.DELETING) {
            log.info("文档已在 DELETING 状态，跳过重复删除: id={}", id);
            return;
        }

        // 进入 DELETING 状态，发布事件驱动级联删除
        doc.setStatus(FileStatus.DELETING);
        doc = textbookRepository.saveAndFlush(doc);
        log.info("文档进入 DELETING 状态: id={}", id);

        // ★ 使用 TransactionSynchronization.afterCommit() 确保事务提交后 DELETING 已落库才发布事件，
        //    与 parse() 保持一致，避免 @Async 监听器在新线程中读不到 DELETING 状态。
        final Long docId = doc.getId();
        final String docPath = doc.getFilePath();
        final String docNo = doc.getDocumentNo();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("事务已提交(DELETING已落库)，发布 TextbookDeletedEvent: id={}", docId);
                eventPublisher.publishEvent(new TextbookDeletedEvent(this, docId, docPath, docNo));
            }
        });
    }

    /**
     * 级联删除终结点：图谱清理完成后，执行 MinIO 文件删除 + MySQL 物理删除。
     *
     * <p>由 {@code TextbookGraphClearedEventListener} 调用（事件链最后一步）。
     * 独立 {@code @Transactional} 方法，通过 {@code TextbookService} 代理调用，
     * 确保事务正确开启（区别于 {@code @EventListener} 方法上的 @Transactional 可能被绕过）。</p>
     */
    @Override
    @Transactional
    public void finalizeDeletion(Long documentId, String filePath) {
        log.info("执行 MinIO + MySQL 物理删除: documentId={}", documentId);

        // ① 查找文档（幂等：已删除则跳过）
        var docOpt = textbookRepository.findById(documentId);
        if (docOpt.isEmpty()) {
            log.info("文档已不存在（幂等跳过）: id={}", documentId);
            return;
        }
        TextbookDO doc = docOpt.get();

        // ② MinIO 文件删除（引用计数保护）
        String objectKey = fileStorageService.extractObjectKey(filePath);
        long refCount = textbookRepository.countByFilePathAndStatusNot(filePath, FileStatus.DELETING);
        if (refCount <= 1) {
            try {
                fileStorageService.deleteFile(objectKey);
                log.info("MinIO 文件已删除（最后引用）: id={}, filePath={}", documentId, filePath);
            } catch (Exception e) {
                log.warn("MinIO 文件删除失败（可能已被删除，忽略继续）: path={}, error={}",
                        filePath, e.getMessage());
            }
        } else {
            log.info("文件仍被 {} 条其他记录引用，跳过 MinIO 删除: id={}, filePath={}",
                    refCount - 1, documentId, filePath);
        }

        // ③ MySQL 物理删除（图谱清理已成功，执行最终物理删除）
        textbookRepository.delete(doc);
        log.info("文档已物理删除: id={}, filePath={}", documentId, filePath);
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

    private byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}