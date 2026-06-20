package com.graphnexus.application.file.grade.parser;

import com.graphnexus.application.file.grade.model.GradeParsePayload;
import com.graphnexus.application.file.grade.model.GradeParsePayload.StudentRecord;
import com.graphnexus.application.file.grade.model.GradeParsePayload.ScoreDetail;
import com.graphnexus.application.file.parse.FileParser;
import com.graphnexus.application.file.parse.model.FileParseRequest;
import com.graphnexus.application.file.parse.model.FileParseResult;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 成绩文件解析器抽象基类。
 *
 * <p>封装双行表头成绩文件的公共解析管线（列分类、知识点提取、校验、分数解析），
 * 子类仅需实现具体的行读取方式（CSV / Excel）。</p>
 *
 * <h3>子类需实现的模板方法</h3>
 * <ul>
 *   <li>{@link #init(byte[])} — 打开/初始化解析资源</li>
 *   <li>{@link #cleanup()} — 关闭/清理解析资源</li>
 *   <li>{@link #getRowCount()} — 返回总行数</li>
 *   <li>{@link #getCellValue(int, int)} — 返回指定单元格的字符串值</li>
 * </ul>
 *
 * @author Jay
 * @date 2026/06/20
 */
@Slf4j
public abstract class AbstractGradeParser implements FileParser {

    protected static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    };

    /** 第 1 行表头中非题号的元数据列名 */
    protected static final Set<String> META_COLUMNS = Set.of(
            "学号", "姓名", "班级", "考试编号", "考试名称", "日期", "总分", "班级排名"
    );

    // ======================== 模板方法（子类实现） ========================

    /** 打开/初始化解析资源 */
    protected abstract void init(byte[] rawBytes) throws Exception;

    /** 关闭/清理解析资源 */
    protected abstract void cleanup() throws Exception;

    /** 返回总行数（含表头） */
    protected abstract int getRowCount();

    /** 返回指定位置的单元格字符串值 */
    protected abstract String getCellValue(int row, int col);

    // ======================== 公共解析管线 ========================

    @Override
    @SuppressWarnings("unchecked")
    public <T> FileParseResult<T> parse(FileParseRequest request) {
        try {
            GradeParsePayload payload = doParse(request.rawBytes(), request.subject());
            return (FileParseResult<T>) new FileParseResult<>(payload, supportedType());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("{} 解析异常: {}", getParserName(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.A0011, getParserName() + " 解析失败: " + e.getMessage());
        }
    }

    /** 子类可覆盖以定制异常消息中的解析器名称 */
    protected String getParserName() {
        return supportedType().name();
    }

    /**
     * 核心解析管线。
     */
    protected GradeParsePayload doParse(byte[] rawBytes, String subject) throws Exception {
        init(rawBytes);
        try {
            int totalRows = getRowCount();
            if (totalRows < 3) {
                throw new BusinessException(ErrorCode.A0011,
                        "行数不足，至少需要 3 行（表头 2 行 + 数据 ≥ 1 行），实际 " + totalRows + " 行");
            }

            // ① 读取双行表头
            int headerColCount = getHeaderColumnCount(0);
            if (headerColCount < 2) {
                throw new BusinessException(ErrorCode.A0011, "表头列为空");
            }

            // ② 定位元数据列和题号列
            Map<String, Integer> metaIndices = new HashMap<>();
            List<Integer> questionCols = new ArrayList<>();
            classifyColumns(0, headerColCount, metaIndices, questionCols);

            // 校验必要列
            validateRequiredColumns(metaIndices, questionCols);

            // ③ 提取每题的知识点名称（从第 2 行 = row index 1）
            List<QuestionMeta> questionMetas = new ArrayList<>();
            Set<String> allKps = new LinkedHashSet<>();
            for (int i = 0; i < questionCols.size(); i++) {
                int col = questionCols.get(i);
                String questionLabel = getCellValue(0, col);
                String kpRaw = getCellValue(1, col);

                List<String> kpNames = new ArrayList<>();
                if (kpRaw != null && !kpRaw.isEmpty()) {
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

            // ④ 解析考试元数据（从第一条数据行 = row index 2）
            String examNo = getCellValue(2, metaIndices.get("考试编号"));
            String examName = metaIndices.containsKey("考试名称")
                    ? nullable(getCellValue(2, metaIndices.get("考试名称"))) : "";
            LocalDate examDate = parseDateCell(2, metaIndices);

            // ⑤ 逐行解析学生数据（row 2 ~ rowCount-1）
            List<StudentRecord> students = new ArrayList<>();
            for (int row = 2; row < totalRows; row++) {
                String studentNo = trimToNull(getCellValue(row, metaIndices.get("学号")));
                if (studentNo == null) continue; // 跳过空行

                int lineNum = row + 1;
                validateDataRow(row, headerColCount);

                String name = metaIndices.containsKey("姓名")
                        ? nullable(getCellValue(row, metaIndices.get("姓名"))) : "";
                String className = metaIndices.containsKey("班级")
                        ? nullable(getCellValue(row, metaIndices.get("班级"))) : "";

                Integer totalScore = parseNullableInt(row, metaIndices, "总分", lineNum);
                Integer classRank = parseNullableInt(row, metaIndices, "班级排名", lineNum);

                List<ScoreDetail> scoreDetails = new ArrayList<>();
                for (QuestionMeta qm : questionMetas) {
                    String raw = trimToNull(getCellValue(row, qm.colIndex));
                    if (raw == null) raw = "";
                    scoreDetails.add(parseScoreDetail(raw, qm.label, lineNum, qm.kpNames));
                }

                students.add(new StudentRecord(studentNo, name, className, totalScore, classRank, scoreDetails));
            }

            return new GradeParsePayload(examNo, examName, examDate, subject,
                    students, questionMetas.size(), new ArrayList<>(allKps));
        } finally {
            cleanup();
        }
    }

    // ======================== 公共解析工具方法 ========================

    /** 获取第 0 行表头的有效列数（遇到空白列停止） */
    private int getHeaderColumnCount(int headerRow) {
        int count = 0;
        while (true) {
            String val = getCellValue(headerRow, count);
            if (val == null || val.trim().isEmpty()) break;
            count++;
        }
        return count;
    }

    /** 分类列：元数据列 vs 题号列 */
    private void classifyColumns(int headerRow, int colCount,
                                  Map<String, Integer> metaIndices, List<Integer> questionCols) {
        for (int i = 0; i < colCount; i++) {
            String colName = getCellValue(headerRow, i).trim();
            if (colName.isEmpty()) break;
            if (META_COLUMNS.contains(colName)) {
                metaIndices.put(colName, i);
            } else {
                questionCols.add(i);
            }
        }
    }

    /** 校验必要列 */
    private void validateRequiredColumns(Map<String, Integer> metaIndices, List<Integer> questionCols) {
        if (!metaIndices.containsKey("学号")) {
            throw new BusinessException(ErrorCode.A0012, "缺少必要列「学号」");
        }
        if (!metaIndices.containsKey("考试编号")) {
            throw new BusinessException(ErrorCode.A0012, "缺少必要列「考试编号」");
        }
        if (questionCols.isEmpty()) {
            throw new BusinessException(ErrorCode.A0011, "未检测到题号列（如「题1」「题2」...）");
        }
    }

    /**
     * 校验数据行的列数是否与表头一致（钩子方法，默认不校验）。
     * 子类（如 CsvGradeParser）可覆盖以启用严格列数检查。
     */
    protected void validateDataRow(int row, int expectedCols) {
        // default: no-op（Excel 行中空白单元格天然存在）
    }

    /** 解析日期（子类可覆盖以处理 Excel 日期数值） */
    protected LocalDate parseDateCell(int row, Map<String, Integer> metaIndices) {
        if (!metaIndices.containsKey("日期")) return null;
        String dateStr = trimToNull(getCellValue(row, metaIndices.get("日期")));
        if (dateStr == null) return null;
        return parseDateString(dateStr);
    }

    /** 字符串 → 日期（多格式尝试） */
    protected LocalDate parseDateString(String dateStr) {
        dateStr = dateStr.replace('/', '-').replace('.', '-');
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        throw new BusinessException(ErrorCode.A0011,
                "日期格式无法解析: " + dateStr + "（支持 yyyy-MM-dd、yyyy/MM/dd、yyyyMMdd）");
    }

    /** 解析可为 null 的整数字段（如总分、排名） */
    private Integer parseNullableInt(int row, Map<String, Integer> metaIndices,
                                      String key, int lineNum) {
        if (!metaIndices.containsKey(key)) return null;
        String raw = trimToNull(getCellValue(row, metaIndices.get(key)));
        if (raw == null) return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.A0011,
                    "第 " + lineNum + " 行" + key + "格式错误: " + raw);
        }
    }

    /** 解析单题得分 */
    private ScoreDetail parseScoreDetail(String raw, String label, int lineNum, List<String> kpNames) {
        Integer rawScore = null;
        Integer maxScore = null;

        if ("-/-".equals(raw) || "/".equals(raw) || raw.isEmpty()) {
            // 缺考
        } else if (raw.contains("/")) {
            String[] parts = raw.split("/", 2);
            try {
                rawScore = parseScore(parts[0], lineNum, "原始分");
                maxScore = parseScore(parts[1], lineNum, "满分");
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.A0011,
                        "第 " + lineNum + " 行 " + label + " 成绩格式错误: " + raw);
            }
        } else {
            throw new BusinessException(ErrorCode.A0011,
                    "第 " + lineNum + " 行 " + label + " 成绩格式错误（应为 raw_score/max_score 或 -/-）: " + raw);
        }
        return new ScoreDetail(label, kpNames, rawScore, maxScore);
    }

    /** 解析单个分数值 */
    private Integer parseScore(String s, int lineNum, String label) {
        s = s.trim();
        if (s.isEmpty() || "-".equals(s)) return null;
        return Integer.parseInt(s);
    }

    /** 空字符串 → null */
    static String trimToNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    /** null → "" */
    static String nullable(String s) {
        return s == null ? "" : s;
    }

    // ======================== 内部数据类 ========================

    /**
     * 题号-列索引-知识点映射（解析阶段内部使用）。
     */
    protected record QuestionMeta(
            String label,
            int colIndex,
            List<String> kpNames
    ) {}
}