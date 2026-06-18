package com.graphnexus.application.file.core.pipeline;

import com.graphnexus.api.file.dto.core.FileVO;
import com.graphnexus.application.file.core.model.FileBO;
import com.graphnexus.application.file.parse.model.FileParseRequest;
import com.graphnexus.application.file.parse.model.FileParseType;
import com.graphnexus.application.file.parse.model.ParseResult;
import com.graphnexus.application.file.parse.parser.FileParser;
import com.graphnexus.application.file.parse.parser.FileParserRegistry;
import com.graphnexus.application.graph.core.model.ExtractionResultBO;
import com.graphnexus.application.graph.core.service.GraphService;
import com.graphnexus.application.graph.fusion.service.FusionService;
import com.graphnexus.application.graph.metrics.event.GraphChangedEvent;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.common.util.Md5Utils;
import com.graphnexus.infrastructure.mysql.file.entity.FileDO;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.mysql.file.repository.FileRepository;
import com.graphnexus.infrastructure.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档处理 Pipeline — 同步串联 上传→解析→抽取→融合 全链路。
 *
 * <p>状态机管理见 {@link FileStatus} v2。失败回退 + 断点续跑见 retry()。
 * 见 DESIGN §2.1 同步链路 + §3 状态机。</p>
 *
 * @author Jay
 * @date 2026/06/18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessingPipeline implements FileProcessingPipeline {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB

    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final FileParserRegistry fileParserRegistry;
    private final GraphService graphService;
    private final FusionService fusionService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public FileParseType supportedType() {
        return FileParseType.DOCUMENT;
    }

    // ======================== 全链路同步处理 ========================

    @Override
    @Transactional
    public Object process(MultipartFile file, String subject) {
        String filename = sanitizeFileName(file.getOriginalFilename());

        // ① 查找解析器
        List<FileParser> parsers = fileParserRegistry.getParsers(filename);
        if (parsers.isEmpty()) {
            throw new BusinessException(ErrorCode.A0004, "不支持的文件类型: " + filename);
        }

        // ② 读取字节 + MD5 + 去重
        byte[] rawBytes = readBytes(file);
        String documentNo = Md5Utils.computeMd5(rawBytes);
        if (fileRepository.findIdByDocumentNoAndSubjectAndIsDeletedFalse(documentNo, subject).isPresent()) {
            throw new BusinessException(ErrorCode.A0007,
                    "文档内容重复: subject=" + subject + ", md5=" + documentNo);
        }

        // ③ 确定文件扩展名 + MinIO 路径
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : "";
        String minioPath = "textbooks/" + UUID.randomUUID() + ext;

        // ④ MinIO 上传
        try (InputStream inputStream = file.getInputStream()) {
            fileStorageService.uploadFile(inputStream, minioPath, file.getContentType());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.B0001, "文件上传失败", "文件存储服务暂时不可用");
        }

        // ⑤ DB insert: UPLOADED
        FileDO doc = FileDO.builder()
                .documentNo(documentNo)
                .name(filename)
                .subject(subject)
                .fileType(parsers.get(0).supportedType())
                .fileSize(file.getSize())
                .minioPath(minioPath)
                .status(FileStatus.UPLOADED)
                .build();
        doc = fileRepository.save(doc);
        Long docId = doc.getId();
        log.info("文档上传成功: id={}, name={}, type={}", docId, filename, doc.getFileType());

        // ⑥ 解析 → PARSING → PARSED
        ParseResult parseResult = doParse(docId, rawBytes, filename, parsers);
        Set<String> kpNames = Collections.emptySet();

        // ⑦ 抽取 → EXTRACTING → EXTRACTED
        try {
            ExtractionResultBO extractResult = doExtract(docId);
            kpNames = extractKnowledgePointNames(docId);
        } catch (Exception e) {
            // 回退到 PARSED + failReason
            revertTo(docId, FileStatus.PARSED, "extraction failed: " + truncate(e.getMessage(), 300));
            throw toBusinessException(e);
        }

        // ⑧ 融合 → FUSING → COMPLETED
        try {
            doFuse(docId, kpNames, subject);
        } catch (Exception e) {
            // 回退到 EXTRACTED + failReason
            revertTo(docId, FileStatus.EXTRACTED, "fusion failed: " + truncate(e.getMessage(), 300));
            throw toBusinessException(e);
        }

        // ⑨ 完成 + 发布事件
        updateStatus(docId, FileStatus.COMPLETED);
        eventPublisher.publishEvent(new GraphChangedEvent(this));
        log.info("文档处理全链路完成: id={}, status=COMPLETED", docId);

        return toBO(docId);
    }

    // ======================== 断点续跑 ========================

    @Override
    @Transactional
    public Object retry(Long documentId) {
        FileDO doc = fileRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006, "文档不存在: id=" + documentId));

        FileStatus status = doc.getStatus();
        log.info("retry 触发: id={}, currentStatus={}", documentId, status);

        switch (status) {
            case UPLOADED:
                // 从头开始
                return fullRetry(documentId);
            case PARSED:
                // 从抽取继续
                return retryFromExtract(documentId);
            case EXTRACTED:
                // 从融合继续
                return retryFromFuse(documentId);
            case FAILED:
                // 清除 failReason，从解析开始
                clearFailReason(documentId);
                return fullRetry(documentId);
            case COMPLETED:
                // 重新处理
                return fullRetry(documentId);
            case PARSING:
            case EXTRACTING:
            case FUSING:
                throw new BusinessException(ErrorCode.A0009,
                        "文档正在处理中，无法重试: status=" + status);
            case DELETING:
                throw new BusinessException(ErrorCode.A0006,
                        "文档正在删除中，无法重试: id=" + documentId);
            default:
                throw new BusinessException(ErrorCode.A0009,
                        "不支持的 retry 状态: " + status);
        }
    }

    // ======================== 内部方法 ========================

    private ParseResult doParse(Long docId, byte[] rawBytes, String filename, List<FileParser> parsers) {
        updateStatus(docId, FileStatus.PARSING);

        // 遍历解析器链（主→兜底），只 catch 解析本身的异常
        ParseResult parseResult = null;
        String lastParserName = "unknown";
        Exception lastError = null;
        for (FileParser parser : parsers) {
            lastParserName = parser.getClass().getSimpleName();
            try {
                FileParseRequest request = new FileParseRequest(
                        null, filename, null, rawBytes);
                parseResult = ((DocumentParser) parser).parse(request.rawBytes());
                lastError = null;
                break;
            } catch (Exception e) {
                lastError = e;
                log.warn("解析器 {} 失败: id={}, error={}",
                        lastParserName, docId, e.getMessage());
            }
        }

        if (parseResult == null) {
            // 所有解析器都失败 → 回退到 UPLOADED
            revertTo(docId, FileStatus.UPLOADED, "all parsers failed: " + truncate(
                    lastError != null ? lastError.getMessage() : "unknown", 300));
            throw new BusinessException(ErrorCode.A0004, "文档解析失败（所有解析器均失败）");
        }

        // 解析成功 → PARSED（在 try-catch 外，状态更新失败会正确传播）
        updateAfterParse(docId, parseResult, lastParserName);
        return parseResult;
    }

    private ExtractionResultBO doExtract(Long docId) {
        updateStatus(docId, FileStatus.EXTRACTING);
        ExtractionResultBO result = graphService.extract(docId);
        updateStatus(docId, FileStatus.EXTRACTED);
        log.info("抽取完成: id={}, entities={}, kps={}",
                docId, result.getEntityCount(), result.getKnowledgePointCount());
        return result;
    }

    private void doFuse(Long docId, Set<String> kpNames, String subject) {
        updateStatus(docId, FileStatus.FUSING);
        if (!kpNames.isEmpty()) {
            fusionService.fuseIncremental(new ArrayList<>(kpNames), subject);
        } else {
            // 无 KP 时仍然执行全量融合
            fusionService.fuseFull();
        }
        log.info("融合完成: id={}, kpCount={}", docId, kpNames.size());
    }

    private Object fullRetry(Long documentId) {
        FileDO doc = fileRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006, "文档不存在: id=" + documentId));

        // 重新解析
        try (InputStream is = fileStorageService.getFile(doc.getMinioPath())) {
            byte[] rawBytes = toByteArray(is);
            String filename = doc.getName();
            List<FileParser> parsers = fileParserRegistry.getParsers(filename);
            doParse(documentId, rawBytes, filename, parsers);
        } catch (IOException e) {
            revertTo(documentId, FileStatus.UPLOADED, "retry read failed: " + e.getMessage());
            throw new BusinessException(ErrorCode.B0001, "文件读取失败", "文件存储服务暂时不可用");
        }

        // 继续抽取 + 融合
        Set<String> kpNames;
        try {
            doExtract(documentId);
            kpNames = extractKnowledgePointNames(documentId);
        } catch (Exception e) {
            revertTo(documentId, FileStatus.PARSED, "retry extraction failed: " + truncate(e.getMessage(), 300));
            throw toBusinessException(e);
        }

        try {
            doFuse(documentId, kpNames, doc.getSubject());
        } catch (Exception e) {
            revertTo(documentId, FileStatus.EXTRACTED, "retry fusion failed: " + truncate(e.getMessage(), 300));
            throw toBusinessException(e);
        }

        updateStatus(documentId, FileStatus.COMPLETED);
        eventPublisher.publishEvent(new GraphChangedEvent(this));
        log.info("retry 全链路完成: id={}", documentId);
        return toBO(documentId);
    }

    private Object retryFromExtract(Long documentId) {
        FileDO doc = fileRepository.findById(documentId).get();
        Set<String> kpNames;
        try {
            doExtract(documentId);
            kpNames = extractKnowledgePointNames(documentId);
        } catch (Exception e) {
            revertTo(documentId, FileStatus.PARSED, "retry extraction failed: " + truncate(e.getMessage(), 300));
            throw toBusinessException(e);
        }
        try {
            doFuse(documentId, kpNames, doc.getSubject());
        } catch (Exception e) {
            revertTo(documentId, FileStatus.EXTRACTED, "retry fusion failed: " + truncate(e.getMessage(), 300));
            throw toBusinessException(e);
        }
        updateStatus(documentId, FileStatus.COMPLETED);
        eventPublisher.publishEvent(new GraphChangedEvent(this));
        log.info("retry(从抽取)完成: id={}", documentId);
        return toBO(documentId);
    }

    private Object retryFromFuse(Long documentId) {
        FileDO doc = fileRepository.findById(documentId).get();
        Set<String> kpNames = extractKnowledgePointNames(documentId);
        try {
            doFuse(documentId, kpNames, doc.getSubject());
        } catch (Exception e) {
            revertTo(documentId, FileStatus.EXTRACTED, "retry fusion failed: " + truncate(e.getMessage(), 300));
            throw toBusinessException(e);
        }
        updateStatus(documentId, FileStatus.COMPLETED);
        eventPublisher.publishEvent(new GraphChangedEvent(this));
        log.info("retry(从融合)完成: id={}", documentId);
        return toBO(documentId);
    }

    // ======================== DB 操作 ========================

    private void updateStatus(Long docId, FileStatus target) {
        FileDO doc = fileRepository.findById(docId).orElseThrow();
        doc.getStatus().validateTransition(target);
        doc.setStatus(target);
        doc.setFailReason(null);
        fileRepository.saveAndFlush(doc);
    }

    private void updateAfterParse(Long docId, ParseResult result, String parserName) {
        FileDO doc = fileRepository.findById(docId).orElseThrow();
        doc.getStatus().validateTransition(FileStatus.PARSED);
        doc.setStatus(FileStatus.PARSED);
        doc.setTextContent(result.textContent());
        doc.setPageCount(result.pageCount());
        doc.setMetadataJson(toJson(result.metadata()));
        doc.setFailReason(null);
        fileRepository.save(doc);
        log.info("解析成功: id={}, parser={}, textLength={}",
                docId, parserName, result.textContent() != null ? result.textContent().length() : 0);
    }

    private void revertTo(Long docId, FileStatus target, String failReason) {
        FileDO doc = fileRepository.findById(docId).orElseThrow();
        doc.setStatus(target);
        doc.setFailReason(failReason);
        fileRepository.save(doc);
        log.warn("Pipeline 回退: id={}, toStatus={}, failReason={}", docId, target, failReason);
    }

    private void clearFailReason(Long docId) {
        FileDO doc = fileRepository.findById(docId).orElseThrow();
        doc.setFailReason(null);
        fileRepository.save(doc);
    }

    // ======================== 工具方法 ========================

    private FileBO toBO(Long docId) {
        FileDO doc = fileRepository.findById(docId).orElseThrow();
        return FileBO.builder()
                .id(doc.getId())
                .documentNo(doc.getDocumentNo())
                .name(doc.getName())
                .subject(doc.getSubject())
                .fileType(doc.getFileType() != null ? doc.getFileType().name() : null)
                .fileSize(doc.getFileSize())
                .minioPath(doc.getMinioPath())
                .pageCount(doc.getPageCount())
                .textContent(doc.getTextContent())
                .metadataJson(doc.getMetadataJson())
                .status(doc.getStatus().name())
                .failReason(doc.getFailReason())
                .uploadedBy(doc.getUploadedBy())
                .createTime(doc.getCreateTime())
                .updateTime(doc.getUpdateTime())
                .build();
    }

    private Set<String> extractKnowledgePointNames(Long docId) {
        // 从 Neo4j 子图中提取 KP 名称用于增量融合
        try {
            var subgraph = graphService.getSubgraph(docId);
            if (subgraph == null || subgraph.getNodes() == null) return Collections.emptySet();
            return subgraph.getNodes().stream()
                    .filter(n -> "KnowledgePoint".equalsIgnoreCase(n.nodeType()))
                    .map(n -> n.properties() != null ? n.properties().getOrDefault("name", "") : "")
                    .filter(name -> !name.toString().isEmpty())
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("提取 KP 名称失败（不影响主流程）: id={}, error={}", docId, e.getMessage());
            return Collections.emptySet();
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.A0004, "文件读取失败", "上传文件无法读取，请重试");
        }
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

    private String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) return "unknown";
        String name = originalFilename.replaceAll("^.*[/\\\\]", "");
        return name.isBlank() ? "unknown" : name;
    }

    private String toJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "{}";
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
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private BusinessException toBusinessException(Exception e) {
        if (e instanceof BusinessException) return (BusinessException) e;
        return new BusinessException(ErrorCode.B0001, e.getMessage());
    }
}