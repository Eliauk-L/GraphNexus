package com.graphnexus.application.file.core.service.impl;

import com.graphnexus.application.file.core.model.FileBO;
import com.graphnexus.application.file.core.model.DeleteResultBO;
import com.graphnexus.application.file.upload.model.GradeRecordBO;
import com.graphnexus.application.file.upload.model.GradeUploadResultBO;
import com.graphnexus.application.file.parse.model.ParseResult;
import com.graphnexus.application.file.core.model.UpdateFileBO;
import com.graphnexus.application.file.parse.parser.MinerUDocumentParser;
import com.graphnexus.application.file.parse.parser.PdfBoxDocumentParser;
import com.graphnexus.application.file.core.service.FileService;
import com.graphnexus.application.file.upload.service.GradeService;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.common.util.Md5Utils;
import com.graphnexus.application.file.parse.parser.mineru.config.MinerUProperties;
import com.graphnexus.infrastructure.mysql.file.entity.FileDO;
import com.graphnexus.infrastructure.mysql.file.repository.FileRepository;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.neo4j.repository.GraphNodeRepository;
import com.graphnexus.infrastructure.storage.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
public class FileServiceImpl implements FileService {

    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB

    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final MinerUDocumentParser minerUDocumentParser;
    private final PdfBoxDocumentParser pdfBoxDocumentParser;
    private final MinerUProperties minerUProperties;
    private final GraphNodeRepository graphNodeRepository;
    private final GradeService gradeService;

    public FileServiceImpl(FileRepository fileRepository,
                               FileStorageService fileStorageService,
                               MinerUDocumentParser minerUDocumentParser,
                               PdfBoxDocumentParser pdfBoxDocumentParser,
                               MinerUProperties minerUProperties,
                               GraphNodeRepository graphNodeRepository,
                               GradeService gradeService) {
        this.fileRepository = fileRepository;
        this.fileStorageService = fileStorageService;
        this.minerUDocumentParser = minerUDocumentParser;
        this.pdfBoxDocumentParser = pdfBoxDocumentParser;
        this.minerUProperties = minerUProperties;
        this.graphNodeRepository = graphNodeRepository;
        this.gradeService = gradeService;
    }

    // ======================== 上传 ========================

    @Override
    @Transactional
    public FileBO upload(MultipartFile file, String subject) {
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
        String documentNo = Md5Utils.computeMd5(pdfBytes);

        // ④ 去重检查
        if (fileRepository.findIdByDocumentNoAndSubjectAndIsDeletedFalse(documentNo, subject).isPresent()) {
            throw new BusinessException(ErrorCode.A0007,
                    "文档内容重复: subject=" + subject + ", md5=" + documentNo);
        }

        // ⑤ 生成 MinIO 路径
        String minioPath = "textbooks/" + UUID.randomUUID() + ".pdf";

        // ⑥ 上传到 MinIO
        try (InputStream inputStream = file.getInputStream()) {
            fileStorageService.uploadFile(inputStream, minioPath, PDF_MIME_TYPE);
        } catch (IOException e) {
            log.error("MinIO 上传失败: minioPath={}", minioPath, e);
            throw new BusinessException(ErrorCode.B0001, "文件上传失败", "文件存储服务暂时不可用");
        }

        // ⑦ 保存到 MySQL
        FileDO doc = FileDO.builder()
                .documentNo(documentNo)
                .name(sanitizeFileName(file.getOriginalFilename()))
                .subject(subject)
                .fileSize(file.getSize())
                .minioPath(minioPath)
                .status(FileStatus.UPLOADED)
                .uploadedBy(null) // v1 无用户体系
                .build();
        doc = fileRepository.save(doc);

        log.info("文档上传成功: id={}, name={}, subject={}, md5={}", doc.getId(), doc.getName(), subject, documentNo);
        return toBO(doc);
    }

    // ======================== 解析 ========================

