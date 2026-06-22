package com.graphnexus.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 认证主体 —— 实现 Spring Security UserDetails。
 *
 * <p>ADMIN 角色自动继承 TEACHER 权限（见 ADR-038）。</p>
 *
 * @author Jay
 * @date 2026/06/22
 */
public class UserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final String realName;
    private final String status;
    private final List<String> roles;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long userId, String username, String password,
                         String realName, String status, List<String> roles) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.realName = realName;
        this.status = status;
        this.roles = roles;
        this.authorities = buildAuthorities(roles);
    }

    /**
     * 构建 GrantedAuthority 列表。ADMIN 自动追加 ROLE_TEACHER。
     */
    private static Collection<? extends GrantedAuthority> buildAuthorities(List<String> roles) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        // ADMIN 继承 TEACHER（见 ADR-038）
        if (roles.contains("ADMIN") && !roles.contains("TEACHER")) {
            authorities.add(new SimpleGrantedAuthority("ROLE_TEACHER"));
        }
        return authorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return "ENABLED".equals(status);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // ── 业务 getter ──

    public Long getUserId() {
        return userId;
    }

    public String getRealName() {
        return realName;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getRoles() {
        return roles;
    }
}