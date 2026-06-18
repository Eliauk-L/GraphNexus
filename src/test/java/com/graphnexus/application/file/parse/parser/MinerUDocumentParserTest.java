package com.graphnexus.application.file.parse.parser;

import com.graphnexus.application.file.parse.model.ParseResult;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.application.file.parse.parser.mineru.client.MinerUApiClient;
import com.graphnexus.application.file.parse.parser.mineru.client.MinerUClient;
import com.graphnexus.application.file.parse.parser.mineru.config.MinerUProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MinerUDocumentParser 单元测试 — mock MinerUClient 验证编排逻辑。
 *
 * @author Jay
 * @date 2026/06/16
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MinerUDocumentParser 解析编排")
class MinerUDocumentParserTest {

    @Mock
    private MinerUClient minerUClient;

    @Mock
    private MinerUProperties properties;

    @InjectMocks
    private MinerUDocumentParser parser;

    @BeforeEach
    void setUp() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getApi()).thenReturn(mockApi());
    }

    private MinerUProperties.Api mockApi() {
        MinerUProperties.Api api = new MinerUProperties.Api();
        api.setModelVersion("vlm");
        return api;
    }

    @Test
    @DisplayName("正常解析流程：submit → upload → poll → download 全部成功")
    void parseShouldCompleteFullFlow() {
        when(minerUClient.submitTask(anyString()))
                .thenReturn(new MinerUApiClient.TaskSubmitResult("task-1", "https://oss.example.com/up"));
        when(minerUClient.pollTaskResult("task-1"))
                .thenReturn(new MinerUApiClient.TaskPollResult("done",
                        "https://cdn.example.com/result.md", null));
        when(minerUClient.downloadMarkdown("https://cdn.example.com/result.md"))
                .thenReturn("# Markdown\n\n$E=mc^2$");

        ParseResult result = parser.parse(new byte[]{1, 2, 3});

        assertThat(result.textContent()).isEqualTo("# Markdown\n\n$E=mc^2$");
        assertThat(result.metadata()).containsEntry("parser", "mineru-v1");
        assertThat(result.metadata()).containsEntry("model", "vlm");
        verify(minerUClient).submitTask(anyString());
        verify(minerUClient).uploadFile(anyString(), any(byte[].class));
        verify(minerUClient).pollTaskResult("task-1");
        verify(minerUClient).downloadMarkdown("https://cdn.example.com/result.md");
    }

    @Test
    @DisplayName("mineru.enabled=false 时抛异常（由上层 fallback）")
    void parseShouldThrowWhenDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> parser.parse(new byte[]{1, 2, 3}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MinerU 已禁用");
        verify(minerUClient, never()).submitTask(anyString());
    }

    @Test
    @DisplayName("空 PDF 字节数组应抛异常")
    void parseShouldThrowOnEmptyBytes() {
        assertThatThrownBy(() -> parser.parse(new byte[0]))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF 文件为空");
    }

    @Test
    @DisplayName("pollTaskResult 返回 failed 时抛异常")
    void parseShouldThrowWhenPollReturnsFailed() {
        when(minerUClient.submitTask(anyString()))
                .thenReturn(new MinerUApiClient.TaskSubmitResult("batch-1", "https://oss.example.com/up"));
        when(minerUClient.pollTaskResult("batch-1"))
                .thenReturn(new MinerUApiClient.TaskPollResult("failed",
                        null, "文件损坏无法解析"));

        assertThatThrownBy(() -> parser.parse(new byte[]{1, 2, 3}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MinerU 解析失败");
        verify(minerUClient, never()).downloadMarkdown(anyString());
    }

    @Test
    @DisplayName("submitTask 异常直接向上传播")
    void parseShouldPropagateSubmitException() {
        when(minerUClient.submitTask(anyString()))
                .thenThrow(new BusinessException(ErrorCode.C0001, "网络异常", "连接超时"));

        assertThatThrownBy(() -> parser.parse(new byte[]{1, 2, 3}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("网络异常");
        verify(minerUClient, never()).pollTaskResult(anyString());
    }

    @Test
    @DisplayName("null pdfBytes 应抛异常")
    void parseShouldThrowOnNullBytes() {
        assertThatThrownBy(() -> parser.parse((byte[]) null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF 文件为空");
    }
}