package com.graphnexus.application.file.textbook.parser.mineru.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * MinerU API 配置属性。
 *
 * <p>绑定 application.yml 中 {@code mineru.*} 配置项。</p>
 *
 * @author Jay
 * @date 2026/06/16
 */
@Data
@Component
@ConfigurationProperties(prefix = "mineru")
public class MinerUProperties {

    /** 是否启用 MinerU 解析（false 时直接走 PDFBox） */
    private boolean enabled = true;

    /** API 配置 */
    private Api api = new Api();

    /** 解析选项 */
    private Parse parse = new Parse();

    @Data
    public static class Api {

        /** MinerU API 基础 URL */
        private String baseUrl = "https://mineru.net";

        /** API 版本：v1（Agent 轻量，免 Token，默认）/ v4（精准解析，需 Token）/ custom（自部署） */
        private String version = "v1";

        /** 任务提交路径（v1: /api/v1/agent/parse/file，v4: /api/v4/file-urls/batch） */
        private String submitPath = "/api/v1/agent/parse/file";

        /** 轮询路径模板，{taskId} 占位符（v1: /api/v1/agent/parse/{taskId}，v4: /api/v4/extract-results/batch/{taskId}） */
        private String pollPathTemplate = "/api/v1/agent/parse/{taskId}";

        /** v4 API Token（Bearer 认证） */
        private String token;

        /** 模型版本：pipeline / vlm */
        private String modelVersion = "vlm";

        /** 解析器标识名（写入 metadata.parser） */
        private String parserName = "mineru-v1";

        /** 轮询超时 */
        private Duration pollTimeout = Duration.ofSeconds(300);

        /** 轮询间隔 */
        private Duration pollInterval = Duration.ofSeconds(3);
    }

    @Data
    public static class Parse {

        /** 是否开启公式识别 */
        private boolean enableFormula = true;

        /** 是否开启表格识别 */
        private boolean enableTable = true;

        /** 文档语言 */
        private String language = "ch";
    }
}