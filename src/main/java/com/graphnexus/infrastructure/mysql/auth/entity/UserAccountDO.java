package com.graphnexus.infrastructure.mysql.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户账号 DO。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Entity
@Table(name = "user_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserAccountDO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "real_name", length = 128)
    private String realName;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AccountStatus status = AccountStatus.ENABLED;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    public enum AccountStatus {
        ENABLED, DISABLED
    }
}