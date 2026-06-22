package com.graphnexus.infrastructure.mysql.auth.repository;

import com.graphnexus.infrastructure.mysql.auth.entity.RoleDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 角色 Repository。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Repository
public interface RoleRepository extends JpaRepository<RoleDO, Long> {

    Optional<RoleDO> findByCode(String code);

    List<RoleDO> findByCodeIn(List<String> codes);
}