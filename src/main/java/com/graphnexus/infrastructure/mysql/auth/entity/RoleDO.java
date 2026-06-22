package com.graphnexus.infrastructure.mysql.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 角色定义 DO。预置 5 行，不支持运行时增删。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Entity
@Table(name = "role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleDO {

    /** 超级管理员（继承 TEACHER） */
    public static final String ADMIN = "ADMIN";
    /** 教师 */
    public static final String TEACHER = "TEACHER";
    /** 学生 */
    public static final String STUDENT = "STUDENT";
    /** 运维人员 */
    public static final String OPS_STAFF = "OPS_STAFF";
    /** 运营人员 */
    public static final String OPS_MANAGER = "OPS_MANAGER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 255)
    private String description;
}