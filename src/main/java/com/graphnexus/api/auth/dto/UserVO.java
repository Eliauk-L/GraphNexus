package com.graphnexus.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户 VO。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    @Schema(description = "用户 ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "角色代码列表")
    private List<String> roles;

    @Schema(description = "账号状态 ENABLED/DISABLED")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}