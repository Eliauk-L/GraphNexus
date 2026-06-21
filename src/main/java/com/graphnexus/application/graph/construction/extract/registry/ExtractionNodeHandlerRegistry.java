package com.graphnexus.application.graph.construction.extract.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 顶层节点类型处理器注册表（D4 / ADR-023）。
 *
 * <p>构造期 Spring 注入所有 {@link ExtractionNodeHandler} 实现按 sectionKey 索引；
 * 生产环境无实现则注册表为空（prompt 扩展节点段为空串，与原 prompt 一致）。
 * 提供 {@link #register} 供测试手动注册 handler，避免 @Component 测试 bean 污染其他 @SpringBootTest。</p>
 *
 * <p>沿用既有 {@code FileParserRegistry} 的注册表范式（构造期注入 List + 内部 Map 索引）。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@Slf4j
@Component
public class ExtractionNodeHandlerRegistry {

    private final Map<String, ExtractionNodeHandler<?, ?>> handlersByKey = new HashMap<>();

    /**
     * Spring 自动注入所有 ExtractionNodeHandler 实现并按 sectionKey 索引。
     */
    public ExtractionNodeHandlerRegistry(List<ExtractionNodeHandler<?, ?>> handlers) {
        for (ExtractionNodeHandler<?, ?> handler : handlers) {
            register(handler);
        }
        log.info("ExtractionNodeHandlerRegistry 初始化完成，已注册 {} 个顶层节点处理器: {}",
                handlersByKey.size(), handlersByKey.keySet());
    }

    /**
     * 注册一个 handler（构造期由 Spring 调用，或测试手动调用）。
     *
     * @throws IllegalStateException sectionKey 与已注册 handler 冲突时
     */
    public void register(ExtractionNodeHandler<?, ?> handler) {
        ExtractionNodeHandler<?, ?> prev = handlersByKey.put(handler.sectionKey(), handler);
        if (prev != null) {
            throw new IllegalStateException(
                    "ExtractionNodeHandler sectionKey 冲突: " + handler.sectionKey()
                            + " 已被 " + prev.getClass().getName() + " 注册");
        }
    }

    /** 所有已注册 handler（不可变视图） */
    public List<ExtractionNodeHandler<?, ?>> all() {
        return List.copyOf(handlersByKey.values());
    }

    /** 按 sectionKey 查找 */
    public Optional<ExtractionNodeHandler<?, ?>> findByKey(String sectionKey) {
        return Optional.ofNullable(handlersByKey.get(sectionKey));
    }
}
