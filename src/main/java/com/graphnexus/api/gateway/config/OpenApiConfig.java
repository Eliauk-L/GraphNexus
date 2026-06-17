package com.graphnexus.api.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 3.0 全局配置。
 *
 * <p>定义 API 文档元信息（标题、版本、描述、错误码体系说明），
 * 并通过 {@link GroupedOpenApi} 限定文档范围（排除 LlmController 调试端点）。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI graphNexusOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("GraphNexus API")
                        .version("1.0.0")
                        .description("""
                                ## GraphNexus 后端接口文档

                                ### 模块概览
                                - **文档处理** — PDF 教辅上传解析 + CSV 成绩导入管理
                                - **知识图谱** — 文档知识图谱抽取与文档子图查询
                                - **宽图谱融合** — 知识点融合合并 + MASTERS 掌握度聚合
                                - **图指标** — Neo4j GDS PageRank 与度中心性查询
                                - **智能问答** — 自然语言问答（图剪枝驱动 LLM 分析诊断）
                                - **图分析** — 剪枝子图可视化数据查询

                                ### 通用约定
                                - 统一响应体：`ApiResult<T>`（code/message/data/traceId/timestamp）
                                - 分页响应体：`PageResult<T>`（list/total/pageNum/pageSize）嵌套在 ApiResult 中
                                - 错误响应体：`ErrorResponse`（errorCode/errorMessage/userTip/traceId/timestamp）
                                - 所有时间字段格式：ISO 8601（`yyyy-MM-ddTHH:mm:ss`）
                                - 分页页码从 1 开始

                                ### 错误码体系（5位：来源(A/B/C) + 4位数字）
                                | 类别 | 错误码范围 | 说明 |
                                |------|-----------|------|
                                | A 用户端 | A0001–A0021 | 参数校验、资源不存在、权限、冲突等 |
                                | B 系统 | B0001–B0002 | 内部异常、服务不可用 |
                                | C 第三方 | C0001 | 外部LLM/API调用失败 |
                                """)
                        .contact(new Contact()
                                .name("Jay")
                                .email("jay@graphnexus.dev"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }

    /**
     * 业务 API 分组 —— 排除 LlmController 调试端点。
     * 仅扫描 api 包下的业务 Controller。
     */
    @Bean
    public GroupedOpenApi businessApi() {
        return GroupedOpenApi.builder()
                .group("business")
                .displayName("业务接口")
                .packagesToScan(
                        "com.graphnexus.api.document",
                        "com.graphnexus.api.graph",
                        "com.graphnexus.api.query",
                        "com.graphnexus.api.analysis"
                )
                .build();
    }
}