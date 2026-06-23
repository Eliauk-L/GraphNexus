package com.graphnexus.application.file.textbook.service;

import com.graphnexus.application.file.upload.UploadService;

import com.graphnexus.application.file.textbook.model.TextbookBO;
import com.graphnexus.application.file.parse.FileParser;
import com.graphnexus.application.file.parse.FileParserRegistry;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.common.util.Md5Utils;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import com.graphnexus.infrastructure.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 教材文档上传服务 — MinIO 上传（无事务）+ DB insert（短事务）。
 *
 * <p>校验 → MD5 去重 → MinIO 存储 → DB insert（status=UPLOADED）。
 * 解析由前端主动调用 {@code POST /parse/{id}}，抽取/融合走独立的 Graph API。</p>
 *
 * <p><b>事务策略（ADR-028）</b>：MinIO 文件 I/O 不在事务内（规则 2），
 * DB insert 通过 {@link TransactionTemplate} 在短事务中执行（规则 1）。</p>
 *
 * @author Jay
 * @date 2026/06/18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextbookUploadService implements UploadService {

    private final TextbookRepository textbookRepository;
    private final FileStorageService fileStorageService;
    private final FileParserRegistry fileParserRegistry;
    private final PlatformTransactionManager txManager;

    @Override
    public Object upload(MultipartFile file, String subject, Long uploadedBy) {
        // ===== 阶段 1：数据准备（无事务）=====

        String filename = sanitizeFileName(file.getOriginalFilename());

        List<FileParser> parsers = fileParserRegistry.getParsers(filename, FileParser.BIZ_TEXTBOOK);
        if (parsers.isEmpty()) {
            throw new BusinessException(ErrorCode.A0004, "不支持的文件类型: " + filename);
        }

        byte[] rawBytes = readBytes(file);
        String documentNo = Md5Utils.computeMd5(rawBytes);

        String ext = "";
        final String nameOnly;
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx > 0) {
            ext = filename.substring(dotIdx);
            nameOnly = filename.substring(0, dotIdx);
        } else {
            nameOnly = filename;
        }

        String fileType = ext.isEmpty() ? "" : ext.substring(1).toLowerCase();

        // ===== 阶段 2：MinIO 上传（无事务 · ADR-028 规则 2）=====

        String objectKey = "textbooks/" + documentNo + ext;
        String filePath = fileStorageService.getFileUrl(objectKey);

        var existing = textbookRepository.findFirstByDocumentNoAndStatusNotOrderByCreateTimeAsc(
                documentNo, FileStatus.DELETING);
        if (existing.isPresent()) {
            filePath = existing.get().getFilePath();
            log.info("内容重复文件，复用 MinIO 文件: documentNo={}, filePath={}", documentNo, filePath);
        } else {
            try (InputStream inputStream = file.getInputStream()) {
                fileStorageService.uploadFile(inputStream, objectKey, file.getContentType());
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.B0001, "文件上传失败", "文件存储服务暂时不可用");
            }
        }

        // ===== 阶段 3：DB insert 短事务（ADR-028 规则 1）=====

        final String finalFilePath = filePath;
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        return txTemplate.execute(status -> {
            TextbookDO doc = TextbookDO.builder()
                    .documentNo(documentNo)
                    .name(nameOnly)
                    .subject(subject)
                    .fileType(fileType)
                    .fileSize(file.getSize())
                    .filePath(finalFilePath)
                    .status(FileStatus.UPLOADED)
                    .uploadedBy(uploadedBy)
                    .build();
            doc = textbookRepository.save(doc);
            log.info("教材已上传入库: id={}, name={}, type={}, filePath={}, status=UPLOADED",
                    doc.getId(), nameOnly, fileType, finalFilePath);
            return toBO(doc.getId());
        });
    }

    // ======================== 工具方法 ========================

    private TextbookBO toBO(Long docId) {
        TextbookDO doc = textbookRepository.findById(docId).orElseThrow();
        return TextbookBO.builder()
                .id(doc.getId())
                .documentNo(doc.getDocumentNo())
                .name(doc.getName())
                .subject(doc.getSubject())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .filePath(doc.getFilePath())
                .pageCount(doc.getPageCount())
                .textContent(doc.getTextContent())
                .status(doc.getStatus().name())
                .failReason(doc.getFailReason())
                .uploadedBy(doc.getUploadedBy())
                .createTime(doc.getCreateTime())
                .build();
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.A0004, "文件读取失败", "上传文件无法读取，请重试");
        }
    }

    private String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) return "unknown";
        String name = originalFilename.replaceAll("^.*[/\\\\]", "");
        return name.isBlank() ? "unknown" : name;
    }
}