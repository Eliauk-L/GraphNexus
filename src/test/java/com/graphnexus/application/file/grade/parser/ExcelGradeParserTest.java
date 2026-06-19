package com.graphnexus.application.file.grade.parser;

import com.graphnexus.application.file.grade.model.GradeParsePayload;
import com.graphnexus.application.file.grade.model.GradeParsePayload.StudentRecord;
import com.graphnexus.application.file.grade.model.GradeParsePayload.ScoreDetail;
import com.graphnexus.application.file.grade.model.GradeFileType;
import com.graphnexus.application.file.parse.FileParseRequest;
import com.graphnexus.application.file.parse.FileParseResult;
import com.graphnexus.common.exception.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExcelGradeParser 单元测试 — 派生自 AC-2 / AC-5 / AC-11。
 *
 * @author Jay
 * @date 2026/06/19
 */
@DisplayName("ExcelGradeParser 成绩 Excel 解析测试")
class ExcelGradeParserTest {

    private static final ExcelGradeParser parser = new ExcelGradeParser();

    /** 创建 .xlsx 工作簿 */
    private XSSFWorkbook createXlsx() {
        return new XSSFWorkbook();
    }

    /** 创建 .xls 工作簿 */
    private HSSFWorkbook createXls() {
        return new HSSFWorkbook();
    }

    /** 工作簿 → 字节数组 */
    private byte[] toBytes(Workbook wb) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            wb.write(baos);
            wb.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 填充表头行（第 1 行 = 列名，第 2 行 = 知识点） */
    private void writeHeaders(Sheet sheet, String[] questionLabels, String[] knowledgePoints) {
        Row row1 = sheet.createRow(0);
        String[] meta = {"学号", "姓名", "班级", "考试编号", "考试名称", "日期", "总分", "班级排名"};
        int col = 0;
        for (String m : meta) row1.createCell(col++).setCellValue(m);
        for (String q : questionLabels) row1.createCell(col++).setCellValue(q);

        Row row2 = sheet.createRow(1);
        // 前 8 个元数据列留空
        for (int i = 0; i < 8; i++) row2.createCell(i).setCellValue("");
        col = 8;
        for (String kp : knowledgePoints) row2.createCell(col++).setCellValue(kp != null ? kp : "");
    }

    /** 填充数据行 */
    private void writeDataRow(Sheet sheet, int rowNum, String studentNo, String name,
                               String className, String examNo, String examName,
                               String dateStr, String totalScore, String rank, String... scores) {
        Row row = sheet.createRow(rowNum);
        int col = 0;
        row.createCell(col++).setCellValue(studentNo);
        row.createCell(col++).setCellValue(name);
        row.createCell(col++).setCellValue(className);
        row.createCell(col++).setCellValue(examNo);
        row.createCell(col++).setCellValue(examName);
        row.createCell(col++).setCellValue(dateStr);
        if (totalScore != null && !totalScore.isEmpty()) {
            row.createCell(col).setCellValue(Integer.parseInt(totalScore));
        }
        col++;
        if (rank != null && !rank.isEmpty()) {
            row.createCell(col).setCellValue(Integer.parseInt(rank));
        }
        col++;
        for (String score : scores) {
            row.createCell(col++).setCellValue(score != null ? score : "");
        }
    }

    private FileParseRequest parseRequest(byte[] bytes, String filename, String subject) {
        return new FileParseRequest(
                new ByteArrayInputStream(bytes), filename, subject, bytes
        );
    }

    // ======================== AC-2 · 正常 Excel 解析 ========================

