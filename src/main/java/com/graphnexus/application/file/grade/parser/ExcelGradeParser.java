package com.graphnexus.application.file.grade.parser;

import com.graphnexus.application.file.grade.model.GradeFileType;
import com.graphnexus.application.file.grade.model.GradeParsePayload;
import com.graphnexus.application.file.grade.model.GradeParsePayload.StudentRecord;
import com.graphnexus.application.file.grade.model.GradeParsePayload.ScoreDetail;
import com.graphnexus.application.file.parse.FileParser;
import com.graphnexus.application.file.parse.FileParseRequest;
import com.graphnexus.application.file.parse.FileParseResult;
import com.graphnexus.application.file.parse.FileParseType;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Excel 成绩文件解析器 — 双行表头格式（与 CSV 一致）。
 *
 * <p>实现 {@link FileParser} 接口，由 {@link FileParserRegistry} 自动注册。
 * 使用 Apache POI 读取 .xlsx/.xls 工作簿，解析结果与 {@link CsvGradeParser} 一致。</p>
 *
 * <h3>Excel 格式（双行表头）</h3>
 * <pre>
 * 第 1 行：学号,姓名,班级,考试编号,考试名称,日期,总分,班级排名,题1,题2,...,题N
 * 第 2 行：,,,,,,,知识点A,知识点B;知识点C,...,知识点N
 * 第 3+ 行：S001,张三,一班,E20240601,月考,2024-06-01,85,5,10/12,8/10,...,-/-
 * </pre>
 *
 * <p>假设 Excel 无合并单元格（与 CSV 双行表头结构一致）。合并单元格场景 v2。</p>
 *
 * @author Jay
 * @date 2026/06/19
 */
