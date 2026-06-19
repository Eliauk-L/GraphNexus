package com.graphnexus.application.file.grade.parser;

import com.graphnexus.application.file.parse.FileParser;

import com.graphnexus.application.file.parse.FileParseResult;
import com.graphnexus.application.file.parse.FileParseType;
import com.graphnexus.application.file.grade.model.GradeFileType;
import com.graphnexus.application.file.grade.model.GradeParsePayload;
import com.graphnexus.application.file.grade.model.GradeParsePayload.StudentRecord;
import com.graphnexus.application.file.grade.model.GradeParsePayload.ScoreDetail;
import com.graphnexus.application.file.parse.FileParseRequest;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * CSV 成绩文件解析器 — 双行表头格式。
 *
 * <p>实现 {@link FileParser} 接口，由 {@link FileParserRegistry} 自动注册。
 * 见 DESIGN D2 (Commons CSV) + D6 (KP 策略) + D12 (策略+工厂)。</p>
 *
 * <h3>CSV 格式（双行表头）</h3>
 * <pre>
 * 第 1 行：学号,姓名,班级,考试编号,考试名称,日期,总分,班级排名,题1,题2,...,题N
 * 第 2 行：,,,,,,,知识点A,知识点B;知识点C,...,知识点N
 * 第 3+ 行：S001,张三,一班,E20240601,月考,2024-06-01,85,5,10/12,8/10,...,-/-
 * </pre>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Slf4j
@Component
public class CsvGradeParser implements FileParser {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 第 1 行表头中非题号的元数据列名 */
    private static final Set<String> META_COLUMNS = Set.of(
            "学号", "姓名", "班级", "考试编号", "考试名称", "日期", "总分", "班级排名"
    );

    @Override
    public FileParseType supportedType() {
        return GradeFileType.CSV;
    }

