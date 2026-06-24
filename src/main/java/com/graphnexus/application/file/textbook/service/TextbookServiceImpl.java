package com.graphnexus.application.file.textbook.service;

import com.graphnexus.application.file.textbook.event.TextbookDeletedEvent;
import com.graphnexus.application.file.textbook.event.TextbookParsedEvent;
import com.graphnexus.application.file.textbook.model.TextbookBO;
import com.graphnexus.application.file.parse.model.FileParseRequest;
import com.graphnexus.application.file.textbook.model.ParseResult;
import com.graphnexus.application.file.parse.FileParser;
import com.graphnexus.application.file.parse.FileParserRegistry;
import com.graphnexus.application.file.textbook.parser.TextbookParser;
import com.graphnexus.application.ops.audit.annotation.Auditable;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.mysql.ops.entity.OperationType;
import com.graphnexus.infrastructure.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
 * <p><b>事务策略（ADR-028）</b>：
 * <ul>
 *   <li>上传：委托 {@link TextbookUploadService}（自管理事务）</li>
 *   <li>解析：短事务（状态变更）→ 无事务（MinIO + 解析器）→ 短事务（状态变更）→ 事务外事件</li>
 *   <li>删除：短事务（状态→DELETING）→ 事务外事件</li>
 *   <li>查询：{@code @Transactional(readOnly = true)} 保持不变</li>
 * </ul>
 * 已消除 {@code TransactionSynchronizationManager.registerSynchronization()} afterCommit 回调。</p>
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
    private final PlatformTransactionManager txManager;

    // ======================== 上传（委托，自管理事务） ========================

    @Override
    @Auditable(OperationType.DOCUMENT_UPLOAD)
    public TextbookBO upload(MultipartFile file, String subject) {
        Long userId = getCurrentUserId();
        return (TextbookBO) uploadService.upload(file, subject, userId);
    }

    // ======================== 解析（短事务→无事务→短事务→事务外事件） ========================

    @Override
    public ParseResult parse(Long documentId) {
        // ===== 阶段 1：短事务 — 校验 + 状态→PARSING（ADR-028 规则 1）=====

        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> {
            TextbookDO doc = textbookRepository.findById(documentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                            "文档不存在: id=" + documentId));

            FileStatus currentStatus = doc.getStatus();
            log.info("parse 触发: id={}, currentStatus={}", documentId, currentStatus);

            if (currentStatus == FileStatus.PARSING) {
                throw new BusinessException(ErrorCode.A0009, "文档正在解析中，请稍后再试");
            }
            if (currentStatus == FileStatus.DELETING) {
                throw new BusinessException(ErrorCode.A0006,
                        "文档正在删除中，无法解析: id=" + documentId);
            }

            doc.setStatus(FileStatus.PARSING);
            doc.setFailReason(null);
            textbookRepository.save(doc);
        });
        // PARSING 已提交 → 前端轮询可见

        // ===== 阶段 2：无事务 — MinIO 读取 + 解析器链（ADR-028 规则 2）=====

        // 重新获取文档元数据（实体在阶段 1 事务外已 detached）
        TextbookDO docMeta = textbookRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                        "文档不存在: id=" + documentId));

        byte[] rawBytes;
        try (InputStream is = fileStorageService.getFile(
                fileStorageService.extractObjectKey(docMeta.getFilePath()))) {
            rawBytes = toByteArray(is);
        } catch (IOException e) {
            // MinIO 失败 → 短事务回退状态
            failParsing(documentId, "文件读取失败: " + e.getMessage());
            throw new BusinessException(ErrorCode.B0001, "文件读取失败", "文件存储服务暂时不可用");
        }

        String filename = docMeta.getName() + (docMeta.getFileType() != null
                && !docMeta.getFileType().isEmpty() ? "." + docMeta.getFileType() : "");
        List<FileParser> parsers = fileParserRegistry.getParsers(filename, FileParser.BIZ_TEXTBOOK);
        if (parsers.isEmpty()) {
            failParsing(documentId, "未找到文件解析器: " + filename);
            throw new BusinessException(ErrorCode.A0004, "未找到文件解析器: " + filename);
        }

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
            failParsing(documentId, "all parsers failed: "
                    + truncate(lastError != null ? lastError.getMessage() : "unknown", 300));
            throw new BusinessException(ErrorCode.A0004, "文档解析失败（所有解析器均失败）");
        }

        // 页数校验：超过 200 页拒绝
        if (parseResult.pageCount() > 200) {
            failParsing(documentId, "页数超过限制: " + parseResult.pageCount() + " 页（最大 200 页）");
            throw new BusinessException(ErrorCode.A0004,
                    "文档页数超过限制（" + parseResult.pageCount() + " 页，最大允许 200 页）");
        }

        // ===== 阶段 3：短事务 — 状态→PARSED（ADR-028 规则 1）=====

        completeParsing(documentId, parseResult);

        // ===== 阶段 4：事务外发布事件（ADR-028 规则 4）=====

        log.info("解析完成: id={}, parser={}, textLength={}",
                documentId, lastParserName,
                parseResult.textContent() != null ? parseResult.textContent().length() : 0);

        eventPublisher.publishEvent(new TextbookParsedEvent(this, documentId));
        return parseResult;
    }

    /**
     * 短事务：解析失败 → 回退状态到 UPLOADED + failReason（ADR-028 规则 1 + ADR-029 补偿）。
     */
    private void failParsing(Long documentId, String reason) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> {
            textbookRepository.findById(documentId).ifPresent(doc -> {
                doc.setStatus(FileStatus.UPLOADED);
                doc.setFailReason(truncate(reason, 300));
                textbookRepository.save(doc);
                log.info("解析失败，状态回退 UPLOADED: id={}", documentId);
            });
        });
    }

    /**
     * 短事务：解析完成 → 状态 PARSED + 文本内容（ADR-028 规则 1）。
     */
    private void completeParsing(Long documentId, ParseResult parseResult) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> {
            textbookRepository.findById(documentId).ifPresent(doc -> {
                doc.setStatus(FileStatus.PARSED);
                doc.setTextContent(parseResult.textContent());
                doc.setPageCount(parseResult.pageCount());
                doc.setFailReason(null);
                textbookRepository.save(doc);
            });
        });
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

    // ======================== 删除（短事务→事务外事件） ========================

    @Override
    public void deleteTextBook(Long id) {
        // ===== 阶段 1：短事务 — 状态→DELETING（ADR-028 规则 1）=====

        TransactionTemplate tx = new TransactionTemplate(txManager);
        Boolean deleted = tx.execute(status -> {
            TextbookDO doc = textbookRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                            "文档不存在: id=" + id));

            if (doc.getStatus() == FileStatus.DELETING) {
                log.info("文档已在 DELETING 状态，跳过重复删除: id={}", id);
                return false;
            }

            doc.setStatus(FileStatus.DELETING);
            textbookRepository.save(doc);
            log.info("文档进入 DELETING 状态: id={}", id);
            return true;
        });

        // ===== 阶段 2：事务外发布事件（ADR-028 规则 4）=====

        if (Boolean.TRUE.equals(deleted)) {
            TextbookDO doc = textbookRepository.findById(id).orElseThrow();
            eventPublisher.publishEvent(new TextbookDeletedEvent(
                    this, id, doc.getFilePath(), doc.getDocumentNo()));
            log.info("已发布 TextbookDeletedEvent: id={}", id);
        }
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

        var docOpt = textbookRepository.findById(documentId);
        if (docOpt.isEmpty()) {
            log.info("文档已不存在（幂等跳过）: id={}", documentId);
            return;
        }
        TextbookDO doc = docOpt.get();

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

        textbookRepository.delete(doc);
        log.info("文档已物理删除: id={}, filePath={}", documentId, filePath);
    }

    /**
     * 保存图谱构建/抽取失败原因到文档记录（ADR-028 规则 5+6）。
     *
     * <p>独立 {@code @Transactional} 方法，供 {@code TextbookParsedEventListener} 通过
     * {@code TextbookService} 代理调用，消除监听器内自调用的 AOP 穿透问题。</p>
     */
    @Override
    @Transactional
    public void saveFailReason(Long documentId, String errorMessage) {
        textbookRepository.findById(documentId).ifPresent(doc -> {
            doc.setFailReason("LLM抽取失败: " + truncate(errorMessage, 300));
            textbookRepository.save(doc);
            log.info("failReason 已保存: documentId={}", documentId);
        });
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

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.graphnexus.common.security.UserPrincipal principal) {
            return principal.getUserId();
        }
        return 0L;
    }
}