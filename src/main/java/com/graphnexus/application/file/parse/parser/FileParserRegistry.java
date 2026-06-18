package com.graphnexus.application.file.parse.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文件解析器注册工厂 — Spring 自动注入所有 {@link FileParser} 实现。
 *
 * <p>按文件扩展名路由到对应解析器，替代 Controller 的 if-else 分流。
 * 新增文件类型只需实现 {@link FileParser} 接口，自动注册，Controller 零改动。
 * 见 DESIGN D12。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Slf4j
@Component
public class FileParserRegistry {

    private final Map<String, FileParser> parserByExtension = new HashMap<>();

    /**
     * Spring 自动注入所有 FileParser 实现，构建扩展名→解析器映射。
     */
    public FileParserRegistry(List<FileParser> allParsers) {
        for (FileParser parser : allParsers) {
            for (String ext : parser.supportedExtensions()) {
                String key = ext.toLowerCase();
                FileParser existing = parserByExtension.put(key, parser);
                if (existing != null) {
                    log.warn("扩展名 {} 被多个 FileParser 注册: {} 覆盖 {}",
                            key, parser.getClass().getSimpleName(),
                            existing.getClass().getSimpleName());
                }
            }
        }
        log.info("FileParserRegistry 初始化完成，已注册 {} 种扩展名", parserByExtension.size());
    }

    /**
     * 根据文件名获取对应解析器。
     *
     * @param filename 原始文件名（含扩展名）
     * @return 匹配的 FileParser，未找到返回 {@code Optional.empty()}
     */
    public Optional<FileParser> getParser(String filename) {
        if (filename == null || !filename.contains(".")) {
            return Optional.empty();
        }
        String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
        return Optional.ofNullable(parserByExtension.get(ext));
    }
}