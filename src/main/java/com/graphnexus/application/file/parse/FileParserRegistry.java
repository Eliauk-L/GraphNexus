package com.graphnexus.application.file.parse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件解析器注册工厂（v2 — 多解析器 per extension）。
 *
 * <p>Spring 自动注入所有 {@link FileParser} 实现，按扩展名分组，
 * 同扩展名内按 {@link FileParser#priority()} 升序排列构成兜底链。
 * 见 ADR-004。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Slf4j
@Component
public class FileParserRegistry {

    private final Map<String, List<FileParser>> parserByExtension = new HashMap<>();

    /**
     * Spring 自动注入所有 FileParser 实现，构建扩展名→解析器有序列表映射。
     */
    public FileParserRegistry(List<FileParser> allParsers) {
        Map<String, List<FileParser>> grouped = new HashMap<>();
        for (FileParser parser : allParsers) {
            for (String ext : parser.supportedExtensions()) {
                String key = ext.toLowerCase();
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(parser);
            }
        }
        for (var entry : grouped.entrySet()) {
            List<FileParser> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(FileParser::priority))
                    .collect(Collectors.toList());
            parserByExtension.put(entry.getKey(), Collections.unmodifiableList(sorted));
            log.info("扩展名 {} → {} 个解析器: {}",
                    entry.getKey(), sorted.size(),
                    sorted.stream().map(p -> p.getClass().getSimpleName())
                            .collect(Collectors.joining(" → ")));
        }
        log.info("FileParserRegistry v2 初始化完成，已注册 {} 种扩展名", parserByExtension.size());
    }

    /**
     * 根据文件名获取所有匹配的解析器（按优先级排序，主解析器在前）。
     *
     * @param filename 原始文件名（含扩展名）
     * @return 有序解析器列表，未找到返回空列表
     */
    public List<FileParser> getParsers(String filename) {
        if (filename == null || !filename.contains(".")) {
            return Collections.emptyList();
        }
        String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
        return parserByExtension.getOrDefault(ext, Collections.emptyList());
    }

    /**
     * 根据文件名获取首选解析器（向后兼容）。
     *
     * @param filename 原始文件名（含扩展名）
     * @return 优先级最高的 FileParser，未找到返回 {@code Optional.empty()}
     */
    public Optional<FileParser> getParser(String filename) {
        List<FileParser> parsers = getParsers(filename);
        return parsers.isEmpty() ? Optional.empty() : Optional.of(parsers.get(0));
    }
}