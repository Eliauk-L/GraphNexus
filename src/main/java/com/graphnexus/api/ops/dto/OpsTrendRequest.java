package com.graphnexus.api.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 运营趋势查询请求 DTO。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Data
@Schema(description = "运营趋势查询请求")
public class OpsTrendRequest {

    @NotBlank(message = "指标名不能为空")
    @Schema(description = "指标名", example = "login_count")
    private String metric;

    @NotBlank(message = "时间粒度不能为空")
    @Schema(description = "时间粒度：day/week/month", example = "day")
    private String granularity;

    @Min(1)
    @Max(365)
    @Schema(description = "时间范围（天数）", example = "30")
    private int range = 30;
}