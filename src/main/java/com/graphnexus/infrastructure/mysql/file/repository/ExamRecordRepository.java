package com.graphnexus.infrastructure.mysql.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;

import java.util.List;

/**
 * 考试成绩记录 Repository。
 *
 * <p>使用 Spring Data JPA 方法名派生查询替代简单 JPQL，
 * 复杂查询（LIKE + DISTINCT + GROUP BY + Object[] 投影）保留 {@link Query} 注解。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Repository
public interface ExamRecordRepository extends JpaRepository<ExamRecordDO, Long> {

    /**
     * 按考试编号查询未删除的成绩记录。
     *
     * @param examNo    考试编号
     * @param isDeleted 逻辑删除标记，传 0 表示未删除
     * @return 成绩记录列表
     */
    List<ExamRecordDO> findByExamNoAndIsDeleted(String examNo, Integer isDeleted);

    /**
     * 按 CSV MD5 查找未删除的记录（用于上传判重，一个 CSV 对应多条学生记录）。
     *
     * @param csvMd5    CSV 文件 MD5
     * @param isDeleted 逻辑删除标记，传 0
     * @return 匹配的记录列表
     */
    List<ExamRecordDO> findByCsvMd5AndIsDeleted(String csvMd5, Integer isDeleted);

    /**
     * 按考试编号查询所有记录（含已删除，删除流程内部使用）。
     *
     * @param examNo 考试编号
     * @return 所有匹配记录（含 isDeleted=1）
     */
    List<ExamRecordDO> findByExamNo(String examNo);

    /**
     * 按学号和删除标记查询成绩记录。
     *
     * @param studentNo 学号
     * @param isDeleted 逻辑删除标记（0=未删除，1=已删除）
     * @return 匹配的记录列表
     */
    List<ExamRecordDO> findByStudentNoAndIsDeleted(String studentNo, Integer isDeleted);

    /**
     * 按学号 + 学科查询未删除的成绩记录（智能问答 MASTERS 降级用）。
     *
     * @param studentNo 学号
     * @param subject   学科
     * @param isDeleted 逻辑删除标记，传 0
     * @return 该学生指定学科下的所有成绩记录
     */
    List<ExamRecordDO> findByStudentNoAndSubjectAndIsDeleted(String studentNo, String subject, Integer isDeleted);

    /**
     * 查询所有不重复的学科（排除已删除记录和空学科，按学科名排序）。
     *
     * @param isDeleted 逻辑删除标记，传 0
     * @return 不重复的学科名称列表
     */
    List<String> findDistinctSubjectByIsDeletedAndSubjectIsNotNullOrderBySubject(Integer isDeleted);

    /**
     * 按姓名模糊匹配学生（返回不重复的 studentNo/name/className）。
     *
     * <p>保留 {@link Query}：涉及 LIKE + DISTINCT + Object[] 投影。</p>
     */
    @Query("SELECT DISTINCT e.studentNo AS studentNo, e.name AS name, e.className AS className " +
           "FROM ExamRecordDO e WHERE e.name LIKE %:name% AND e.isDeleted = 0")
    List<Object[]> findStudentByName(String name);

    /**
     * 按学号精确查找学生。
     *
     * <p>保留 {@link Query}：涉及 DISTINCT + Object[] 投影。</p>
     */
    @Query("SELECT DISTINCT e.studentNo AS studentNo, e.name AS name, e.className AS className " +
           "FROM ExamRecordDO e WHERE e.studentNo = :studentNo AND e.isDeleted = 0")
    List<Object[]> findStudentByNo(String studentNo);

    /**
     * 分页查询不重复的考试元数据（按 examNo 分组）。
     *
     * <p>保留 {@link Query}：涉及 DISTINCT + GROUP BY + COUNT + Object[] 投影。</p>
     */
    @Query("SELECT DISTINCT e.examNo AS examNo, e.examName AS examName, "
         + "e.examDate AS examDate, e.subject AS subject, e.csvFilePath AS csvFilePath, "
         + "e.csvMd5 AS csvMd5, COUNT(e) AS studentCount "
         + "FROM ExamRecordDO e WHERE e.isDeleted = 0 "
         + "GROUP BY e.examNo, e.examName, e.examDate, e.subject, e.csvFilePath, e.csvMd5 "
         + "ORDER BY e.examDate DESC")
    Page<Object[]> findDistinctExams(Pageable pageable);
}