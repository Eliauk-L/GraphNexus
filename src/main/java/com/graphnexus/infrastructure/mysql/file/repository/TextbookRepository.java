package com.graphnexus.infrastructure.mysql.file.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import org.springframework.data.repository.query.Param;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;

import java.util.Optional;

/**
 * 教材文档元数据 Repository。
 *
 * <p>使用 Spring Data JPA 方法名派生查询替代显式 JPQL。
 * isDeleted 字段为 Integer 类型（0=未删除），方法名中使用属性名 isDeleted 而非 IsDeletedFalse。</p>
 *
 * @author Jay
 * @date 2026/06/12
 */
@Repository
public interface TextbookRepository extends JpaRepository<TextbookDO, Long> {

    /**
     * 查询未删除且非删除中状态的文档（分页）。
     *
     * @param isDeleted 逻辑删除标记，传 0 表示未删除
     * @param status    排除的状态，传 {@link FileStatus#DELETING}
     * @param pageable  分页参数
     * @return 分页文档列表
     */
    Page<TextbookDO> findByIsDeletedAndStatusNot(Integer isDeleted, FileStatus status, Pageable pageable);

    /**
     * 按 ID 查询未删除且非删除中状态的文档。
     *
     * @param id        文档 ID
     * @param isDeleted 逻辑删除标记，传 0
     * @param status    排除的状态，传 {@link FileStatus#DELETING}
     * @return 文档（若存在）
     */
    Optional<TextbookDO> findByIdAndIsDeletedAndStatusNot(Long id, Integer isDeleted, FileStatus status);

    /**
     * 按内容指纹 + 学科查找未删除文档的 ID（用于去重检查）。
     *
     * @param documentNo 内容指纹（MD5）
     * @param subject    学科
     * @param isDeleted  逻辑删除标记，传 0
     * @param status     排除的状态，传 {@link FileStatus#DELETING}
     * @return 文档 ID（若存在）
     */
    Optional<Long> findIdByDocumentNoAndSubjectAndIsDeletedAndStatusNot(
            String documentNo, String subject, Integer isDeleted, FileStatus status);

    /**
     * 按内容指纹查找第一条未删除的文档（用于复用已有 MinIO 文件路径），按创建时间升序。
     *
     * @param documentNo 内容指纹（MD5）
     * @param isDeleted  逻辑删除标记，传 0
     * @param status     排除的状态，传 {@link FileStatus#DELETING}
     * @return 第一条匹配文档（若存在）
     */
    Optional<TextbookDO> findFirstByDocumentNoAndIsDeletedAndStatusNotOrderByCreateTimeAsc(
            String documentNo, Integer isDeleted, FileStatus status);

    /**
     * 统计引用同一文件路径的未删除且非删除中状态的记录数（用于删除时判断是否可清理 MinIO 文件）。
     *
     * @param filePath  MinIO 文件路径
     * @param isDeleted 逻辑删除标记，传 0
     * @param status    排除的状态，传 {@link FileStatus#DELETING}
     * @return 引用计数
     */
    long countByFilePathAndIsDeletedAndStatusNot(String filePath, Integer isDeleted, FileStatus status);

    /**
     * 条件分页查询：支持按文件类型和文件名筛选。
     *
     * <p>保留 {@link Query} 注解：涉及 NULL 可选参数 + LIKE 模糊搜索，
     * 方法名派生无法表达"参数为 NULL 时忽略条件"的语义。</p>
     */
    @Query("SELECT d FROM TextbookDO d WHERE d.isDeleted = 0 AND d.status <> 'DELETING' "
         + "AND (:fileType IS NULL OR d.fileType = :fileType) "
         + "AND (:name IS NULL OR d.name LIKE %:name%) "
         + "ORDER BY d.createTime DESC")
    Page<TextbookDO> findByConditions(@Param("fileType") String fileType,
                                  @Param("name") String name,
                                  Pageable pageable);
}