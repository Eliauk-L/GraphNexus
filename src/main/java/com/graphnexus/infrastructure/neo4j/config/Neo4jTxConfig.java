package com.graphnexus.infrastructure.neo4j.config;

import jakarta.persistence.EntityManagerFactory;
import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;

/**
 * 事务管理器配置。JPA（MySQL）为默认，Neo4j 为专用。
 *
 * <p>Spring Boot 的 {@code Neo4jTransactionManagerAutoConfiguration} 带
 * {@code @ConditionalOnMissingBean(PlatformTransactionManager.class)}，
 * JPA 自动配置的 {@code JpaTransactionManager} 会导致 Neo4j TM 不自动注册。
 * 此处显式注册两个事务管理器：JPA 标记 {@code @Primary} 作为 {@code @Transactional} 默认，
 * Neo4j 以 {@code Neo4jTransactionManager} 类型注入供 {@code FusionServiceImpl} 使用。
 * 见 ADR-020。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@Configuration
public class Neo4jTxConfig {

    @Primary
    @Bean
    public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager tm = new JpaTransactionManager();
        tm.setEntityManagerFactory(entityManagerFactory);
        return tm;
    }

    @Bean
    public Neo4jTransactionManager neo4jTransactionManager(Driver driver,
                                                            DatabaseSelectionProvider databaseSelectionProvider) {
        return new Neo4jTransactionManager(driver, databaseSelectionProvider);
    }
}