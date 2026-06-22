package com.graphnexus.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 更新用户请求 DTO。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Schema(description = "真实姓名", example = "新名字")
    private String realName;

    @Schema(description = "新密码（为空则不修改）")
    private String password;

    @Schema(description = "角色代码列表")
    private List<String> roles;

    @Schema(description = "账号状态 ENABLED/DISABLED")
    private String status;
}