    @Override
    @Transactional
    public ParseResult process(Long documentId) {
        // ① 查询文档
        FileDO doc = fileRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                        "文档不存在: id=" + documentId));

        // ② 校验并更新状态 → PROCESSING
        doc.getStatus().validateTransition(FileStatus.PARSING);
        doc.setStatus(FileStatus.PARSING);
        fileRepository.save(doc);

        // ③ 从 MinIO 获取文件
        try (InputStream is = fileStorageService.getFile(doc.getMinioPath())) {
            // ④ 读入 byte[]
            byte[] pdfBytes = toByteArray(is);

            // ⑤ 调用解析器（MinerU 优先 → PDFBox 兜底）
            ParseResult result;
            Map<String, String> parseMetadata = new LinkedHashMap<>();
            String parserUsed;

            if (minerUProperties.isEnabled()) {
                try {
                    result = minerUDocumentParser.parse(pdfBytes);
                    parseMetadata.putAll(result.metadata());
                    String parserName = minerUProperties.getApi().getParserName();
                    parseMetadata.put("parser", parserName);
                    parserUsed = parserName;
                    log.info("MinerU v4 解析成功: docId={}", doc.getId());
                } catch (Exception mineruEx) {
                    log.warn("MinerU 解析失败，fallback to PDFBox: docId={}, error={}",
                            doc.getId(), mineruEx.getMessage());
                    try {
                        result = pdfBoxDocumentParser.parse(pdfBytes);
                        parseMetadata.putAll(result.metadata());
                        parseMetadata.put("parser", "pdfbox-fallback");
                        parseMetadata.put("fallbackReason", truncate(mineruEx.getMessage(), 200));
                        parserUsed = "pdfbox-fallback";
                        log.info("PDFBox 兜底解析成功: docId={}", doc.getId());
                    } catch (Exception pdfBoxEx) {
                        // 两次都失败 → FAILED
                        doc.setStatus(FileStatus.FAILED);
                        String failReason = String.format(
                                "MinerU: %s; PDFBox: %s",
                                truncate(mineruEx.getMessage(), 200),
                                truncate(pdfBoxEx.getMessage(), 200));
                        doc.setFailReason(failReason);
                        fileRepository.save(doc);
                        log.error("MinerU + PDFBox 双失败: docId={}, failReason={}",
                                doc.getId(), failReason);
                        throw new BusinessException(ErrorCode.A0004,
                                "文档解析失败（MinerU + PDFBox 均失败）",
                                failReason);
                    }
                }
            } else {
                log.info("MinerU 已禁用，直接使用 PDFBox: docId={}", doc.getId());
                result = pdfBoxDocumentParser.parse(pdfBytes);
                parseMetadata.putAll(result.metadata());
                parseMetadata.put("parser", "pdfbox-direct");
                parserUsed = "pdfbox-direct";
            }

            // ⑥ 更新 DO
            doc.setTextContent(result.textContent());
            doc.setPageCount(result.pageCount());
            doc.setMetadataJson(toJson(parseMetadata));
            doc.setStatus(FileStatus.COMPLETED);
            doc.setFailReason(null);
            fileRepository.save(doc);

            log.info("文档解析完成: id={}, parser={}, pages={}, textLength={}",
                    doc.getId(), parserUsed, result.pageCount(),
                    result.textContent() != null ? result.textContent().length() : 0);
            return new ParseResult(result.textContent(), result.pageCount(), parseMetadata);

        } catch (BusinessException e) {
            // 解析失败 → 标记 FAILED
            doc.setStatus(FileStatus.FAILED);
            doc.setFailReason(e.getMessage());
            fileRepository.save(doc);
            log.error("文档解析失败: id={}", doc.getId(), e);
            throw e;

        } catch (IOException e) {
            doc.setStatus(FileStatus.FAILED);
            doc.setFailReason("文件读取失败: " + e.getMessage());
            fileRepository.save(doc);
            log.error("MinIO 文件读取失败: id={}, minioPath={}", doc.getId(), doc.getMinioPath(), e);
            throw new BusinessException(ErrorCode.B0001, "文件读取失败", "文件存储服务暂时不可用");
        }
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
        // 幂等：允许 DELETING 状态的文档重复删除，从中断点继续
        FileDO doc = fileRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                        "文档不存在: id=" + id));

        // 已逻辑删除 → 幂等返回
        if (doc.getIsDeleted() == 1) {
            log.info("文档已删除（幂等跳过）: id={}", id);
            return;
        }

        // ① 进入/保持 DELETING 状态，标记删除意图
        if (doc.getStatus() != FileStatus.DELETING) {
            doc.setStatus(FileStatus.DELETING);
            fileRepository.saveAndFlush(doc);
            log.info("文档进入 DELETING 状态: id={}", id);
        } else {
            log.info("文档已在 DELETING 状态，从中断点继续: id={}", id);
        }

        // ② MinIO 物理删除（幂等：文件不存在时跳过）
        try {
            fileStorageService.deleteFile(doc.getMinioPath());
        } catch (Exception e) {
            log.warn("MinIO 文件删除失败（可能已被删除，忽略继续）: path={}, error={}",
                    doc.getMinioPath(), e.getMessage());
        }

        // ③ Neo4j 图谱删除（幂等：无极节点时无影响）
        graphNodeRepository.deleteByDocumentId(String.valueOf(id));

        // ④ 全部组件清理完成，逻辑删除
        doc.markDeleted();
        fileRepository.save(doc);

        log.info("文档已删除: id={}, minioPath={}", doc.getId(), doc.getMinioPath());
    }

    // ======================== CSV 成绩委托 ========================

    @Override
    public GradeUploadResultBO uploadGradeCsv(MultipartFile file, String subject) {
        return gradeService.uploadGradeCsv(file, subject);
    }

    @Override
    public DeleteResultBO deleteGradeByExamNo(String examNo) {
        return gradeService.deleteByExamNo(examNo);
    }

    @Override
    public List<GradeRecordBO> queryGradeByExam(String examNo) {
        return gradeService.queryByExam(examNo);
    }

    // ======================== 工具方法 ========================

    /**
     * FileDO → FileBO 转换。
     */
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

    /**
     * 截断字符串至指定长度（用于 failReason，避免 DB 字段溢出）。
     */
    private String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}