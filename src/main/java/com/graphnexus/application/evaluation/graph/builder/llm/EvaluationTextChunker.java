package com.graphnexus.application.evaluation.graph.builder.llm;

import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EvaluationTextChunker {

    private final EvaluationProperties properties;

    public List<TextChunk> chunk(String text, String textHash, String configVersion) {
        if (text == null || text.isBlank()) return List.of();
        int size = properties.getChunkSize();
        int overlap = properties.getChunkOverlap();
        List<String> units = splitUnits(text);
        List<String> contents = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            if (isHeading(unit) && current.length() > 0) emit(contents, current);
            if (unit.length() > size) {
                if (current.length() > 0) emit(contents, current);
                for (int start = 0; start < unit.length(); start += Math.max(1, size - overlap)) {
                    contents.add(unit.substring(start, Math.min(unit.length(), start + size)));
                    if (start + size >= unit.length()) break;
                }
            } else if (current.length() + unit.length() + 2 > size) {
                String previous = current.toString();
                emit(contents, current);
                if (overlap > 0 && !previous.isEmpty()) {
                    current.append(previous.substring(Math.max(0, previous.length() - overlap)));
                }
                append(current, unit);
            } else {
                append(current, unit);
            }
        }
        if (current.length() > 0) emit(contents, current);
        List<TextChunk> result = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            String content = contents.get(i);
            String id = "chunk-" + i + "-" + shortHash(value(textHash) + "|" + value(configVersion) + "|" + i + "|" + content);
            result.add(new TextChunk(id, i, content));
        }
        return List.copyOf(result);
    }

    private List<String> splitUnits(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        List<String> units = new ArrayList<>();
        for (String paragraph : normalized.split("\\n\\s*\\n|(?m)(?=^#{1,6}\\s)|(?m)(?=^第[一二三四五六七八九十0-9]+[章节])")) {
            String value = paragraph.trim();
            if (!value.isEmpty()) units.add(value);
        }
        return units;
    }

    private boolean isHeading(String value) {
        String firstLine = value.lines().findFirst().orElse("").trim();
        return firstLine.matches("^#{1,6}\\s+.*|^第[一二三四五六七八九十0-9]+[章节].*|^\\d+(?:\\.\\d+)*\\s+.+");
    }

    private void append(StringBuilder builder, String unit) {
        if (builder.length() > 0) builder.append("\n\n");
        builder.append(unit);
    }

    private void emit(List<String> result, StringBuilder builder) {
        String content = builder.toString().trim();
        if (!content.isEmpty()) result.add(content);
        builder.setLength(0);
    }

    private String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String value(String value) { return value == null ? "" : value; }

    public record TextChunk(String chunkId, int index, String content) { }
}