@Slf4j
@Component
public class ExcelGradeParser implements FileParser {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    };

    /** 第 1 行表头中非题号的元数据列名 */
    private static final Set<String> META_COLUMNS = Set.of(
            "学号", "姓名", "班级", "考试编号", "考试名称", "日期", "总分", "班级排名"
    );

    @Override
    public FileParseType supportedType() {
        return GradeFileType.EXCEL;
    }

    @Override
    public String businessType() {
        return BIZ_GRADE;
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".xlsx", ".xls");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> FileParseResult<T> parse(FileParseRequest request) {
        try {
            GradeParsePayload payload = doParse(new ByteArrayInputStream(request.rawBytes()), request.subject());
            return (FileParseResult<T>) new FileParseResult<>(payload, GradeFileType.EXCEL);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excel 解析异常: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.A0011, "Excel 解析失败: " + e.getMessage());
        }
    }

    /**
     * 核心解析逻辑。
     */
    private GradeParsePayload doParse(ByteArrayInputStream inputStream, String subject) throws Exception {
        Workbook workbook = WorkbookFactory.create(inputStream);
        Sheet sheet = workbook.getSheetAt(0);

        int lastRowNum = sheet.getLastRowNum();
        if (lastRowNum < 2) {
            workbook.close();
            throw new BusinessException(ErrorCode.A0011,
                    "Excel 行数不足，至少需要 3 行（表头 2 行 + 数据 ≥ 1 行），实际 " + (lastRowNum + 1) + " 行");
        }

        // ① 读取双行表头
        Row headerRow1 = sheet.getRow(0);
        Row headerRow2 = sheet.getRow(1);

        if (headerRow1 == null || headerRow2 == null) {
            workbook.close();
            throw new BusinessException(ErrorCode.A0011, "Excel 缺少表头行");
        }

        int colCount = headerRow1.getLastCellNum();
        if (colCount < 0) {
            workbook.close();
            throw new BusinessException(ErrorCode.A0011, "Excel 表头列为空");
        }

        // ② 定位元数据列和题号列
        List<Integer> questionCols = new ArrayList<>();
        Map<String, Integer> metaIndices = new HashMap<>();

        for (int i = 0; i < colCount; i++) {
            String colName = getCellString(headerRow1, i);
            if (colName.isEmpty()) {
                break; // 空白列之后视为结束
            }
            if (META_COLUMNS.contains(colName)) {
                metaIndices.put(colName, i);
            } else {
                questionCols.add(i);
            }
        }

        // 校验必要列
        if (!metaIndices.containsKey("学号")) {
            workbook.close();
            throw new BusinessException(ErrorCode.A0012, "Excel 缺少必要列「学号」");
        }
        if (!metaIndices.containsKey("考试编号")) {
            workbook.close();
            throw new BusinessException(ErrorCode.A0012, "Excel 缺少必要列「考试编号」");
        }
        if (questionCols.isEmpty()) {
            workbook.close();
            throw new BusinessException(ErrorCode.A0011, "Excel 未检测到题号列（如「题1」「题2」...）");
        }

        // ③ 提取每题的知识点名称（从第 2 行）
        List<QuestionMeta> questionMetas = new ArrayList<>();
        Set<String> allKps = new LinkedHashSet<>();

        for (int i = 0; i < questionCols.size(); i++) {
            int col = questionCols.get(i);
            String questionLabel = getCellString(headerRow1, col);
            String kpRaw = getCellString(headerRow2, col);

            List<String> kpNames = new ArrayList<>();
            if (!kpRaw.isEmpty()) {
                for (String kp : kpRaw.split(";")) {
                    String trimmed = kp.trim();
                    if (!trimmed.isEmpty()) {
                        kpNames.add(trimmed);
                        allKps.add(trimmed);
                    }
                }
            }
            questionMetas.add(new QuestionMeta(questionLabel, col, kpNames));
        }

        // ④ 解析考试元数据（从第一条数据行提取）
        Row firstData = sheet.getRow(2);
        if (firstData == null) {
            workbook.close();
            throw new BusinessException(ErrorCode.A0011, "Excel 数据行为空");
        }

        String examNo = getCellString(firstData, metaIndices.get("考试编号"));
        String examName = metaIndices.containsKey("考试名称")
                ? getCellString(firstData, metaIndices.get("考试名称")) : "";
        LocalDate examDate = parseDate(firstData, metaIndices);

        // ⑤ 逐行解析学生数据
        List<StudentRecord> students = new ArrayList<>();
        for (int rowIdx = 2; rowIdx <= lastRowNum; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue; // 跳过空行

            int lineNum = rowIdx + 1;

            String studentNo = getCellString(row, metaIndices.get("学号"));
            if (studentNo.isEmpty()) continue; // 跳过空行

            String name = metaIndices.containsKey("姓名") ? getCellString(row, metaIndices.get("姓名")) : "";
            String className = metaIndices.containsKey("班级") ? getCellString(row, metaIndices.get("班级")) : "";

            // 解析总分与排名
            Integer totalScore = null;
            if (metaIndices.containsKey("总分")) {
                String ts = getCellString(row, metaIndices.get("总分"));
                if (!ts.isEmpty() && !"-".equals(ts)) {
                    try { totalScore = Integer.parseInt(ts); }
                    catch (NumberFormatException e) {
                        throw new BusinessException(ErrorCode.A0011,
                                "第 " + lineNum + " 行总分格式错误: " + ts);
                    }
                }
            }

            Integer classRank = null;
            if (metaIndices.containsKey("班级排名")) {
                String cr = getCellString(row, metaIndices.get("班级排名"));
                if (!cr.isEmpty() && !"-".equals(cr)) {
                    try { classRank = Integer.parseInt(cr); }
                    catch (NumberFormatException e) {
                        throw new BusinessException(ErrorCode.A0011,
                                "第 " + lineNum + " 行班级排名格式错误: " + cr);
                    }
                }
            }

            // 解析每题得分
            List<ScoreDetail> scoreDetails = new ArrayList<>();
            for (QuestionMeta qm : questionMetas) {
                String raw = getCellString(row, qm.colIndex);
                Integer rawScore = null;
                Integer maxScore = null;

                if ("-/-".equals(raw) || "/".equals(raw) || raw.isEmpty()) {
                    rawScore = null;
                    maxScore = null;
                } else if (raw.contains("/")) {
                    String[] parts = raw.split("/", 2);
                    try {
                        rawScore = parseScore(parts[0], lineNum, "原始分");
                        maxScore = parseScore(parts[1], lineNum, "满分");
                    } catch (NumberFormatException e) {
                        throw new BusinessException(ErrorCode.A0011,
                                "第 " + lineNum + " 行 " + qm.label + " 成绩格式错误: " + raw);
                    }
                } else {
                    throw new BusinessException(ErrorCode.A0011,
                            "第 " + lineNum + " 行 " + qm.label + " 成绩格式错误（应为 raw_score/max_score 或 -/-）: " + raw);
                }

                scoreDetails.add(new ScoreDetail(qm.label, qm.kpNames, rawScore, maxScore));
            }

            students.add(new StudentRecord(studentNo, name, className, totalScore, classRank, scoreDetails));
        }

        workbook.close();
        return new GradeParsePayload(examNo, examName, examDate, subject, students, questionMetas.size(), new ArrayList<>(allKps));
    }

    // ======================== 单元格工具 ========================

    /**
     * 获取单元格的字符串值。
     * 支持 STRING、NUMERIC（含日期）、BOOLEAN、FORMULA 类型。
     */
    private String getCellString(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().format(DATE_FORMATS[0]);
                }
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try {
                        yield String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        yield "";
                    }
                }
            }
            default -> "";
        };
    }

    /**
     * 解析考试日期，兼容 Excel 日期数值和字符串格式。
     */
    private LocalDate parseDate(Row row, Map<String, Integer> metaIndices) {
        if (!metaIndices.containsKey("日期")) {
            return null;
        }
        int col = metaIndices.get("日期");
        Cell cell = row.getCell(col);
        if (cell == null) return null;

        // Excel 日期数值
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        // 字符串日期
        String dateStr = getCellString(row, col);
        if (dateStr.isEmpty()) return null;

        dateStr = dateStr.replace('/', '-').replace('.', '-');
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new BusinessException(ErrorCode.A0011,
                "日期格式无法解析: " + dateStr + "（支持 yyyy-MM-dd、yyyy/MM/dd、yyyyMMdd）");
    }

    /**
     * 解析分数（字符串 → Integer，支持 "-" 表示 null）。
     */
    private Integer parseScore(String s, int lineNum, String label) {
        s = s.trim();
        if (s.isEmpty() || "-".equals(s)) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.A0011,
                    "第 " + lineNum + " 行" + label + "格式错误: " + s);
        }
    }

    // ======================== 内部数据类 ========================

    /**
     * 题号-列索引-知识点映射（解析阶段内部使用）。
     */
    private record QuestionMeta(
            String label,
            int colIndex,
            List<String> kpNames
    ) {}
}