    @Test
    @DisplayName("AC-2: .xlsx 3名学生 + 3道题 + 多知识点")
    void shouldParseValidXlsx() {
        XSSFWorkbook wb = createXlsx();
        Sheet sheet = wb.createSheet();
        writeHeaders(sheet,
                new String[]{"题1", "题2", "题3"},
                new String[]{"力学基础", "牛顿第二定律;力学基础", "能量守恒"});
        writeDataRow(sheet, 2, "S001", "张三", "初三(1)班", "E20250310", "三月月考",
                "2025-03-10", "85", "3", "10/12", "15/18", "8/10");
        writeDataRow(sheet, 3, "S002", "李四", "初三(1)班", "E20250310", "三月月考",
                "2025-03-10", "92", "1", "12/12", "-/-", "9/10");
        writeDataRow(sheet, 4, "S003", "王五", "初三(2)班", "E20250310", "三月月考",
                "2025-03-10", "78", "5", "8/12", "14/18", "7/10");

        FileParseResult<GradeParsePayload> result = parser.parse(
                parseRequest(toBytes(wb), "test.xlsx", "物理"));
        GradeParsePayload payload = result.payload();

        assertEquals(GradeFileType.EXCEL, result.parseType());
        assertEquals("E20250310", payload.examNo());
        assertEquals("三月月考", payload.examName());
        assertEquals(LocalDate.of(2025, 3, 10), payload.examDate());
        assertEquals("物理", payload.subject());
        assertEquals(3, payload.students().size());
        assertEquals(3, payload.questionCount());
        assertEquals(3, payload.knowledgePoints().size());

        StudentRecord zs = payload.students().get(0);
        assertEquals("S001", zs.studentNo());
        assertEquals("张三", zs.name());
        assertEquals(85, zs.totalScore());
        assertEquals(3, zs.classRank());
        ScoreDetail sd1 = zs.scoreDetails().get(0);
        assertEquals(10, sd1.rawScore());
        assertEquals(12, sd1.maxScore());
        assertEquals(List.of("力学基础"), sd1.kpNames());
    }

    @Test
    @DisplayName("AC-2: .xls 格式正常解析")
    void shouldParseValidXls() {
        HSSFWorkbook wb = createXls();
        Sheet sheet = wb.createSheet();
        writeHeaders(sheet,
                new String[]{"题1"},
                new String[]{"知识点A"});
        writeDataRow(sheet, 2, "S001", "张三", "一班", "E001", "测试",
                "2024-06-15", "90", "1", "10/10");

        FileParseResult<GradeParsePayload> result = parser.parse(
                parseRequest(toBytes(wb), "test.xls", "数学"));
        assertEquals(GradeFileType.EXCEL, result.parseType());
        assertEquals("E001", result.payload().examNo());
        assertEquals(1, result.payload().students().size());
    }

    // ======================== AC-5 · 格式校验 ========================

    @Test
    @DisplayName("AC-5: 行数不足（只有表头） → 拒绝 A0011")
    void shouldRejectTooFewRows() {
        XSSFWorkbook wb = createXlsx();
        Sheet sheet = wb.createSheet();
        writeHeaders(sheet, new String[]{"题1"}, new String[]{"KP1"});

        BusinessException ex = assertThrows(BusinessException.class,
                () -> parser.parse(parseRequest(toBytes(wb), "test.xlsx", "数学")));
        assertEquals("A0011", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("行数不足"));
    }

