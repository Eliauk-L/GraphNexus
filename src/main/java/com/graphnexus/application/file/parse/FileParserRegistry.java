package com.graphnexus.application.file.parse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件解析器注册工厂（v3 — 多解析器 per extension + businessType）。
 *
 * <p>Spring 自动注入所有 {@link FileParser} 实现，按扩展名 + 业务类型分组，
 * 同扩展名内按 {@link FileParser#priority()} 升序排列构成兜底链。
 * 见 ADR-004。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Slf4j
@Component
public class FileParserRegistry {

    /** ext → bizType → 解析器有序列表 */
    private final Map<String, Map<String, List<FileParser>>> parserByExtensionAndBiz = new HashMap<>();

    /**
     * Spring 自动注入所有 FileParser 实现，构建 (扩展名, 业务类型) → 解析器有序列表映射。
     */
    public FileParserRegistry(List<FileParser> allParsers) {
        Map<String, Map<String, List<FileParser>>> grouped = new HashMap<>();
        for (FileParser parser : allParsers) {
            String bizType = parser.businessType();
            for (String ext : parser.supportedExtensions()) {
                String extKey = ext.toLowerCase();
                grouped
                        .computeIfAbsent(extKey, k -> new HashMap<>())
                        .computeIfAbsent(bizType, k -> new ArrayList<>())
                        .add(parser);
            }
        }
        for (var extEntry : grouped.entrySet()) {
            Map<String, List<FileParser>> bizMap = new HashMap<>();
            for (var bizEntry : extEntry.getValue().entrySet()) {
                List<FileParser> sorted = bizEntry.getValue().stream()
                        .sorted(Comparator.comparingInt(FileParser::priority))
                        .collect(Collectors.toList());
                bizMap.put(bizEntry.getKey(), Collections.unmodifiableList(sorted));
                log.info("扩展名 {} / {} → {} 个解析器: {}",
                        extEntry.getKey(), bizEntry.getKey(), sorted.size(),
                        sorted.stream().map(p -> p.getClass().getSimpleName())
                                .collect(Collectors.joining(" → ")));
            }
            parserByExtensionAndBiz.put(extEntry.getKey(), Collections.unmodifiableMap(bizMap));
        }
        log.info("FileParserRegistry v3 初始化完成，已注册 {} 种扩展名", parserByExtensionAndBiz.size());
    }

    /**
     * 根据文件名和业务类型获取匹配的解析器（按优先级排序，主解析器在前）。
     *
     * @param filename     原始文件名（含扩展名）
     * @param businessType 业务类型（{@link FileParser#BIZ_TEXTBOOK} 或 {@link FileParser#BIZ_GRADE}）
     * @return 有序解析器列表，未找到返回空列表
     */
    public List<FileParser> getParsers(String filename, String businessType) {
        if (filename == null || !filename.contains(".")) {
            return Collections.emptyList();
        }
        String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
        Map<String, List<FileParser>> bizMap = parserByExtensionAndBiz.get(ext);
        if (bizMap == null) {
            return Collections.emptyList();
        }
        return bizMap.getOrDefault(businessType, Collections.emptyList());
    }

    /**
     * 根据文件名获取所有匹配的解析器（忽略业务类型，跨 biz 合并）。
     *
     * @param filename 原始文件名（含扩展名）
     * @return 有序解析器列表，未找到返回空列表
     */
    public List<FileParser> getParsers(String filename) {
        if (filename == null || !filename.contains(".")) {
            return Collections.emptyList();
        }
        String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
        Map<String, List<FileParser>> bizMap = parserByExtensionAndBiz.get(ext);
        if (bizMap == null) {
            return Collections.emptyList();
        }
        return bizMap.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparingInt(FileParser::priority))
                .collect(Collectors.toList());
    }

    /**
     * 根据文件名和业务类型获取首选解析器。
     *
     * @param filename     原始文件名（含扩展名）
     * @param businessType 业务类型（{@link FileParser#BIZ_TEXTBOOK} 或 {@link FileParser#BIZ_GRADE}）
     * @return 优先级最高的 FileParser，未找到返回 {@code Optional.empty()}
     */
    public Optional<FileParser> getParser(String filename, String businessType) {
        List<FileParser> parsers = getParsers(filename, businessType);
        return parsers.isEmpty() ? Optional.empty() : Optional.of(parsers.get(0));
    }

    /**
     * 根据文件名获取首选解析器（忽略业务类型，向后兼容）。
     *
     * @param filename 原始文件名（含扩展名）
     * @return 优先级最高的 FileParser，未找到返回 {@code Optional.empty()}
     */
    public Optional<FileParser> getParser(String filename) {
        List<FileParser> parsers = getParsers(filename);
        return parsers.isEmpty() ? Optional.empty() : Optional.of(parsers.get(0));
    }
}