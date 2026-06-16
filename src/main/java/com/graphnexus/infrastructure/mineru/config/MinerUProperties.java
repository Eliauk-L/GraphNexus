package com.graphnexus.infrastructure.mineru.config;

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

        /** v4 API Token（Bearer 认证） */
        private String token;

        /** 模型版本：pipeline / vlm */
        private String modelVersion = "vlm";

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