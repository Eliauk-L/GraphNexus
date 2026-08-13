package com.graphnexus.application.evaluation.graph.builder.nlp;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SentenceSplitter {

    public List<Sentence> split(String text) {
        if (text == null || text.isBlank()) return List.of();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        List<Sentence> result = new ArrayList<>();
        int paragraph = 0;
        int index = 0;
        for (String block : normalized.split("\\n\\s*\\n")) {
            for (String value : block.split("(?<=[。！？!?；;])|\\n+")) {
                String sentence = value.trim();
                if (!sentence.isEmpty()) result.add(new Sentence(index++, paragraph, sentence));
            }
            paragraph++;
        }
        return List.copyOf(result);
    }

    public record Sentence(int index, int paragraphIndex, String text) { }
}
