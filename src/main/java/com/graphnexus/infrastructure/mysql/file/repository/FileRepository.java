package com.graphnexus.infrastructure.mysql.file.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.graphnexus.infrastructure.mysql.file.entity.FileDO;

import java.util.Optional;

/**
 * 文档元数据 Repository。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Repository
public interface FileRepository extends JpaRepository<FileDO, Long> {

    /**
     * 查询未删除的文档（分页）。
     */
    @Query("SELECT d FROM FileDO d WHERE d.isDeleted = 0 AND d.status <> 'DELETING'")
    Page<FileDO> findByIsDeletedFalse(Pageable pageable);

    /**
     * 按 ID 查询未删除的文档。
     */
    @Query("SELECT d FROM FileDO d WHERE d.id = :id AND d.isDeleted = 0 AND d.status <> 'DELETING'")
    Optional<FileDO> findByIdAndIsDeletedFalse(Long id);

    /**
     * 按内容指纹 + 学科查找未删除的文档 ID（用于去重检查）。
     */
    @Query("SELECT d.id FROM FileDO d WHERE d.documentNo = :documentNo AND d.subject = :subject AND d.isDeleted = 0 AND d.status <> 'DELETING'")
    Optional<Long> findIdByDocumentNoAndSubjectAndIsDeletedFalse(String documentNo, String subject);
}