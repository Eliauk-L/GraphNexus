package com.graphnexus.infrastructure.mysql.file.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 教材文档元数据 Repository。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Repository
public interface TextbookRepository extends JpaRepository<TextbookDO, Long> {

    /**
     * 查询未删除的文档（分页）。
     */
    @Query("SELECT d FROM TextbookDO d WHERE d.isDeleted = 0 AND d.status <> 'DELETING'")
    Page<TextbookDO> findByIsDeletedFalse(Pageable pageable);

    /**
     * 按 ID 查询未删除的文档。
     */
    @Query("SELECT d FROM TextbookDO d WHERE d.id = :id AND d.isDeleted = 0 AND d.status <> 'DELETING'")
    Optional<TextbookDO> findByIdAndIsDeletedFalse(Long id);

    /**
     * 按内容指纹 + 学科查找未删除的文档 ID（用于去重检查）。
     */
    @Query("SELECT d.id FROM TextbookDO d WHERE d.documentNo = :documentNo AND d.subject = :subject AND d.isDeleted = 0 AND d.status <> 'DELETING'")
    Optional<Long> findIdByDocumentNoAndSubjectAndIsDeletedFalse(String documentNo, String subject);

    /**
     * 按内容指纹查找第一条未删除的文档（用于复用已有 MinIO 文件路径）。
     */
    @Query("SELECT d FROM TextbookDO d WHERE d.documentNo = :documentNo AND d.isDeleted = 0 AND d.status <> 'DELETING' ORDER BY d.createTime ASC LIMIT 1")
    Optional<TextbookDO> findFirstByDocumentNoAndNotDeleted(String documentNo);

    /**
     * 统计引用同一文件路径的未删除记录数（用于删除时判断是否可清理 MinIO 文件）。
     */
    @Query("SELECT COUNT(d) FROM TextbookDO d WHERE d.filePath = :filePath AND d.isDeleted = 0 AND d.status <> 'DELETING'")
    long countByFilePathAndNotDeleted(String filePath);

    /**
     * 条件分页查询：支持按文件类型和文件名筛选。
     */
    @Query("SELECT d FROM TextbookDO d WHERE d.isDeleted = 0 AND d.status <> 'DELETING' "
         + "AND (:fileType IS NULL OR d.fileType = :fileType) "
         + "AND (:name IS NULL OR d.name LIKE %:name%) "
         + "ORDER BY d.createTime DESC")
    Page<TextbookDO> findByConditions(@Param("fileType") String fileType,
                                  @Param("name") String name,
                                  Pageable pageable);
}