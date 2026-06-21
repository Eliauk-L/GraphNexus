package com.graphnexus.infrastructure.neo4j.config;

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;

/**
 * Neo4j 事务管理器配置。
 *
 * <p>项目同时使用 JPA（MySQL）与 Neo4j，Spring Boot 的
 * {@code Neo4jTransactionManagerAutoConfiguration} 带
 * {@code @ConditionalOnMissingBean(PlatformTransactionManager.class)}，被 JPA 的
 * {@code JpaTransactionManager} 抢占后不再注册 Neo4j 事务管理器。此处显式注册
 * 供 {@code FusionServiceImpl} 的融合原子性事务使用（见 ADR-020）。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@Configuration
public class Neo4jTxConfig {

    @Bean
    public Neo4jTransactionManager neo4jTransactionManager(Driver driver,
                                                            DatabaseSelectionProvider databaseSelectionProvider) {
        return new Neo4jTransactionManager(driver, databaseSelectionProvider);
    }
}