package com.graphnexus.infrastructure.mysql.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文档元数据 Repository。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Repository
public interface DocumentRepository extends JpaRepository<DocumentDO, Long> {

    /**
     * 查询未删除的文档（分页）。
     */
    Page<DocumentDO> findByIsDeletedFalse(Pageable pageable);

    /**
     * 按 ID 查询未删除的文档。
     */
    Optional<DocumentDO> findByIdAndIsDeletedFalse(Long id);

    /**
     * 按内容指纹 + 学科检查是否已存在（去重）。
     */
    boolean existsByDocumentNoAndSubjectAndIsDeletedFalse(String documentNo, String subject);
}