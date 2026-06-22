package com.graphnexus.application.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户信息 BO（用于缓存 + 响应）。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {

    private Long userId;
    private String username;
    private String realName;
    private String status;
    private List<String> roles;
}