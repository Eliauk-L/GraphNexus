package com.graphnexus.infrastructure.mysql.auth.repository;

import com.graphnexus.infrastructure.mysql.auth.entity.UserRoleDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户-角色关联 Repository。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRoleDO, Long> {

    List<UserRoleDO> findByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserRoleDO ur WHERE ur.userId = :userId")
    void deleteByUserId(Long userId);
}