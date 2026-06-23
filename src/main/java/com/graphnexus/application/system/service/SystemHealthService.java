package com.graphnexus.application.system.service;

import com.graphnexus.api.system.dto.SystemHealthVO;

/**
 * 系统健康业务接口。
 *
 * @author Jay
 * @date 2026/06/23
 */
public interface SystemHealthService {

    /**
     * 获取系统健康全貌（组件状态 + JVM 指标）。
     *
     * @return 聚合后的系统健康 VO
     */
    SystemHealthVO getSystemHealth();
}