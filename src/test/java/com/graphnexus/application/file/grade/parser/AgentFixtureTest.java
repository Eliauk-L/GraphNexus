package com.graphnexus.application.file.grade.parser;

import com.graphnexus.application.file.grade.model.GradeParsePayload;
import com.graphnexus.application.file.parse.model.FileParseRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentFixtureTest {
    private final CsvGradeParser parser = new CsvGradeParser();

    @Test
    void allThreeExamFixturesAreImportableAndIncludeAbsence() throws Exception {
        for (int exam = 1; exam <= 3; exam++) {
            String path = "/fixtures/agent/exam-" + exam + ".csv";
            byte[] bytes = getClass().getResourceAsStream(path).readAllBytes();
            var result = parser.<GradeParsePayload>parse(new FileParseRequest(
                    new ByteArrayInputStream(bytes), "exam-" + exam + ".csv", "数学", bytes));
            assertEquals("E-FIXTURE-00" + exam, result.payload().examNo());
            assertEquals(3, result.payload().students().size());
            assertEquals(5, result.payload().questionCount());
        }
        byte[] third = getClass().getResourceAsStream("/fixtures/agent/exam-3.csv").readAllBytes();
        GradeParsePayload payload = parser.<GradeParsePayload>parse(new FileParseRequest(
                new ByteArrayInputStream(third), "exam-3.csv", "数学", third)).payload();
        payload.students().get(2).scoreDetails().forEach(score -> {
            assertNull(score.rawScore());
            assertNull(score.maxScore());
        });
    }
}
