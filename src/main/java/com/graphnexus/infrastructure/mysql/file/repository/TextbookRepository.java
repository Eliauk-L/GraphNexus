package com.graphnexus.infrastructure.mysql.file.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import org.springframework.data.repository.query.Param;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;

import java.util.List;
import java.util.Optional;

/**
 * 教材文档元数据 Repository。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Repository
public interface TextbookRepository extends JpaRepository<TextbookDO, Long> {

    /** 查询非删除中状态的文档（分页）。 */
    Page<TextbookDO> findByStatusNot(FileStatus status, Pageable pageable);

    /** 按 ID 查询非删除中状态的文档。 */
    Optional<TextbookDO> findByIdAndStatusNot(Long id, FileStatus status);

    /** 按内容指纹 + 学科查找非删除中状态的文档 ID（用于去重检查）。 */
    Optional<Long> findIdByDocumentNoAndSubjectAndStatusNot(
            String documentNo, String subject, FileStatus status);

    /** 按内容指纹查找第一条非删除中状态的文档（用于复用 MinIO 路径），按创建时间升序。 */
    Optional<TextbookDO> findFirstByDocumentNoAndStatusNotOrderByCreateTimeAsc(
            String documentNo, FileStatus status);

    /** 统计引用同一文件路径的非删除中状态记录数（用于删除时判断是否可清理 MinIO 文件）。 */
    long countByFilePathAndStatusNot(String filePath, FileStatus status);

    /**
     * 条件分页查询：支持按文件类型和文件名筛选。
     */
    @Query("SELECT d FROM TextbookDO d WHERE d.status <> 'DELETING' "
         + "AND (:fileType IS NULL OR d.fileType = :fileType) "
         + "AND (:name IS NULL OR d.name LIKE %:name%) "
         + "ORDER BY d.createTime DESC")
    Page<TextbookDO> findByConditions(@Param("fileType") String fileType,
                                  @Param("name") String name,
                                  Pageable pageable);

    /** 按处理状态分组统计文档数（排除 DELETING 状态）。 */
    @Query("SELECT d.status, COUNT(d) FROM TextbookDO d WHERE d.status <> 'DELETING' GROUP BY d.status")
    List<Object[]> countGroupByStatus();

    /** 按学科分组统计文档数（排除 DELETING 状态）。 */
    @Query("SELECT d.subject, COUNT(d) FROM TextbookDO d WHERE d.status <> 'DELETING' GROUP BY d.subject ORDER BY COUNT(d) DESC")
    List<Object[]> countGroupBySubject();
}