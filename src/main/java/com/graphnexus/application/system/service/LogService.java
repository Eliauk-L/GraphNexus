package com.graphnexus.application.system.service;

import com.graphnexus.api.system.dto.LogContentVO;
import com.graphnexus.api.system.dto.LogFileVO;

import java.nio.file.Path;
import java.util.List;

/**
 * 日志文件管理业务接口。
 *
 * @author Jay
 * @date 2026/06/23
 */
public interface LogService {

    /**
     * 列出日志目录下所有文件，按修改时间倒序。
     *
     * @return 日志文件元数据列表
     */
    List<LogFileVO> listFiles();

    /**
     * 分页读取日志文件内容。
     *
     * @param fileName 文件名（不含路径）
     * @param page     页码，从 1 开始
     * @param size     每页行数，默认 200，最大 500
     * @return 分页日志内容
     */
    LogContentVO readContent(String fileName, int page, int size);

    /**
     * 校验文件名安全性并返回日志文件的完整路径。
     *
     * @param fileName 文件名（不含路径）
     * @return 安全校验后的文件 Path
     */
    Path resolveLogFile(String fileName);
}