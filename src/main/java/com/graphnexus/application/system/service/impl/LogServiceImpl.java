package com.graphnexus.application.system.service.impl;

import com.graphnexus.api.system.dto.LogContentVO;
import com.graphnexus.api.system.dto.LogFileVO;
import com.graphnexus.application.system.service.LogService;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 日志文件管理业务实现。
 *
 * <p>双重路径校验防遍历攻击（AD R-048）：
 * 字符级黑名单 + OS 级 canonical path 前缀检查。</p>
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@Service
public class LogServiceImpl implements LogService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_PAGE_SIZE = 500;

    private final Path logDir;

    public LogServiceImpl(@Value("${logging.file.dir:logs}") String logDirPath) {
        this.logDir = Paths.get(logDirPath).toAbsolutePath().normalize();
    }

    @Override
    public List<LogFileVO> listFiles() {
        if (!Files.isDirectory(logDir)) {
            log.warn("日志目录不存在: {}", logDir);
            return List.of();
        }

        try (Stream<Path> stream = Files.list(logDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(this::toLogFileVO)
                    .sorted(Comparator.comparing(LogFileVO::getLastModified).reversed())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("扫描日志目录失败: {}", logDir, e);
            throw new BusinessException(ErrorCode.B0001);
        }
    }

    @Override
    public LogContentVO readContent(String fileName, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = DEFAULT_PAGE_SIZE;
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;

        Path filePath = resolveLogFile(fileName);

        if (!Files.isRegularFile(filePath)) {
            throw new BusinessException(ErrorCode.A0034);
        }

        try {
            long totalLines;
            try (Stream<String> lineStream = Files.lines(filePath)) {
                totalLines = lineStream.count();
            }

            int totalPages = (int) Math.ceil((double) totalLines / size);
            if (totalPages == 0) totalPages = 1;

            long skip = (long) (page - 1) * size;
            List<String> lines;
            try (Stream<String> lineStream = Files.lines(filePath)) {
                lines = lineStream.skip(skip).limit(size).collect(Collectors.toList());
            }

            return LogContentVO.builder()
                    .fileName(fileName)
                    .lines(lines)
                    .currentPage(page)
                    .totalPages(totalPages)
                    .totalLines(totalLines)
                    .pageSize(size)
                    .build();
        } catch (IOException e) {
            log.error("读取日志文件失败: {}", filePath, e);
            throw new BusinessException(ErrorCode.B0001);
        }
    }

    @Override
    public Path resolveLogFile(String fileName) {
        // 第一层：字符级黑名单（AD R-048）
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException(ErrorCode.A0034);
        }

        // 第二层：OS 级路径解析验证
        Path filePath = logDir.resolve(fileName).normalize();
        if (!filePath.startsWith(logDir)) {
            throw new BusinessException(ErrorCode.A0034);
        }

        return filePath;
    }

    private LogFileVO toLogFileVO(Path path) {
        try {
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            long size = attr.size();
            return LogFileVO.builder()
                    .fileName(path.getFileName().toString())
                    .fileSize(size)
                    .fileSizeFormatted(formatFileSize(size))
                    .lastModified(DATE_FMT.format(attr.lastModifiedTime().toInstant()))
                    .build();
        } catch (IOException e) {
            log.warn("读取文件属性失败: {}", path, e);
            return LogFileVO.builder()
                    .fileName(path.getFileName().toString())
                    .fileSize(0L)
                    .fileSizeFormatted("—")
                    .lastModified("—")
                    .build();
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}