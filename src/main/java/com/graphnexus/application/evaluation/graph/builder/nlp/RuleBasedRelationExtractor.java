package com.graphnexus.application.evaluation.graph.builder.nlp;

import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import com.graphnexus.application.evaluation.graph.model.CandidateDependency;
import com.graphnexus.application.evaluation.graph.model.CandidateTopic;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RuleBasedRelationExtractor {

    private final EvaluationProperties properties;

    public List<CandidateDependency> extract(List<SentenceSplitter.Sentence> sentences, List<CandidateTopic> topics) {
        if (properties.getNlpExplicitRelationScore() < properties.getNlpRelationThreshold()) return List.of();
        List<CandidateDependency> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (var sentence : sentences) {
            String text = sentence.text().replaceAll("\\s+", "");
            for (CandidateTopic source : topics) {
                for (CandidateTopic target : topics) {
                    if (source == target || !text.contains(source.name()) || !text.contains(target.name())) continue;
                    if (isPrerequisite(text, source.name(), target.name())) {
                        String key = source.tempId() + "->" + target.tempId();
                        if (seen.add(key)) result.add(new CandidateDependency(source.tempId(), target.tempId(), "hard"));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private boolean isPrerequisite(String text, String source, String target) {
        int sourceIndex = text.indexOf(source);
        int targetIndex = text.indexOf(target);
        if (sourceIndex < 0 || targetIndex < 0 || sourceIndex >= targetIndex
                || sourceIndex + source.length() > targetIndex) return false;
        String beforeSource = text.substring(0, sourceIndex);
        String between = text.substring(sourceIndex + source.length(), targetIndex);
        return (beforeSource.endsWith("先") && between.contains("再"))
                || (beforeSource.endsWith("由") && between.contains("推出"))
                || (beforeSource.endsWith("利用"))
                || (beforeSource.endsWith("掌握") && between.contains("后"))
                || (beforeSource.endsWith("回顾") && between.contains("引入"))
                || between.contains("基础上");
    }
}