    @Test
    @DisplayName("AC-5: 成绩格式异常 → 拒绝 A0011")
    void shouldRejectMalformedScore() {
        XSSFWorkbook wb = createXlsx();
        Sheet sheet = wb.createSheet();
        writeHeaders(sheet, new String[]{"题1"}, new String[]{"KP1"});
        writeDataRow(sheet, 2, "S001", "张三", "一班", "E01", "月考",
                "2024-06-01", "90", "2", "ABC");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> parser.parse(parseRequest(toBytes(wb), "test.xlsx", "数学")));
        assertEquals("A0011", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("成绩格式错误"));
    }

    @Test
    @DisplayName("AC-5: 缺少必要列「学号」 → 拒绝 A0012")
    void shouldRejectMissingStudentNoColumn() {
        XSSFWorkbook wb = createXlsx();
        Sheet sheet = wb.createSheet();
        Row row1 = sheet.createRow(0);
        row1.createCell(0).setCellValue("姓名");
        row1.createCell(1).setCellValue("考试编号");
        row1.createCell(2).setCellValue("题1");
        Row row2 = sheet.createRow(1);
        row2.createCell(2).setCellValue("KP1");
        // 数据行
        Row row3 = sheet.createRow(2);
        row3.createCell(0).setCellValue("张三");
        row3.createCell(1).setCellValue("E01");
        row3.createCell(2).setCellValue("10/10");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> parser.parse(parseRequest(toBytes(wb), "test.xlsx", "数学")));
        assertEquals("A0012", ex.getErrorCode());
    }

    // ======================== AC-11 · 缺考标记 ========================

    @Test
    @DisplayName("AC-11: -/- 缺考 → rawScore=null, maxScore=null")
    void shouldHandleAbsentScore() {
        XSSFWorkbook wb = createXlsx();
        Sheet sheet = wb.createSheet();
        writeHeaders(sheet, new String[]{"题1", "题2"}, new String[]{"KP1", "KP2"});
        writeDataRow(sheet, 2, "S001", "张三", "一班", "E01", "月考",
                "2024-06-01", "50", "10", "-/-", "10/20");

        FileParseResult<GradeParsePayload> result = parser.parse(
                parseRequest(toBytes(wb), "test.xlsx", "数学"));
        StudentRecord student = result.payload().students().get(0);

        assertNull(student.scoreDetails().get(0).rawScore());
        assertNull(student.scoreDetails().get(0).maxScore());
        assertEquals(10, student.scoreDetails().get(1).rawScore());
    }

    // ======================== 日期格式兼容 ========================

    @Test
    @DisplayName("日期列: Excel 日期数值格式")
    void shouldParseExcelDateValue() {
        XSSFWorkbook wb = createXlsx();
        Sheet sheet = wb.createSheet();
        writeHeaders(sheet, new String[]{"题1"}, new String[]{"KP1"});

        Row row = sheet.createRow(2);
        row.createCell(0).setCellValue("S001");
        row.createCell(1).setCellValue("张三");
        row.createCell(2).setCellValue("一班");
        row.createCell(3).setCellValue("E001");
        row.createCell(4).setCellValue("月考");
        // 日期列: 数值类型
        Cell dateCell = row.createCell(5);
        dateCell.setCellValue("2024-06-15");
        row.createCell(6).setCellValue(90);
        row.createCell(7).setCellValue(1);
        row.createCell(8).setCellValue("10/10");

        FileParseResult<GradeParsePayload> result = parser.parse(
                parseRequest(toBytes(wb), "test.xlsx", "数学"));
        assertEquals(LocalDate.of(2024, 6, 15), result.payload().examDate());
    }

    // ======================== 边界用例 ========================

    @Test
    @DisplayName("边界: 跳过空行")
    void shouldSkipEmptyRows() {
        XSSFWorkbook wb = createXlsx();
        Sheet sheet = wb.createSheet();
        writeHeaders(sheet, new String[]{"题1"}, new String[]{"KP1"});
        writeDataRow(sheet, 2, "S001", "张三", "一班", "E001", "月考",
                "2024-06-15", "90", "1", "10/10");
        // 空行
        sheet.createRow(3);
        writeDataRow(sheet, 4, "S002", "李四", "一班", "E001", "月考",
                "2024-06-15", "80", "2", "8/10");

        FileParseResult<GradeParsePayload> result = parser.parse(
                parseRequest(toBytes(wb), "test.xlsx", "数学"));
        assertEquals(2, result.payload().students().size());
    }

    @Test
    @DisplayName("边界: 可选列缺失时仍正常解析")
    void shouldHandleMissingOptionalColumns() {
        XSSFWorkbook wb = createXlsx();
        Sheet sheet = wb.createSheet();
        // 只有必要列
        Row row1 = sheet.createRow(0);
        row1.createCell(0).setCellValue("学号");
        row1.createCell(1).setCellValue("考试编号");
        row1.createCell(2).setCellValue("题1");
        Row row2 = sheet.createRow(1);
        row2.createCell(2).setCellValue("KP1");
        Row row3 = sheet.createRow(2);
        row3.createCell(0).setCellValue("S001");
        row3.createCell(1).setCellValue("E001");
        row3.createCell(2).setCellValue("10/10");

        FileParseResult<GradeParsePayload> result = parser.parse(
                parseRequest(toBytes(wb), "test.xlsx", "数学"));
        assertEquals("S001", result.payload().students().get(0).studentNo());
    }
}