    @Override
    public String businessType() {
        return BIZ_GRADE;
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".csv");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> FileParseResult<T> parse(FileParseRequest request) {
        try {
            GradeParsePayload payload = doParse(
                    new ByteArrayInputStream(request.rawBytes()),
                    request.subject()
            );
            return (FileParseResult<T>) new FileParseResult<>(payload, GradeFileType.CSV_GRADE);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("CSV 解析异常: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.A0011, "CSV 解析失败: " + e.getMessage());
        }
    }

    /**
     * 核心解析逻辑。
     */
    private GradeParsePayload doParse(InputStream inputStream, String subject) throws IOException {
        // ① 读取全部字节，尝试 UTF-8 → GBK 回退
        byte[] rawBytes = inputStream.readAllBytes();
        String content = tryDecode(rawBytes);
        // 去除 UTF-8 BOM（Excel 导出的 CSV 首字符常为 ﻿）
        if (!content.isEmpty() && content.charAt(0) == '﻿') {
            content = content.substring(1);
        }

        // ② 使用 Commons CSV 解析
        CSVParser parser = CSVParser.parse(content, CSVFormat.DEFAULT.withTrim());
        List<CSVRecord> allRecords = parser.getRecords();

        if (allRecords.size() < 3) {
            throw new BusinessException(ErrorCode.A0011,
                    "CSV 行数不足，至少需要 3 行（表头 2 行 + 数据 ≥ 1 行），实际 " + allRecords.size() + " 行");
        }

        // ③ 解析双行表头
        CSVRecord headerRow1 = allRecords.get(0);  // 列名行
        CSVRecord headerRow2 = allRecords.get(1);  // 知识点行

        if (headerRow1.size() != headerRow2.size()) {
            throw new BusinessException(ErrorCode.A0011,
                    "两行表头列数不一致：第 1 行 " + headerRow1.size() + " 列，第 2 行 " + headerRow2.size() + " 列");
        }

        // ④ 定位元数据列和题号列
        List<Integer> questionCols = new ArrayList<>();
        Map<String, Integer> metaIndices = new HashMap<>();

        for (int i = 0; i < headerRow1.size(); i++) {
            String colName = headerRow1.get(i).trim();
            if (META_COLUMNS.contains(colName)) {
                metaIndices.put(colName, i);
            } else {
                // 非元数据列 → 视为题号列
                questionCols.add(i);
            }
        }

        // 校验必要列
        if (!metaIndices.containsKey("学号")) {
            throw new BusinessException(ErrorCode.A0012, "CSV 缺少必要列「学号」");
        }
        if (!metaIndices.containsKey("考试编号")) {
            throw new BusinessException(ErrorCode.A0012, "CSV 缺少必要列「考试编号」");
        }
        if (questionCols.isEmpty()) {
            throw new BusinessException(ErrorCode.A0011, "CSV 未检测到题号列（如「题1」「题2」...）");
        }

        // ⑤ 提取每题的知识点名称（从第 2 行）
        List<QuestionMeta> questionMetas = new ArrayList<>();
        Set<String> allKps = new LinkedHashSet<>();

        for (int i = 0; i < questionCols.size(); i++) {
            int col = questionCols.get(i);
            String questionLabel = headerRow1.get(col).trim();     // "题1"
            String kpRaw = headerRow2.get(col).trim();             // "二次函数顶点式;对称轴"

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

        // ⑥ 解析考试元数据（从第一条数据行提取）
        CSVRecord firstData = allRecords.get(2);
        String examNo = firstData.get(metaIndices.get("考试编号")).trim();
        String examName = metaIndices.containsKey("考试名称")
                ? firstData.get(metaIndices.get("考试名称")).trim() : "";
        LocalDate examDate = parseDate(firstData, metaIndices);

        // ⑦ 逐行解析学生数据
        List<StudentRecord> students = new ArrayList<>();
        for (int rowIdx = 2; rowIdx < allRecords.size(); rowIdx++) {
            CSVRecord row = allRecords.get(rowIdx);
            int lineNum = rowIdx + 1; // CSV 行号从 1 起

            // 校验列数
            if (row.size() != headerRow1.size()) {
                throw new BusinessException(ErrorCode.A0011,
                        "第 " + lineNum + " 行列数不一致：期望 " + headerRow1.size()
                                + " 列，实际 " + row.size() + " 列");
            }

            String studentNo = row.get(metaIndices.get("学号")).trim();
            String name = metaIndices.containsKey("姓名") ? row.get(metaIndices.get("姓名")).trim() : "";
            String className = metaIndices.containsKey("班级") ? row.get(metaIndices.get("班级")).trim() : "";

            // 解析总分与排名
            Integer totalScore = null;
            if (metaIndices.containsKey("总分")) {
                String ts = row.get(metaIndices.get("总分")).trim();
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
                String cr = row.get(metaIndices.get("班级排名")).trim();
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
                String raw = row.get(qm.colIndex).trim();
                Integer rawScore = null;
                Integer maxScore = null;

                if ("-/-".equals(raw) || "/".equals(raw) || raw.isEmpty()) {
                    // 缺考
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

        parser.close();
        return new GradeParsePayload(examNo, examName, examDate, subject, students, questionMetas.size(), new ArrayList<>(allKps));
    }

    /**
     * 尝试 UTF-8 → GBK 解码。
     */
    private String tryDecode(byte[] bytes) {
        // 先尝试 UTF-8
        try {
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // fall through
        }
        // 回退 GBK
        try {
            return new String(bytes, Charset.forName("GBK"));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.A0013,
                    "CSV 编码解码失败，请使用 UTF-8 或 GBK 编码");
        }
    }

    /**
     * 解析考试日期，支持 yyyy-MM-dd、yyyy/MM/dd 等常见格式。
     */
    private LocalDate parseDate(CSVRecord row, Map<String, Integer> metaIndices) {
        if (!metaIndices.containsKey("日期")) {
            return null;
        }
        String dateStr = row.get(metaIndices.get("日期")).trim();
        if (dateStr.isEmpty()) {
            return null;
        }
        // 统一分割符
        dateStr = dateStr.replace('/', '-').replace('.', '-');
        try {
            return LocalDate.parse(dateStr, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            try {
                // 尝试 yyyyMMdd 无分隔符
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
            } catch (DateTimeParseException e2) {
                throw new BusinessException(ErrorCode.A0011,
                        "日期格式无法解析: " + dateStr + "（支持 yyyy-MM-dd、yyyy/MM/dd、yyyyMMdd）");
            }
        }
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