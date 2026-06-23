package com.graphnexus.infrastructure.mysql.config.repository;

import com.graphnexus.infrastructure.mysql.config.entity.SystemConfigDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 系统配置 Repository — 遵循方法名派生规范，不含 DO 后缀。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfigDO, Long> {

    /**
     * 按配置键精确查询。
     */
    Optional<SystemConfigDO> findByConfigKey(String configKey);

    /**
     * 全量查询，按 sortOrder 升序排列（用于前端展示）。
     */
    List<SystemConfigDO> findAllByOrderBySortOrderAsc();
}