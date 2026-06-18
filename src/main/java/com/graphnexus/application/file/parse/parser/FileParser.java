package com.graphnexus.application.file.parse.parser;

import com.graphnexus.application.file.parse.model.FileParseRequest;
import com.graphnexus.application.file.parse.model.FileParseResult;
import com.graphnexus.application.file.parse.model.FileParseType;

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
 *   ├── DocumentParser（文档解析，如 PDF）
 *   │     ├── PdfBoxDocumentParser
 *   │     └── MinerUDocumentParser
 *   └── CsvGradeParser（CSV 成绩解析，直接实现 FileParser）
 * </pre>
 *
 * @author Jay
 * @date 2026/06/15
 */
public interface FileParser {

    /**
     * 返回本解析器处理的文件类型枚举值。
     */
    FileParseType supportedType();

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
}