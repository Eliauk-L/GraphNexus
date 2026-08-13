package com.graphnexus.application.evaluation.graph.gold;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.evaluation.graph.model.GoldDataset;
import com.graphnexus.application.evaluation.graph.model.GoldDependency;
import com.graphnexus.application.evaluation.graph.model.GoldTopic;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Loads and validates the structured taxonomy files without accessing textbook PDFs. */
@Component
@RequiredArgsConstructor
public class GoldDatasetLoader {

    private final DatasetRegistry datasetRegistry;
    private final ObjectMapper objectMapper;
    private final GoldDatasetValidator validator;

    public GoldDataset load(String datasetVersion) {
        Path directory = datasetRegistry.resolve(datasetVersion);
        try {
            byte[] topicsBytes = requiredFile(directory, "topics.json");
            byte[] dependenciesBytes = requiredFile(directory, "dependencies.json");
            byte[] clustersBytes = requiredFile(directory, "clusters.json");
            byte[] manifestBytes = requiredFile(directory, "manifest.json");
            JsonNode topicsRoot = objectMapper.readTree(topicsBytes);
            JsonNode dependenciesRoot = objectMapper.readTree(dependenciesBytes);
            objectMapper.readTree(clustersBytes);
            JsonNode manifestRoot = objectMapper.readTree(manifestBytes);

            List<GoldTopic> topics = parseTopics(topicsRoot.path("topics"));
            List<GoldDependency> dependencies = parseDependencies(dependenciesRoot.path("dependencies"));
            Map<String, Long> manifestDistribution = new java.util.LinkedHashMap<>();
            manifestRoot.path("typeDistribution").fields().forEachRemaining(entry ->
                    manifestDistribution.put(entry.getKey(), entry.getValue().asLong()));
            List<String> warnings = validator.validate(topics, dependencies, manifestDistribution);

            Set<String> ids = topics.stream().map(GoldTopic::id).collect(Collectors.toSet());
            List<GoldDependency> internal = new ArrayList<>();
            List<GoldDependency> crossBook = new ArrayList<>();
            Set<String> externalIds = new LinkedHashSet<>();
            for (GoldDependency dependency : dependencies) {
                if (ids.contains(dependency.prerequisiteId())) {
                    internal.add(dependency);
                } else {
                    crossBook.add(dependency);
                    externalIds.add(dependency.prerequisiteId());
                }
            }
            return new GoldDataset(datasetVersion, hashDataset(topicsBytes, dependenciesBytes, clustersBytes, manifestBytes), topics, dependencies,
                    internal, crossBook, externalIds, warnings);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw invalid("黄金集 JSON 读取失败: " + ex.getMessage());
        }
    }

    private List<GoldTopic> parseTopics(JsonNode array) {
        if (!array.isArray()) throw invalid("topics.json 缺少 topics 数组");
        List<GoldTopic> result = new ArrayList<>();
        array.forEach(node -> result.add(new GoldTopic(
                text(node, "id"), text(node, "name"), text(node, "type"),
                text(node, "domain"), text(node, "description"))));
        return result;
    }

    private List<GoldDependency> parseDependencies(JsonNode array) {
        if (!array.isArray()) throw invalid("dependencies.json 缺少 dependencies 数组");
        List<GoldDependency> result = new ArrayList<>();
        array.forEach(node -> result.add(new GoldDependency(
                text(node, "prerequisiteId"), text(node, "topicId"), text(node, "strength"))));
        return result;
    }

    private byte[] requiredFile(Path directory, String fileName) throws IOException {
        Path file = directory.resolve(fileName);
        if (!Files.isRegularFile(file)) throw invalid("黄金集缺少文件: " + fileName);
        return Files.readAllBytes(file);
    }

    private String hashDataset(byte[]... files) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (byte[] file : files) bytes.write(file);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (NoSuchAlgorithmException | IOException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String text(JsonNode node, String field) {
        return node.path(field).isMissingNode() || node.path(field).isNull() ? "" : node.path(field).asText().trim();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.A0002, message);
    }
}
