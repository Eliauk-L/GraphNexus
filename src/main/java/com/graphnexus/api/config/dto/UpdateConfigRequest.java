package com.graphnexus.api.config.dto;

import lombok.Data;

/**
 * 更新配置请求 DTO。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Data
public class UpdateConfigRequest {

    /** 配置值。空字符串表示恢复默认值（仅非必填项允许）。 */
    private String configValue;
}