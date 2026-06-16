package com.graphnexus.application.document.parser;

import com.graphnexus.application.document.model.ParseResult;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mineru.client.MinerUClient;
import com.graphnexus.infrastructure.mineru.config.MinerUProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MinerU v4 文档解析器实现。
 *
 * <p>实现 {@link DocumentParser} 接口，通过 MinerU v4 精准解析 API（vlm 模型）
 * 将 PDF 转换为 Markdown，公式以 LaTeX 格式保留（如 {@code $E=mc^2$}）。
 * 所有异常向上抛 {@link BusinessException}，由调用方
 * {@code DocumentServiceImpl} 统一 catch 后 fallback 到 {@link PdfBoxDocumentParser}。</p>
 *
 * <p>v4 API 调用链：submitBatch → uploadFile → pollBatchResult → downloadAndExtractMarkdown</p>
 *
 * @author Jay
 * @date 2026/06/16
 * @see PdfBoxDocumentParser
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinerUDocumentParser implements DocumentParser {

    private final MinerUClient minerUClient;
    private final MinerUProperties properties;

    /**
     * 使用 MinerU v4 API 解析 PDF 字节数组。
     *
     * @param pdfBytes PDF 文件的完整字节数组
     * @return 解析结果（Markdown 文本 + 页数 + 元信息）
     * @throws BusinessException 解析失败时抛出 C0001，由调用方 fallback 到 PDFBox
     */
    @Override
    public ParseResult parse(byte[] pdfBytes) {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.C0001, "MinerU 已禁用",
                    "mineru.enabled=false，应由调用方直接使用 PDFBox");
        }

        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new BusinessException(ErrorCode.A0004, "PDF 文件为空",
                    "上传的文件内容为空，无法解析");
        }

        long startTime = System.currentTimeMillis();
        log.info("MinerU v4 开始解析: fileSize={} bytes", pdfBytes.length);

        // ① 申请上传链接
        String fileName = "document-" + System.currentTimeMillis() + ".pdf";
        MinerUClient.BatchSubmitResult submitResult = minerUClient.submitBatch(fileName);

        // ② 上传文件到 MinerU OSS
        minerUClient.uploadFile(submitResult.fileUrl(), pdfBytes);

        // ③ 轮询等待解析完成
        MinerUClient.BatchPollResult pollResult = minerUClient.pollBatchResult(submitResult.batchId());

        if (pollResult.isFailed()) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("MinerU v4 解析失败: batchId={}, errMsg={}, elapsed={}ms",
                    submitResult.batchId(), pollResult.errMsg(), elapsed);
            throw new BusinessException(ErrorCode.C0001,
                    "MinerU 解析失败: " + pollResult.errMsg(),
                    "MinerU 无法解析该文档，将尝试 PDFBox 兜底");
        }

        // ④ 下载并解压 Markdown
        String markdown = minerUClient.downloadAndExtractMarkdown(pollResult.fullZipUrl());

        // ⑤ 构建返回结果
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("parser", "mineru-v4");
        metadata.put("model", properties.getApi().getModelVersion());
        metadata.put("batchId", submitResult.batchId());

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("MinerU v4 解析完成: batchId={}, textLength={}, elapsed={}ms",
                submitResult.batchId(), markdown.length(), elapsed);

        // pageCount 从 MinerU zip 中不易直接获取，设为 0（调用方以 textContent 为主）
        return new ParseResult(markdown, 0, metadata);
    }
}