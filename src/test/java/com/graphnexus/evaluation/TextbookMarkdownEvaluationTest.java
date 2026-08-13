package com.graphnexus.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.evaluation.graph.builder.GraphBuilderRegistry;
import com.graphnexus.application.evaluation.graph.builder.llm.EvaluationTextChunker;
import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import com.graphnexus.application.evaluation.graph.core.CandidateGraphNormalizer;
import com.graphnexus.application.evaluation.graph.core.StrictGraphEvaluator;
import com.graphnexus.application.evaluation.graph.gold.GoldDatasetLoader;
import com.graphnexus.application.evaluation.graph.model.CandidateDependency;
import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.CandidateTopic;
import com.graphnexus.application.evaluation.graph.model.EvaluationMetrics;
import com.graphnexus.application.evaluation.graph.model.GraphBuildContext;
import com.graphnexus.application.evaluation.graph.model.GraphBuildMethod;
import com.graphnexus.application.evaluation.graph.model.GraphEvaluationReport;
import com.graphnexus.infrastructure.neo4j.repository.EvaluationGraphRepository;
import com.graphnexus.infrastructure.neo4j.repository.model.EvaluationGraphSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in evaluation against the extracted eighth-grade textbook Markdown.
 *
 * <p>Smoke run (first six chunks):
 * {@code mvn -Dtest=TextbookMarkdownEvaluationTest -Devaluation.textbook.it=true test}</p>
 *
 * <p>Full document run:
 * {@code mvn -Dtest=TextbookMarkdownEvaluationTest -Devaluation.textbook.it=true
 * -Devaluation.textbook.max-chunks=0 -Devaluation.textbook.keep-graphs=true test}</p>
 *
 * <p>The test is disabled by default because the LLM branch performs real, potentially costly calls.</p>
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "evaluation.textbook.it", matches = "true")
class TextbookMarkdownEvaluationTest {

    private static final String DATASET_VERSION = "pep-math-8down-v1";
    private static final Path DEFAULT_MARKDOWN = Path.of(
            "pep-math-taxonomy", "textbook", "数学八年级下册_markdown.md");
    private static final int DEFAULT_MAX_CHUNKS = 4;

    @Autowired private EvaluationTextChunker chunker;
    @Autowired private EvaluationProperties properties;
    @Autowired private GraphBuilderRegistry builderRegistry;
    @Autowired private CandidateGraphNormalizer graphNormalizer;
    @Autowired private GoldDatasetLoader goldDatasetLoader;
    @Autowired private StrictGraphEvaluator evaluator;
    @Autowired private EvaluationGraphRepository graphRepository;
    @Autowired private ObjectMapper objectMapper;

    @Value("${spring.ai.openai.api-key:}")
    private String configuredLlmApiKey;

    private final List<String> persistedGraphIds = new ArrayList<>();

    @DynamicPropertySource
    static void configureLlmCredential(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.api-key", TextbookMarkdownEvaluationTest::resolveLlmApiKey);
    }

    @AfterEach
    void cleanEvaluationGraphsUnlessRequested() {
        if (!Boolean.getBoolean("evaluation.textbook.keep-graphs")) {
            persistedGraphIds.forEach(graphRepository::deleteEvaluationGraph);
        }
    }

    @Test
    void loadsLlmCredentialWithoutExposingIt() {
        assertThat(configuredLlmApiKey)
                .as("Set LLM_API_KEY or configure it in the git-ignored .env file")
                .isNotBlank()
                .doesNotContain("sk-your-key-here");
    }

