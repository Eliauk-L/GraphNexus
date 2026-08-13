package com.graphnexus.application.evaluation.graph.builder.nlp;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MathTermCandidateExtractor {

    private static final Pattern CALLED = Pattern.compile("[^，。；]{1,24}(?:叫做|称为)([\\p{IsHan}A-Za-z0-9（）()·]{2,16})");
    private static final Pattern IS_DEFINITION = Pattern.compile("([\\p{IsHan}A-Za-z0-9（）()·]{2,16})是[^，。；]{2,30}");
    private static final Pattern MATH_PHRASE = Pattern.compile("([\\p{IsHan}A-Za-z0-9（）()·]{2,18}(?:概念|性质|判定|运算|表示|应用|定理|公式|函数|方程|图象|图像))");

    public List<TermEvidence> extract(List<SentenceSplitter.Sentence> sentences) {
        Map<String, MutableEvidence> evidence = new HashMap<>();
        for (var sentence : sentences) {
            collect(CALLED, sentence, true, true, evidence);
            collect(IS_DEFINITION, sentence, true, false, evidence);
            collect(MATH_PHRASE, sentence, false, true, evidence);
        }
        List<TermEvidence> result = new ArrayList<>();
        evidence.values().stream().sorted(java.util.Comparator.comparing(value -> value.term)).forEach(value ->
                result.add(new TermEvidence(value.term, value.titleHit, value.definitionHit, value.frequency,
                        value.mathPatternHit, value.paragraphs.size() > 1)));
        return result;
    }

    private void collect(Pattern pattern, SentenceSplitter.Sentence sentence, boolean definition, boolean math,
                         Map<String, MutableEvidence> evidence) {
        Matcher matcher = pattern.matcher(sentence.text());
        while (matcher.find()) {
            String term = clean(matcher.group(1));
            if (term.length() < 2) continue;
            MutableEvidence value = evidence.computeIfAbsent(term, MutableEvidence::new);
            value.definitionHit |= definition;
            value.mathPatternHit |= math;
            value.titleHit |= isGenericTitle(sentence.text());
            value.frequency++;
            value.paragraphs.add(sentence.paragraphIndex());
        }
    }

    private boolean isGenericTitle(String text) {
        String value = text.trim();
        return value.matches("^#{0,6}\\s*第[一二三四五六七八九十0-9]+[章节].*")
                || value.matches("^\\d+(?:\\.\\d+)*\\s+.*")
                || value.matches("^(本章|小结|复习题|练习题).*");
    }

    private String clean(String value) {
        return value.replaceAll("^[的与和及、，。；：\u3000\\s]+|[的与和及、，。；：\u3000\\s]+$", "").trim();
    }

    private static class MutableEvidence {
        private final String term;
        private boolean titleHit;
        private boolean definitionHit;
        private int frequency;
        private boolean mathPatternHit;
        private final java.util.Set<Integer> paragraphs = new java.util.HashSet<>();
        private MutableEvidence(String term) { this.term = term; }
    }

    public record TermEvidence(String term, boolean titleHit, boolean definitionHit, int frequency,
                               boolean mathPatternHit, boolean crossParagraphHit) { }
}
