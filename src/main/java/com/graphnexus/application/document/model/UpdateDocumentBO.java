package com.graphnexus.application.document.model;

import lombok.Data;

/**
 * 文档更新请求（仅允许更名，v1 最小化）。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Data
public class UpdateDocumentBO {

    /** 新文档名称 */
    private String name;
}