    @Test
    void splitsTheCompleteMarkdownDeterministically() throws Exception {
        TextbookSource source = loadSource();

        List<EvaluationTextChunker.TextChunk> first = chunker.chunk(source.markdown(), source.textHash(), chunkVersion());
        List<EvaluationTextChunker.TextChunk> second = chunker.chunk(source.markdown(), source.textHash(), chunkVersion());

        assertThat(first).hasSizeGreaterThan(1).isEqualTo(second);
        assertThat(first).allSatisfy(chunk -> {
            assertThat(chunk.content()).isNotBlank();
            assertThat(chunk.content().length()).isLessThanOrEqualTo(properties.getChunkSize() + properties.getChunkOverlap());
        });
        assertThat(first).extracting(EvaluationTextChunker.TextChunk::chunkId).doesNotHaveDuplicates();
        assertThat(first.stream().mapToLong(chunk -> chunk.content().length()).sum())
                .isGreaterThan((long) (source.markdown().length() * 0.9));

        log.info("教材 Markdown 分块检查完成: chars={}, chunks={}, chunkSize={}, overlap={}",
                source.markdown().length(), first.size(), properties.getChunkSize(), properties.getChunkOverlap());
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.MINUTES)
    void evaluatesTheSameChunkWindowWithLlmAndNlpAndPersistsBothGraphs() throws Exception {
        assertThat(configuredLlmApiKey)
                .as("Set LLM_API_KEY or configure it in the git-ignored .env file")
                .isNotBlank();
        TextbookSource source = loadSource();
        var gold = goldDatasetLoader.load(DATASET_VERSION);
        List<EvaluationTextChunker.TextChunk> allChunks = chunker.chunk(
                source.markdown(), source.textHash(), chunkVersion());
        List<EvaluationTextChunker.TextChunk> selectedChunks = selectChunks(allChunks);
        String groupId = UUID.randomUUID().toString();

        MethodRun llm = evaluateMethod(GraphBuildMethod.LLM, selectedChunks, source, groupId, gold);
        MethodRun nlp = evaluateMethod(GraphBuildMethod.NLP_NER_RE, selectedChunks, source, groupId, gold);

        assertComparableRun(llm, selectedChunks.size(), gold.topics().size(), gold.internalDependencies().size());
        assertComparableRun(nlp, selectedChunks.size(), gold.topics().size(), gold.internalDependencies().size());
        assertThat(llm.callCount()).isGreaterThanOrEqualTo(selectedChunks.size());
        assertThat(nlp.callCount()).isZero();
        assertThat(llm.graphId()).isNotEqualTo(nlp.graphId());

        Path reportPath = writeReport(new TextbookEvaluationSummary(
                source.path().toAbsolutePath().normalize().toString(), source.textHash(), source.markdown().length(),
                allChunks.size(), selectedChunks.size(), selectedChunks.size() == allChunks.size(),
                properties.getChunkSize(), properties.getChunkOverlap(), List.of(llm, nlp)));

        log.info("教材图谱评测完成: processedChunks={}/{}, LLM graphId={}, NLP graphId={}, report={}",
                selectedChunks.size(), allChunks.size(), llm.graphId(), nlp.graphId(), reportPath.toAbsolutePath());
    }

    private MethodRun evaluateMethod(GraphBuildMethod method,
                                     List<EvaluationTextChunker.TextChunk> chunks,
                                     TextbookSource source,
                                     String groupId,
                                     com.graphnexus.application.evaluation.graph.model.GoldDataset gold) {
        List<CandidateTopic> topics = new ArrayList<>();
        List<CandidateDependency> dependencies = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        long durationMs = 0;
        long promptChars = 0;
        long responseChars = 0;
        long estimatedInputTokens = 0;
        long estimatedOutputTokens = 0;
        int callCount = 0;

        for (EvaluationTextChunker.TextChunk chunk : chunks) {
            GraphBuildContext context = new GraphBuildContext(
                    0L, source.path().getFileName().toString(), "数学", "八年级下册", chunk.content(),
                    source.textHash(), chunkVersion(), groupId + ":" + method + ":" + chunk.index());
            var result = builderRegistry.get(method).build(context);
            prefixAndCollect(chunk.chunkId(), result.rawGraph(), topics, dependencies);
            warnings.addAll(result.warnings());
            durationMs += result.durationMs();
            promptChars += result.promptChars();
            responseChars += result.responseChars();
            estimatedInputTokens += result.estimatedInputTokens();
            estimatedOutputTokens += result.estimatedOutputTokens();
            callCount += result.callCount();
        }

        var normalized = graphNormalizer.normalize(new CandidateGraph(topics, dependencies));
        warnings.addAll(normalized.warnings());
        GraphEvaluationReport report = evaluator.evaluateReport(gold, normalized.normalizedGraph());
        String graphId = "textbook-" + method.name().toLowerCase() + "-" + UUID.randomUUID();
        graphRepository.saveEvaluationGraph(toSnapshot(graphId, method, gold.datasetHash(), report, warnings));
        persistedGraphIds.add(graphId);

        return new MethodRun(method, graphId, chunks.size(), durationMs, promptChars, responseChars,
                estimatedInputTokens, estimatedOutputTokens, callCount, report.metrics(), report.structure(),
                report.errors(), List.copyOf(warnings));
    }

    private void prefixAndCollect(String chunkId, CandidateGraph graph, List<CandidateTopic> topics,
                                  List<CandidateDependency> dependencies) {
        graph.topics().forEach(topic -> topics.add(new CandidateTopic(
                chunkId + ":" + topic.tempId(), topic.name(), topic.type(), topic.domain(), topic.description())));
        graph.dependencies().forEach(edge -> dependencies.add(new CandidateDependency(
                chunkId + ":" + edge.prerequisiteTempId(), chunkId + ":" + edge.topicTempId(), edge.strength())));
    }

