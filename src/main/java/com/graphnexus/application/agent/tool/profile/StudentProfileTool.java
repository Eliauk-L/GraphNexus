package com.graphnexus.application.agent.tool.profile;

import com.graphnexus.application.agent.tool.EvidenceRef;
import com.graphnexus.application.agent.tool.TeachingTool;
import com.graphnexus.application.agent.tool.ToolExecutionContext;
import com.graphnexus.application.agent.tool.ToolResult;
import com.graphnexus.application.mastery.config.MasteryProperties;
import com.graphnexus.application.mastery.model.MasteryView;
import com.graphnexus.application.mastery.service.MasteryQueryService;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StudentProfileTool
        implements TeachingTool<StudentProfileInput, StudentProfileResult> {
    public static final String NAME = "student_profile";
    private final ExamRecordRepository examRepository;
    private final MasteryQueryService masteryQueryService;
    private final MasteryProperties properties;

    @Override public String name() { return NAME; }
    @Override public String description() { return "查询学生历史考试、知识点掌握度和薄弱分层画像"; }
    @Override public Class<StudentProfileInput> inputType() { return StudentProfileInput.class; }

    @Override
    public ToolResult<StudentProfileResult> execute(StudentProfileInput input, ToolExecutionContext context) {
        long start = System.currentTimeMillis();
        if (input == null || blank(input.studentNo()) || blank(input.subject())) {
            return ToolResult.failure("INVALID_ARGUMENT", "studentNo 和 subject 不能为空",
                    System.currentTimeMillis() - start);
        }
        if (!context.canAccessStudent(input.studentNo())) {
            return ToolResult.failure("STUDENT_SCOPE_DENIED", "无权访问该学生画像",
                    System.currentTimeMillis() - start);
        }
        List<ExamRecordDO> records = examRepository.findByStudentNoAndSubject(
                input.studentNo(), input.subject());
        int limit = input.recentExamLimit() == null ? 5
                : Math.max(1, Math.min(input.recentExamLimit(), 20));
        List<StudentProfileResult.RecentExam> recent = records.stream()
                .sorted(Comparator.comparing(ExamRecordDO::getExamDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(record -> new StudentProfileResult.RecentExam(
                        record.getExamNo(), record.getExamName(), record.getExamDate(),
                        record.getTotalScore(), record.getClassRank())).toList();
        List<MasteryView> mastery = masteryQueryService.current(input.studentNo(), input.subject());
        List<MasteryView> weak = mastery.stream()
                .filter(item -> item.weight() < properties.getWeakThreshold()).toList();
        List<MasteryView> borderline = mastery.stream()
                .filter(item -> item.weight() >= properties.getWeakThreshold()
                        && item.weight() < properties.getMasteredThreshold()).toList();
        List<MasteryView> mastered = mastery.stream()
                .filter(item -> item.weight() >= properties.getMasteredThreshold()).toList();
        double average = mastery.stream().mapToDouble(MasteryView::weight).average().orElse(0);
        ExamRecordDO identity = records.isEmpty() ? null : records.get(0);
        StudentProfileResult result = new StudentProfileResult(
                input.studentNo(), identity == null ? null : identity.getName(),
                identity == null ? null : identity.getClassName(), input.subject(), recent,
                weak, borderline, mastered, average);
        List<EvidenceRef> evidence = recent.stream()
                .map(exam -> new EvidenceRef("EXAM", exam.examNo(), exam.examName())).toList();
        return ToolResult.success(result, evidence, System.currentTimeMillis() - start,
                mastery.size() + recent.size());
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
