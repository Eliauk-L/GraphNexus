package com.graphnexus.application.file.textbook.pipeline;

import com.graphnexus.application.file.textbook.model.FileBO;
import com.graphnexus.application.file.parse.FileParseRequest;
import com.graphnexus.application.file.textbook.model.TextbookFileType;
import com.graphnexus.application.file.parse.FileParseType;
import com.graphnexus.application.file.parse.ParseResult;
import com.graphnexus.application.file.parse.TextbookParser;
import com.graphnexus.application.file.parse.FileParser;
import com.graphnexus.application.file.parse.FileParserRegistry;
import com.graphnexus.application.file.textbook.service.TextbookUploadService;
import com.graphnexus.application.graph.core.model.ExtractionResultBO;
import com.graphnexus.application.graph.core.service.GraphService;
import com.graphnexus.application.graph.fusion.service.FusionService;
import com.graphnexus.application.graph.metrics.event.GraphChangedEvent;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import com.graphnexus.infrastructure.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档处理 Pipeline — 仅负责对已入库文件执行 解析→抽取→融合。
 *
 * <p>上传由 {@link TextbookUploadService} 负责。
 * processStored: 从 MinIO 读取文件 → 解析 → LLM 抽取 → 融合，支持断点续跑。
 * 见 DESIGN §2.1 + §3 状态机。</p>
 *
 * @author Jay
 * @date 2026/06/18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextbookProcessingPipeline implements FileProcessingPipeline {

    private final TextbookRepository textbookRepository;
    private final FileStorageService fileStorageService;
    private final FileParserRegistry fileParserRegistry;
    private final GraphService graphService;
    private final FusionService fusionService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public FileParseType supportedType() {
        return TextbookFileType.PDF;
    }

    // ======================== 处理已入库文件 ========================

    @Override
    @Transactional
    public Object processStored(Long documentId) {
        TextbookDO doc = textbookRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006, "文档不存在: id=" + documentId));

        FileStatus status = doc.getStatus();
        log.info("processStored 触发: id={}, currentStatus={}", documentId, status);

        switch (status) {
            case UPLOADED:
                // 从头开始处理
                return fullProcess(documentId);
            case PARSED:
                // 从抽取继续
                return processFromExtract(documentId);
            case EXTRACTED:
                // 从融合继续
                return processFromFuse(documentId);
            case FAILED:
                // 清除 failReason，从解析开始
                clearFailReason(documentId);
                return fullProcess(documentId);
            case COMPLETED:
                // 重新处理
                return fullProcess(documentId);
            case PARSING:
            case EXTRACTING:
            case FUSING:
                throw new BusinessException(ErrorCode.A0009,
                        "文档正在处理中，无法处理: status=" + status);
            case DELETING:
                throw new BusinessException(ErrorCode.A0006,
                        "文档正在删除中，无法处理: id=" + documentId);
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
                parseResult = ((TextbookParser) parser).parse(request.rawBytes());
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
        // 全量融合 — 确保文档 KPs 与成绩 KPs 跨源匹配
        fusionService.fuseFull();
        log.info("融合完成: id={}, kpCount={}", docId, kpNames.size());
    }

    private Object fullProcess(Long documentId) {
        TextbookDO doc = textbookRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006, "文档不存在: id=" + documentId));

        // 重新解析
        try (InputStream is = fileStorageService.getFile(
                fileStorageService.extractObjectKey(doc.getFilePath()))) {
            byte[] rawBytes = toByteArray(is);
            String filename = doc.getName();
            List<FileParser> parsers = fileParserRegistry.getParsers(filename, FileParser.BIZ_TEXTBOOK);
            doParse(documentId, rawBytes, filename, parsers);
        } catch (IOException e) {
            revertTo(documentId, FileStatus.UPLOADED, "processStored read failed: " + e.getMessage());
            throw new BusinessException(ErrorCode.B0001, "文件读取失败", "文件存储服务暂时不可用");
        }

        // 继续抽取 + 融合
        Set<String> kpNames;
        try {
            doExtract(documentId);
            kpNames = extractKnowledgePointNames(documentId);
        } catch (Exception e) {
            revertTo(documentId, FileStatus.PARSED, "extraction failed: " + truncate(e.getMessage(), 300));
            throw toBusinessException(e);
        }

        try {
            doFuse(documentId, kpNames, doc.getSubject());
        } catch (Exception e) {
            revertTo(documentId, FileStatus.EXTRACTED, "fusion failed: " + truncate(e.getMessage(), 300));
            throw toBusinessException(e);
        }

        updateStatus(documentId, FileStatus.COMPLETED);
        eventPublisher.publishEvent(new GraphChangedEvent(this));
        log.info("processStored 全链路完成: id={}", documentId);
        return toBO(documentId);
    }

    private Object processFromExtract(Long documentId) {
        TextbookDO doc = textbookRepository.findById(documentId).get();
        Set<String> kpNames;
        try {
            doExtract(documentId);
            kpNames = extractKnowledgePointNames(documentId);
        } catch (Exception e) {
            revertTo(documentId, FileStatus.PARSED, "extraction failed: " + truncate(e.getMessage(), 300));
            throw toBusinessException(e);
        }
        try {
            doFuse(documentId, kpNames, doc.getSubject());
        } catch (Exception e) {
            revertTo(documentId, FileStatus.EXTRACTED, "fusion failed: " + truncate(e.getMessage(), 300));
            throw toBusinessException(e);
        }
        updateStatus(documentId, FileStatus.COMPLETED);
        eventPublisher.publishEvent(new GraphChangedEvent(this));
        log.info("processStored(从抽取)完成: id={}", documentId);
        return toBO(documentId);
    }

    private Object processFromFuse(Long documentId) {
        TextbookDO doc = textbookRepository.findById(documentId).get();
        Set<String> kpNames = extractKnowledgePointNames(documentId);
        try {
            doFuse(documentId, kpNames, doc.getSubject());
        } catch (Exception e) {
            revertTo(documentId, FileStatus.EXTRACTED, "fusion failed: " + truncate(e.getMessage(), 300));
            throw toBusinessException(e);
        }
        updateStatus(documentId, FileStatus.COMPLETED);
        eventPublisher.publishEvent(new GraphChangedEvent(this));
        log.info("processStored(从融合)完成: id={}", documentId);
        return toBO(documentId);
    }

    // ======================== DB 操作 ========================

    private void updateStatus(Long docId, FileStatus target) {
        TextbookDO doc = textbookRepository.findById(docId).orElseThrow();
        doc.getStatus().validateTransition(target);
        doc.setStatus(target);
        doc.setFailReason(null);
        textbookRepository.saveAndFlush(doc);
    }

    private void updateAfterParse(Long docId, ParseResult result, String parserName) {
        TextbookDO doc = textbookRepository.findById(docId).orElseThrow();
        doc.getStatus().validateTransition(FileStatus.PARSED);
        doc.setStatus(FileStatus.PARSED);
        doc.setTextContent(result.textContent());
        doc.setPageCount(result.pageCount());
        doc.setFailReason(null);
        textbookRepository.save(doc);
        log.info("解析成功: id={}, parser={}, textLength={}",
                docId, parserName, result.textContent() != null ? result.textContent().length() : 0);
    }

    private void revertTo(Long docId, FileStatus target, String failReason) {
        TextbookDO doc = textbookRepository.findById(docId).orElseThrow();
        doc.setStatus(target);
        doc.setFailReason(failReason);
        textbookRepository.save(doc);
        log.warn("Pipeline 回退: id={}, toStatus={}, failReason={}", docId, target, failReason);
    }

    private void clearFailReason(Long docId) {
        TextbookDO doc = textbookRepository.findById(docId).orElseThrow();
        doc.setFailReason(null);
        textbookRepository.save(doc);
    }

    // ======================== 工具方法 ========================

    private FileBO toBO(Long docId) {
        TextbookDO doc = textbookRepository.findById(docId).orElseThrow();
        return FileBO.builder()
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

    private BusinessException toBusinessException(Exception e) {
        if (e instanceof BusinessException) return (BusinessException) e;
        return new BusinessException(ErrorCode.B0001, e.getMessage());
    }
}