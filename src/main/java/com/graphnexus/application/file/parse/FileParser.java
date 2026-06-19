package com.graphnexus.application.file.parse;

import java.util.Set;

/**
 * 统一文件解析器接口（策略模式）。
 *
 * <p>所有文件类型的解析器实现此接口，由 {@link FileParserRegistry}
 * 按扩展名自动路由。新增文件类型只需写新实现类，调用方零改动。
 * 见 DESIGN D12。</p>
 *
 * <p>继承体系：</p>
 * <pre>
 * FileParser（通用文件解析）
 *   ├── TextbookParser（文档解析，如 PDF/TXT）
 *   │     ├── PdfBoxTextbookParser
 *   │     ├── MinerUTextbookParser
 *   │     └── TxtTextbookParser
 *   └── CsvGradeParser（CSV 成绩解析，直接实现 FileParser）
 * </pre>
 *
 * @author Jay
 * @date 2026/06/15
 */
public interface FileParser {

    /** TEXTBOOK 业务类型标识 */
    String BIZ_TEXTBOOK = "TEXTBOOK";
    /** GRADE 业务类型标识 */
    String BIZ_GRADE = "GRADE";

    /**
     * 返回本解析器处理的文件类型枚举值。
     */
    FileParseType supportedType();

    /**
     * 返回本解析器所属的业务类型（{@value #BIZ_TEXTBOOK} 或 {@value #BIZ_GRADE}）。
     */
    String businessType();

    /**
     * 返回本解析器处理的文件扩展名集合（含点号，如 {@code ".csv"}、{@code ".pdf"}）。
     */
    Set<String> supportedExtensions();

    /**
     * 解析文件内容。
     *
     * @param request 统一解析请求（含 InputStream + 文件名 + 业务元数据 + 原始字节）
     * @param <T>     payload 类型（由各解析器自行定义）
     * @return 解析结果
     */
    <T> FileParseResult<T> parse(FileParseRequest request);

    /**
     * 解析器优先级，数值越小优先级越高。
     * 同一扩展名注册多个解析器时，按 priority 升序排列构成兜底链。
     * 默认 0（最高优先级）。
     *
     * <p>示例：MinerU(0) → PDFBox(1) 在 PDF 解析时 MinerU 优先。</p>
     *
     * @see FileParserRegistry#getParsers(String)
     */
    default int priority() {
        return 0;
    }
}