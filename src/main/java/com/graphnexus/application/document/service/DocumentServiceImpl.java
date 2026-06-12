package com.graphnexus.application.document.service;

import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.document.DocumentDO;
import com.graphnexus.infrastructure.mysql.document.DocumentRepository;
import com.graphnexus.infrastructure.mysql.document.DocumentStatus;
import com.graphnexus.infrastructure.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * 文档处理业务服务实现。
 *
 * <p>编排 L3 基础设施（MinIO + MySQL）完成 PDF 上传→解析→管理的完整业务流程。
 * 事务管理放在 L2（见 CONTEXT 分层异常传递规则）。</p>
 *
 * @author Jay
 * @date 2026/06/12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final DocumentParser documentParser;

    // ======================== 上传 ========================

    @Override
    @Transactional
    public DocumentBO upload(MultipartFile file, String subject) {
        // ① 校验 MIME type
        if (!PDF_MIME_TYPE.equals(file.getContentType())) {
            throw new BusinessException(ErrorCode.A0004, "不支持的文件类型: " + file.getContentType());
        }

        // ② 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.A0005,
                    "文件大小 " + file.getSize() + " 超过 50MB 限制");
        }

        // ③ 计算 MD5 内容指纹
        byte[] pdfBytes;
        try {
            pdfBytes = file.getBytes();
        } catch (IOException e) {
            log.error("读取上传文件失败", e);
            throw new BusinessException(ErrorCode.A0004, "文件读取失败", "上传文件无法读取，请重试");
        }
        String documentNo = computeMd5(pdfBytes);

        // ④ 去重检查
        if (documentRepository.existsByDocumentNoAndSubjectAndIsDeletedFalse(documentNo, subject)) {
            throw new BusinessException(ErrorCode.A0007,
                    "文档内容重复: subject=" + subject + ", md5=" + documentNo);
        }

        // ⑤ 生成 MinIO 路径
        String minioPath = UUID.randomUUID() + ".pdf";

        // ⑥ 上传到 MinIO
        try (InputStream inputStream = file.getInputStream()) {
            fileStorageService.uploadFile(inputStream, minioPath, PDF_MIME_TYPE);
        } catch (IOException e) {
            log.error("MinIO 上传失败: minioPath={}", minioPath, e);
            throw new BusinessException(ErrorCode.B0001, "文件上传失败", "文件存储服务暂时不可用");
        }

        // ⑦ 保存到 MySQL
        DocumentDO doc = DocumentDO.builder()
                .documentNo(documentNo)
                .name(sanitizeFileName(file.getOriginalFilename()))
                .subject(subject)
                .fileSize(file.getSize())
                .minioPath(minioPath)
                .status(DocumentStatus.UPLOADED)
                .uploadedBy(null) // v1 无用户体系
                .build();
        doc = documentRepository.save(doc);

        log.info("文档上传成功: id={}, name={}, subject={}, md5={}", doc.getId(), doc.getName(), subject, documentNo);
        return toBO(doc);
    }

    // ======================== 解析 ========================

    @Override
    @Transactional
    public ParseResult process(Long documentId) {
        // ① 查询文档
        DocumentDO doc = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                        "文档不存在: id=" + documentId));

        // ② 校验并更新状态 → PROCESSING
        doc.getStatus().validateTransition(DocumentStatus.PROCESSING);
        doc.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(doc);

        // ③ 从 MinIO 获取文件
        try (InputStream is = fileStorageService.getFile(doc.getMinioPath())) {
            // ④ 读入 byte[]
            byte[] pdfBytes = toByteArray(is);

            // ⑤ 调用解析器
            ParseResult result = documentParser.parse(pdfBytes);

            // ⑥ 更新 DO
            doc.setTextContent(result.textContent());
            doc.setPageCount(result.pageCount());
            doc.setMetadataJson(toJson(result.metadata()));
            doc.setStatus(DocumentStatus.COMPLETED);
            doc.setFailReason(null);
            documentRepository.save(doc);

            log.info("文档解析完成: id={}, pages={}, textLength={}",
                    doc.getId(), result.pageCount(),
                    result.textContent() != null ? result.textContent().length() : 0);
            return result;

        } catch (BusinessException e) {
            // 解析失败 → 标记 FAILED
            doc.setStatus(DocumentStatus.FAILED);
            doc.setFailReason(e.getMessage());
            documentRepository.save(doc);
            log.error("文档解析失败: id={}", doc.getId(), e);
            throw e;

        } catch (IOException e) {
            doc.setStatus(DocumentStatus.FAILED);
            doc.setFailReason("文件读取失败: " + e.getMessage());
            documentRepository.save(doc);
            log.error("MinIO 文件读取失败: id={}, minioPath={}", doc.getId(), doc.getMinioPath(), e);
            throw new BusinessException(ErrorCode.B0001, "文件读取失败", "文件存储服务暂时不可用");
        }
    }

    // ======================== 查询 ========================

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentBO> listDocuments(int pageNum, int pageSize) {
        return documentRepository
                .findByIsDeletedFalse(PageRequest.of(pageNum - 1, pageSize))
                .map(this::toBO);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentBO getDocument(Long id) {
        DocumentDO doc = documentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                        "文档不存在: id=" + id));
        return toBO(doc);
    }

    // ======================== 更新 ========================

    @Override
    @Transactional
    public DocumentBO updateDocument(Long id, UpdateDocumentBO bo) {
        DocumentDO doc = documentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                        "文档不存在: id=" + id));

        if (bo.getName() != null && !bo.getName().isBlank()) {
            doc.setName(bo.getName().trim());
        }

        doc = documentRepository.save(doc);
        log.info("文档已更新: id={}, name={}", doc.getId(), doc.getName());
        return toBO(doc);
    }

    // ======================== 删除 ========================

    @Override
    @Transactional
    public void deleteDocument(Long id) {
        DocumentDO doc = documentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                        "文档不存在: id=" + id));

        // 逻辑删除
        doc.markDeleted();
        documentRepository.save(doc);

        // MinIO 物理删除
        fileStorageService.deleteFile(doc.getMinioPath());

        log.info("文档已删除: id={}, minioPath={}", doc.getId(), doc.getMinioPath());
    }

    // ======================== 工具方法 ========================

    /**
     * DocumentDO → DocumentBO 转换。
     */
    private DocumentBO toBO(DocumentDO doc) {
        return DocumentBO.builder()
                .id(doc.getId())
                .documentNo(doc.getDocumentNo())
                .name(doc.getName())
                .subject(doc.getSubject())
                .fileSize(doc.getFileSize())
                .minioPath(doc.getMinioPath())
                .pageCount(doc.getPageCount())
                .textContent(doc.getTextContent())
                .metadataJson(doc.getMetadataJson())
                .status(doc.getStatus())
                .failReason(doc.getFailReason())
                .uploadedBy(doc.getUploadedBy())
                .createTime(doc.getCreateTime())
                .updateTime(doc.getUpdateTime())
                .build();
    }

    /**
     * 计算字节数组的 MD5（32 位小写十六进制）。
     */
    private String computeMd5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }

    /**
     * 清洗文件名（取原始文件名，null 时返回 unknown.pdf）。
     */
    private String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "unknown.pdf";
        }
        // 去除路径信息，只保留文件名
        String name = originalFilename.replaceAll("^.*[/\\\\]", "");
        return name.isBlank() ? "unknown.pdf" : name;
    }

    /**
     * InputStream → byte[]。
     */
    private byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    /**
     * Map → JSON 字符串（简单拼接，不引入 Jackson 仅在 L2 层做序列化）。
     */
    private String toJson(java.util.Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            sb.append("\"").append(escapeJson(entry.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}