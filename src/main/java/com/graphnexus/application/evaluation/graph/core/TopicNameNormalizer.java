package com.graphnexus.application.evaluation.graph.core;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

/** Conservative name normalization used by strict matching. */
@Component
public class TopicNameNormalizer {

    public String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("^[\\p{P}]+|[\\p{P}]+$", "").trim();
    }
}
