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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 教材文档上传服务 — 仅入库 {@code text_book} 表，不做后续处理。
 *
 * <p>校验 → MD5 去重 → MinIO 存储 → DB insert（status=UPLOADED）。
 * 解析由前端主动调用 {@code POST /parse/{id}}，抽取/融合走独立的 Graph API。</p>
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

    @Override
    @Transactional
    public Object upload(MultipartFile file, String subject) {
        String filename = sanitizeFileName(file.getOriginalFilename());

        // ① 查找解析器（验证文件类型）
        List<FileParser> parsers = fileParserRegistry.getParsers(filename, FileParser.BIZ_TEXTBOOK);
        if (parsers.isEmpty()) {
            throw new BusinessException(ErrorCode.A0004, "不支持的文件类型: " + filename);
        }

        // ② 读取字节 + MD5
        byte[] rawBytes = readBytes(file);
        String documentNo = Md5Utils.computeMd5(rawBytes);

        // ③ 分离文件名与扩展名
        String ext = "";
        String nameOnly = filename;
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx > 0) {
            ext = filename.substring(dotIdx);
            nameOnly = filename.substring(0, dotIdx);
        }

        // ④ MinIO 对象键（以 documentNo 为 key，内容寻址）
        String objectKey = "textbooks/" + documentNo + ext;
        String filePath = fileStorageService.getFileUrl(objectKey);

        // ⑤ 检查是否已有相同内容文件（复用 MinIO 路径，跳过上传）
        var existing = textbookRepository.findFirstByDocumentNoAndIsDeletedAndStatusNotOrderByCreateTimeAsc(
                documentNo, 0, FileStatus.DELETING);
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

        // ⑥ DB insert: UPLOADED（仅入库，不做后续处理）
        String fileType = ext.isEmpty() ? "" : ext.substring(1).toLowerCase();
        TextbookDO doc = TextbookDO.builder()
                .documentNo(documentNo)
                .name(nameOnly)
                .subject(subject)
                .fileType(fileType)
                .fileSize(file.getSize())
                .filePath(filePath)
                .status(FileStatus.UPLOADED)
                .build();
        doc = textbookRepository.save(doc);
        log.info("教材已上传入库: id={}, name={}, type={}, filePath={}, status=UPLOADED",
                doc.getId(), nameOnly, fileType, filePath);

        return toBO(doc.getId());
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