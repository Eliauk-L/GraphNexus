package com.graphnexus.application.file.grade.parser;

import com.graphnexus.application.file.grade.parser.CsvGradeParser.CsvParsePayload;
import com.graphnexus.application.file.grade.parser.CsvGradeParser.StudentRecord;
import com.graphnexus.application.file.grade.parser.CsvGradeParser.ScoreDetail;
import com.graphnexus.application.file.parse.FileParseRequest;
import com.graphnexus.application.file.parse.FileParseResult;
import com.graphnexus.application.file.grade.model.GradeFileType;
import com.graphnexus.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CsvGradeParser 单元测试 — 派生自 AC-1 / AC-2 / AC-6。
 *
 * <p>CSV 构建说明：8 个元数据列（学号/姓名/班级/考试编号/考试名称/日期/总分/班级排名）
 * + N 个题号列。第 2 行前 8 列为空，后 N 列为知识点名。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@DisplayName("CsvGradeParser 成绩 CSV 解析测试")
class CsvGradeParserTest {

    private static final CsvGradeParser parser = new CsvGradeParser();

    /** 构建 8 元数据列 + N 题号列的表头行 1 */
    private static String headerRow1(int n) {
        return "学号,姓名,班级,考试编号,考试名称,日期,总分,班级排名" + questionHeaders(n);
    }

