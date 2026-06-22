package com.graphnexus.infrastructure.mysql.auth.repository;

import com.graphnexus.infrastructure.mysql.auth.entity.UserAccountDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户账号 Repository。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Repository
public interface UserAccountRepository extends JpaRepository<UserAccountDO, Long> {

    Optional<UserAccountDO> findByUsername(String username);

    boolean existsByUsername(String username);
}