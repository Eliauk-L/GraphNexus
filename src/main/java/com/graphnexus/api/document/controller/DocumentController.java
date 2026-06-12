package com.graphnexus.api.document.controller;

import com.graphnexus.api.document.dto.DocumentVO;
import com.graphnexus.api.document.dto.ParseResultVO;
import com.graphnexus.api.document.dto.UpdateDocumentRequest;
import com.graphnexus.application.document.service.DocumentBO;
import com.graphnexus.application.document.service.DocumentService;
import com.graphnexus.application.document.service.ParseResult;
import com.graphnexus.application.document.service.UpdateDocumentBO;
import com.graphnexus.common.ApiResponse;
import com.graphnexus.common.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档处理 REST API 控制器。
 *
 * <p>端点映射（见 DESIGN § 9.3）：</p>
 * <pre>
 *   POST   /api/v1/document/upload       上传 PDF
 *   POST   /api/v1/document/{id}/process  触发解析
 *   GET    /api/v1/document               分页查询
 *   GET    /api/v1/document/{id}          单条查询
 *   PUT    /api/v1/document/{id}          更新文档
 *   DELETE /api/v1/document/{id}          删除文档
 * </pre>
 *
 * @author Jay
 * @date 2026/06/12
 */
@RestController
@RequestMapping("/api/v1/document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 上传 PDF 文件。
     */
    @PostMapping("/upload")
    public ApiResponse<DocumentVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("subject") String subject
    ) {
        DocumentBO bo = documentService.upload(file, subject);
        return ApiResponse.success(DocumentVO.from(bo));
    }

    /**
     * 触发文档解析。
     */
    @PostMapping("/{id}/process")
    public ApiResponse<ParseResultVO> process(@PathVariable("id") Long id) {
        ParseResult result = documentService.process(id);
        return ApiResponse.success(ParseResultVO.from(id, result));
    }

    /**
     * 分页查询文档列表。
     */
    @GetMapping
    public ApiResponse<PageResult<DocumentVO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        Page<DocumentBO> page = documentService.listDocuments(pageNum, pageSize);
        return ApiResponse.success(PageResult.of(page.map(DocumentVO::from)));
    }

    /**
     * 查询单个文档。
     */
    @GetMapping("/{id}")
    public ApiResponse<DocumentVO> get(@PathVariable("id") Long id) {
        DocumentBO bo = documentService.getDocument(id);
        return ApiResponse.success(DocumentVO.from(bo));
    }

    /**
     * 更新文档名称。
     */
    @PutMapping("/{id}")
    public ApiResponse<DocumentVO> update(
            @PathVariable("id") Long id,
            @RequestBody @Valid UpdateDocumentRequest request
    ) {
        UpdateDocumentBO bo = new UpdateDocumentBO();
        bo.setName(request.getName());
        DocumentBO updated = documentService.updateDocument(id, bo);
        return ApiResponse.success(DocumentVO.from(updated));
    }

    /**
     * 删除文档（逻辑删除 + MinIO 物理清除）。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        documentService.deleteDocument(id);
        return ApiResponse.success(null);
    }
}