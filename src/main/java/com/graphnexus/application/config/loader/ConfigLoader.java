package com.graphnexus.application.config.loader;

import com.graphnexus.application.config.service.impl.ConfigServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 配置加载器 — 在 Spring 启动完成后从 DB 加载配置到 Properties Bean + Caffeine 缓存。
 *
 * <p>@Order(0) 确保在其他 ApplicationRunner 之前执行。
 * DB 不可达时不阻断启动，仅 WARN 日志 + 使用 yml 默认值。
 * 见 DESIGN §1 D1 + ADR-041。</p>
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(0)
public class ConfigLoader implements ApplicationRunner {

    private final ConfigServiceImpl configService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int loaded = configService.loadFromDatabase();
            if (loaded > 0) {
                log.info("Loaded {} configs from database", loaded);
            } else {
                log.info("No custom configs in database, using yml defaults");
            }
        } catch (Exception e) {
            log.warn("Failed to load configs from database, using yml defaults: {}",
                    e.getMessage());
        }
    }
}