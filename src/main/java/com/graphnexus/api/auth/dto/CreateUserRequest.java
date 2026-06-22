package com.graphnexus.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建用户请求 DTO。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @NotBlank
    @Size(min = 2, max = 64)
    @Schema(description = "用户名", example = "new_teacher")
    private String username;

    @NotBlank
    @Size(min = 6, max = 128)
    @Schema(description = "密码", example = "Temp@123")
    private String password;

    @NotBlank
    @Schema(description = "真实姓名", example = "张老师")
    private String realName;

    @Schema(description = "角色代码列表", example = "[\"TEACHER\"]")
    private List<String> roles;
}