    private EvaluationGraphSnapshot toSnapshot(String graphId, GraphBuildMethod method, String datasetHash,
                                                GraphEvaluationReport report, List<String> warnings) {
        EvaluationMetrics m = report.metrics();
        CandidateGraph graph = report.normalization().normalizedGraph();
        return new EvaluationGraphSnapshot(graphId, DATASET_VERSION, datasetHash, method.name(), "COMPLETED",
                LocalDateTime.now(),
                new EvaluationGraphSnapshot.MetricSnapshot(
                        m.goldTopicCount(), m.candidateTopicCount(), m.matchedTopicCount(),
                        m.topicStrictPrecision(), m.topicStrictRecall(), m.topicStrictF1(),
                        m.topicRelaxedPrecision(), m.topicRelaxedRecall(), m.topicRelaxedF1(),
                        m.typeAccuracy(), m.typeCoverage(), m.domainAccuracy(), m.domainCoverage(),
                        m.goldInternalDependencyCount(), m.candidateDependencyCount(), m.matchedDependencyCount(),
                        m.internalRelationPrecision(), m.internalRelationRecall(), m.internalRelationF1(),
                        m.directionAccuracy(), m.strengthAccuracy(), m.structureValidity(), m.qualityScore()),
                graph.topics().stream().map(topic -> new EvaluationGraphSnapshot.TopicSnapshot(
                        topic.tempId(), topic.name(), topic.type(), topic.domain(), topic.description())).toList(),
                graph.dependencies().stream().map(edge -> new EvaluationGraphSnapshot.DependencySnapshot(
                        edge.prerequisiteTempId(), edge.topicTempId(), edge.strength())).toList(),
                List.copyOf(warnings));
    }

    private void assertComparableRun(MethodRun run, int expectedChunks, int goldTopics, int goldDependencies) {
        assertThat(run.processedChunks()).isEqualTo(expectedChunks);
        assertThat(run.metrics().goldTopicCount()).isEqualTo(goldTopics);
        assertThat(run.metrics().goldInternalDependencyCount()).isEqualTo(goldDependencies);
        assertThat(run.metrics().topicStrictF1()).isBetween(0.0, 1.0);
        assertThat(run.metrics().topicRelaxedF1()).isBetween(0.0, 1.0);
        assertThat(run.metrics().internalRelationF1()).isBetween(0.0, 1.0);
        assertThat(graphRepository.findEvaluationGraph(run.graphId())).isPresent().get()
                .extracting(snapshot -> snapshot.topics().size())
                .isEqualTo(run.metrics().candidateTopicCount());
    }

    private List<EvaluationTextChunker.TextChunk> selectChunks(List<EvaluationTextChunker.TextChunk> chunks) {
        int maxChunks = Integer.getInteger("evaluation.textbook.max-chunks", DEFAULT_MAX_CHUNKS);
        if (maxChunks < 0) {
            throw new IllegalArgumentException("evaluation.textbook.max-chunks must be >= 0");
        }
        return maxChunks == 0 ? chunks : chunks.subList(0, Math.min(maxChunks, chunks.size()));
    }

    private TextbookSource loadSource() throws Exception {
        Path path = Path.of(System.getProperty("evaluation.textbook.path", DEFAULT_MARKDOWN.toString()));
        assertThat(path).isRegularFile();
        String markdown = Files.readString(path, StandardCharsets.UTF_8);
        assertThat(markdown).isNotBlank();
        return new TextbookSource(path, markdown, sha256(markdown));
    }

    private Path writeReport(TextbookEvaluationSummary summary) throws Exception {
        Path directory = Path.of("target", "evaluation-reports");
        Files.createDirectories(directory);
        Path report = directory.resolve("textbook-markdown-" + UUID.randomUUID() + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(report.toFile(), summary);
        return report;
    }

    private String chunkVersion() {
        return "chunk-" + properties.getChunkSize() + "-overlap-" + properties.getChunkOverlap();
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String resolveLlmApiKey() {
        String environmentKey = System.getenv("LLM_API_KEY");
        if (environmentKey != null && !environmentKey.isBlank()) {
            return environmentKey.trim();
        }
        Path dotenv = Path.of(".env");
        if (!Files.isRegularFile(dotenv)) {
            return "";
        }
        try {
            return Files.readAllLines(dotenv, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(line -> line.startsWith("LLM_API_KEY="))
                    .map(line -> unquote(line.substring("LLM_API_KEY=".length()).trim()))
                    .filter(value -> !value.isBlank() && !"sk-your-key-here".equals(value))
                    .findFirst()
                    .orElse("");
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read LLM_API_KEY from .env", ex);
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private record TextbookSource(Path path, String markdown, String textHash) { }

    private record TextbookEvaluationSummary(
            String sourcePath,
            String textHash,
            int sourceChars,
            int totalChunks,
            int processedChunks,
            boolean fullDocument,
            int chunkSize,
            int chunkOverlap,
            List<MethodRun> methods
    ) { }

    private record MethodRun(
            GraphBuildMethod method,
            String graphId,
            int processedChunks,
            long durationMs,
            long promptChars,
            long responseChars,
            long estimatedInputTokens,
            long estimatedOutputTokens,
            int callCount,
            EvaluationMetrics metrics,
            com.graphnexus.application.evaluation.graph.model.GraphStructureMetrics structure,
            List<com.graphnexus.application.evaluation.graph.model.EvaluationErrorDetail> errors,
            List<String> warnings
    ) { }
}
