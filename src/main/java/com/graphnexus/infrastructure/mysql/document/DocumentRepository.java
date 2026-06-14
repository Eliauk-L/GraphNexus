package com.graphnexus.infrastructure.mysql.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
    @Query("SELECT d FROM DocumentDO d WHERE d.isDeleted = 0")
    Page<DocumentDO> findByIsDeletedFalse(Pageable pageable);

    /**
     * 按 ID 查询未删除的文档。
     */
    @Query("SELECT d FROM DocumentDO d WHERE d.id = :id AND d.isDeleted = 0")
    Optional<DocumentDO> findByIdAndIsDeletedFalse(Long id);

    /**
     * 按内容指纹 + 学科查找未删除的文档 ID（用于去重检查）。
     */
    @Query("SELECT d.id FROM DocumentDO d WHERE d.documentNo = :documentNo AND d.subject = :subject AND d.isDeleted = 0")
    Optional<Long> findIdByDocumentNoAndSubjectAndIsDeletedFalse(String documentNo, String subject);
}