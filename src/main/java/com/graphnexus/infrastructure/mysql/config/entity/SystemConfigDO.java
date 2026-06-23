package com.graphnexus.infrastructure.mysql.config.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统配置 DO — 存储可动态管理的配置项。
 *
 * <p>config_value 为 NULL 时表示未自定义，业务逻辑使用 yml 默认值。
 * configType 约束前端编辑控件类型，validationRule 为 JSON 格式的校验规则。</p>
 *
 * @author Jay
 * @date 2026/06/23
 */
@Entity
@Table(name = "system_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfigDO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, unique = true, length = 128)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "config_type", nullable = false, length = 16)
    private String configType;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(name = "config_name", nullable = false, length = 64)
    private String configName;

    @Column(length = 256)
    @Builder.Default
    private String description = "";

    @Column(name = "default_value", length = 512)
    private String defaultValue;

    @Column(name = "required", nullable = false)
    @Builder.Default
    private Boolean required = false;

    @Column(name = "validation_rule", columnDefinition = "JSON")
    private String validationRule;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

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
}