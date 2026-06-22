package com.graphnexus.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色 VO。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleVO {

    @Schema(description = "角色代码", example = "ADMIN")
    private String code;

    @Schema(description = "角色中文名", example = "超级管理员")
    private String name;
}