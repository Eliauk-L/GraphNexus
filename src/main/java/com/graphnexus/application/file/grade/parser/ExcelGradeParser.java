package com.graphnexus.application.file.grade.parser;

import com.graphnexus.application.file.grade.model.GradeFileType;
import com.graphnexus.application.file.parse.model.FileParseType;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Excel 成绩文件解析器 — 双行表头格式（与 CSV 一致）。
 *
 * <p>继承 {@link AbstractGradeParser}，仅负责 Apache POI 工作簿读取，
 * 列分类、校验、知识点提取、分数解析等公共逻辑由父类提供。</p>
 *
 * @author Jay
 * @date 2026/06/20
 */
@Slf4j
@Component
public class ExcelGradeParser extends AbstractGradeParser {

    private Workbook workbook;
    private Sheet sheet;

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
    protected String getParserName() {
        return "Excel";
    }

    // ======================== 模板方法实现 ========================

    @Override
    protected void init(byte[] rawBytes) throws Exception {
        workbook = WorkbookFactory.create(new java.io.ByteArrayInputStream(rawBytes));
        sheet = workbook.getSheetAt(0);
    }

    @Override
    protected void cleanup() throws Exception {
        if (workbook != null) {
            workbook.close();
        }
        workbook = null;
        sheet = null;
    }

    @Override
    protected int getRowCount() {
        return sheet.getLastRowNum() + 1;
    }

    @Override
    protected String getCellValue(int row, int col) {
        Row r = sheet.getRow(row);
        if (r == null) return "";
        Cell cell = r.getCell(col);
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

    // ======================== 日期处理（覆盖以支持 Excel 日期数值） ========================

    @Override
    protected LocalDate parseDateCell(int row, Map<String, Integer> metaIndices) {
        if (!metaIndices.containsKey("日期")) return null;
        int col = metaIndices.get("日期");
        Row r = sheet.getRow(row);
        if (r == null) return null;
        Cell cell = r.getCell(col);
        if (cell == null) return null;

        // Excel 日期数值
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        // 字符串日期（回退到父类）
        String dateStr = getCellValue(row, col);
        if (dateStr.isEmpty()) return null;
        return parseDateString(dateStr);
    }
}