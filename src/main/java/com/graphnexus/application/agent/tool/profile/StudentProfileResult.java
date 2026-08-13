package com.graphnexus.application.agent.tool.profile;

import com.graphnexus.application.mastery.model.MasteryView;

import java.time.LocalDate;
import java.util.List;

public record StudentProfileResult(
        String studentNo,
        String name,
        String className,
        String subject,
        List<RecentExam> recentExams,
        List<MasteryView> weak,
        List<MasteryView> borderline,
        List<MasteryView> mastered,
        double averageMastery
) {
    public record RecentExam(String examNo, String examName, LocalDate examDate,
                             Integer totalScore, Integer classRank) {}
}
