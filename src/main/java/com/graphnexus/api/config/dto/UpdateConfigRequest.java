package com.graphnexus.api.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新配置请求 DTO。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Data
public class UpdateConfigRequest {

    @NotBlank(message = "配置值不能为空")
    private String configValue;
}