    /** 构建题号列头：,题1,题2,...,题N */
    private static String questionHeaders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            sb.append(",").append("题").append(i);
        }
        return sb.toString();
    }

    /** 构建第 2 行（知识点行）：前 8 列为空，后 N 列为知识点 */
    private static String headerRow2(String... kps) {
        return ",".repeat(8) + String.join(",", kps);
        // 8 commas = 9 leading empty fields (covering 8 meta cols + 1 gap before KPs)
    }

    /** 构建数据行 */
    private static String dataRow(String studentNo, String name, String className,
                                  String examNo, String examName, String date,
                                  String totalScore, String rank, String... scores) {
        return String.join(",",
                studentNo, name, className, examNo, examName, date, totalScore, rank
        ) + "," + String.join(",", scores);
    }

    private FileParseRequest parseRequest(String csv, String subject) {
        byte[] rawBytes = csv.getBytes(StandardCharsets.UTF_8);
        return new FileParseRequest(
                new ByteArrayInputStream(rawBytes), "test.csv", subject, rawBytes
        );
    }

    // ======================== AC-1 · 正常解析 ========================

    @Test
    @DisplayName("AC-1: 3名学生 + 3道题 + 3个知识点")
    void shouldParseValidCsv() {
        String csv = String.join("\n",
                headerRow1(3),
                headerRow2("力学基础", "牛顿第二定律应用;力学基础", "能量守恒"),
                dataRow("S001", "张三", "初三(1)班", "E20250310", "三月月考", "2025-03-10", "85", "3", "10/12", "15/18", "8/10"),
                dataRow("S002", "李四", "初三(1)班", "E20250310", "三月月考", "2025-03-10", "92", "1", "12/12", "-/-", "9/10"),
                dataRow("S003", "王五", "初三(2)班", "E20250310", "三月月考", "2025-03-10", "78", "5", "8/12", "14/18", "7/10")
        ) + "\n";

        FileParseResult<CsvParsePayload> result = parser.parse(parseRequest(csv, "物理"));
        CsvParsePayload payload = result.payload();

        assertEquals("E20250310", payload.examNo());
        assertEquals("三月月考", payload.examName());
        assertEquals(LocalDate.of(2025, 3, 10), payload.examDate());
        assertEquals("物理", payload.subject());
        assertEquals(3, payload.students().size());
        assertEquals(3, payload.questionCount());
        assertEquals(3, payload.knowledgePoints().size());
        assertTrue(payload.knowledgePoints().contains("力学基础"));

        // 张三题1: 10/12 + 知识点"力学基础"
        StudentRecord zs = payload.students().get(0);
        assertEquals("S001", zs.studentNo());
        assertEquals(85, zs.totalScore());
        assertEquals(3, zs.classRank());
        ScoreDetail sd1 = zs.scoreDetails().get(0);
        assertEquals("题1", sd1.questionLabel());
        assertEquals(10, sd1.rawScore());
        assertEquals(12, sd1.maxScore());
        assertEquals(List.of("力学基础"), sd1.kpNames());
    }

    @Test
    @DisplayName("AC-1: 一题多知识点（分号分隔）")
    void shouldParseMultiKnowledgePointQuestion() {
        String csv = String.join("\n",
                headerRow1(2),
                headerRow2("代数基础;方程思想", "几何证明"),
                dataRow("S001", "张三", "一班", "E20240601", "六月月考", "2024-06-01", "90", "2", "15/20", "18/20")
        ) + "\n";

        FileParseResult<CsvParsePayload> result = parser.parse(parseRequest(csv, "数学"));
        CsvParsePayload payload = result.payload();

        assertEquals(3, payload.knowledgePoints().size());
        ScoreDetail sd1 = payload.students().get(0).scoreDetails().get(0);
        assertEquals(2, sd1.kpNames().size());
        assertEquals("代数基础", sd1.kpNames().get(0));
        assertEquals("方程思想", sd1.kpNames().get(1));
    }

    // ======================== AC-2 · 格式校验 ========================

    @Test
    @DisplayName("AC-2: 只有 1 行表头 → 拒绝 A0011")
    void shouldRejectSingleHeaderRow() {
        String csv = String.join("\n",
                "学号,姓名,班级,考试编号,题1",
                "S001,张三,一班,E01,10/12"
        ) + "\n";

        BusinessException ex = assertThrows(BusinessException.class,
                () -> parser.parse(parseRequest(csv, "数学")));
        assertEquals("A0011", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("行数不足"));
    }

    @Test
    @DisplayName("AC-2: 成绩格式非 raw/max → 拒绝 A0011")
    void shouldRejectMalformedScore() {
        String csv = String.join("\n",
                headerRow1(1),
                headerRow2("知识点A"),
                dataRow("S001", "张三", "一班", "E01", "月考", "2024-06-01", "90", "2", "ABC")
        ) + "\n";

        BusinessException ex = assertThrows(BusinessException.class,
                () -> parser.parse(parseRequest(csv, "数学")));
        assertEquals("A0011", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("成绩格式错误"));
    }

    @Test
    @DisplayName("AC-2: 数据行列数不一致 → 拒绝 A0011")
    void shouldRejectColumnMismatch() {
        // headerRow1(2) = 8 + 2 = 10 columns, dataRow has only 1 score = 9 columns
        String csv = String.join("\n",
                headerRow1(2),
                headerRow2("KP1", "KP2"),
                dataRow("S001", "张三", "一班", "E01", "月考", "2024-06-01", "90", "2", "10/12")
        ) + "\n";

        BusinessException ex = assertThrows(BusinessException.class,
                () -> parser.parse(parseRequest(csv, "数学")));
        assertEquals("A0011", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("列数不一致"));
    }

    @Test
    @DisplayName("AC-2: 缺少必要列「学号」→ 拒绝 A0012")
    void shouldRejectMissingStudentNoColumn() {
        String csv = String.join("\n",
                "姓名,班级,考试编号,题1",
                ",,,KP1",
                "张三,一班,E01,10/12"
        ) + "\n";

        BusinessException ex = assertThrows(BusinessException.class,
                () -> parser.parse(parseRequest(csv, "数学")));
        assertEquals("A0012", ex.getErrorCode());
    }

    // ======================== AC-6 · 缺考标记 ========================

    @Test
    @DisplayName("AC-6: -/- 缺考 → rawScore=null, maxScore=null")
    void shouldHandleAbsentScore() {
        String csv = String.join("\n",
                headerRow1(2),
                headerRow2("KP1", "KP2"),
                dataRow("S001", "张三", "一班", "E01", "月考", "2024-06-01", "50", "10", "-/-", "10/20")
        ) + "\n";

        FileParseResult<CsvParsePayload> result = parser.parse(parseRequest(csv, "数学"));
        StudentRecord student = result.payload().students().get(0);

        assertNull(student.scoreDetails().get(0).rawScore());
        assertNull(student.scoreDetails().get(0).maxScore());
        assertEquals(10, student.scoreDetails().get(1).rawScore());
        assertEquals(50, student.totalScore());
    }

    @Test
    @DisplayName("AC-6: 空字符串或 / 也视为缺考")
    void shouldHandleEmptyOrSlashOnlyAsAbsent() {
        String csv = String.join("\n",
                headerRow1(2),
                headerRow2("KP1", "KP2"),
                dataRow("S001", "张三", "一班", "E01", "月考", "2024-06-01", "80", "5", "", "/")
        ) + "\n";

        FileParseResult<CsvParsePayload> result = parser.parse(parseRequest(csv, "数学"));
        StudentRecord student = result.payload().students().get(0);
        assertNull(student.scoreDetails().get(0).rawScore());
        assertNull(student.scoreDetails().get(1).rawScore());
    }

    // ======================== 边界值 ========================

    @Test
    @DisplayName("边界: 1名学生 + 1道题")
    void shouldParseMinimalCsv() {
        String csv = String.join("\n",
                headerRow1(1),
                headerRow2("单独知识点"),
                dataRow("S001", "张三", "一班", "E01", "月考", "2024-06-01", "100", "1", "10/10")
        ) + "\n";

        FileParseResult<CsvParsePayload> result = parser.parse(parseRequest(csv, "数学"));
        assertEquals(1, result.payload().students().size());
        assertEquals(1, result.payload().questionCount());
    }

    @Test
    @DisplayName("边界: 不含可选列（姓名/班级/总分/排名 → null）")
    void shouldParseCsvWithoutOptionalColumns() {
        String csv = String.join("\n",
                "学号,考试编号,题1",
                ",,KP1",
                "S001,E01,10/12"
        ) + "\n";

        FileParseResult<CsvParsePayload> result = parser.parse(parseRequest(csv, "数学"));
        StudentRecord sr = result.payload().students().get(0);
        assertEquals("S001", sr.studentNo());
        assertEquals("", sr.name());
        assertNull(sr.totalScore());
        assertNull(sr.classRank());
    }

    @Test
    @DisplayName("边界: 日期格式 yyyy/MM/dd 和 yyyyMMdd")
    void shouldParseMultipleDateFormats() {
        String csv = String.join("\n",
                headerRow1(1),
                headerRow2("KP1"),
                dataRow("S001", "张三", "一班", "E01", "月考", "2024/06/01", "90", "2", "10/12"),
                dataRow("S002", "李四", "一班", "E01", "月考", "20240601", "90", "2", "10/12")
        ) + "\n";

        FileParseResult<CsvParsePayload> result = parser.parse(parseRequest(csv, "数学"));
        assertEquals(LocalDate.of(2024, 6, 1), result.payload().examDate());
    }

    @Test
    @DisplayName("扩展名注册: 返回 .csv 和 CSV_GRADE")
    void shouldRegisterAsCsvGradeParser() {
        assertEquals(GradeFileType.CSV_GRADE, parser.supportedType());
        assertTrue(parser.supportedExtensions().contains(".csv"));
    }
}