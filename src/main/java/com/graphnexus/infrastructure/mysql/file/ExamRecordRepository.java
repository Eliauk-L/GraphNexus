package com.graphnexus.infrastructure.mysql.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 考试成绩记录 Repository。
 *
 * <p>延用显式 JPQL 模式（参考 {@link FileRepository}），
 * 规避 Hibernate 6.5 Boolean/TINYINT 谓词 bug。
 * 见 DESIGN §0.5.2。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Repository
public interface ExamRecordRepository extends JpaRepository<ExamRecordDO, Long> {

    /**
     * 按考试编号查询未删除的成绩记录。
     */
    @Query("SELECT e FROM ExamRecordDO e WHERE e.examNo = :examNo AND e.isDeleted = 0")
    List<ExamRecordDO> findByExamNoAndIsDeletedFalse(String examNo);

    /**
     * 按 CSV MD5 查找未删除的记录（用于上传判重，一个 CSV 对应多条学生记录）。
     */
    @Query("SELECT e FROM ExamRecordDO e WHERE e.csvMd5 = :csvMd5 AND e.isDeleted = 0")
    List<ExamRecordDO> findByCsvMd5AndIsDeletedFalse(String csvMd5);

    /**
     * 按考试编号查询所有记录（含 isDeleted=1，删除流程内部使用）。
     */
    @Query("SELECT e FROM ExamRecordDO e WHERE e.examNo = :examNo")
    List<ExamRecordDO> findByExamNo(String examNo);

    List<ExamRecordDO> findExamRecordDOByStudentNoAndIsDeleted(String studentNo, Integer isDeleted);

    /**
     * 按学号 + 学科查询未删除的成绩记录（智能问答 MASTERS 降级用）。
     *
     * @param studentNo 学号
     * @param subject   学科
     * @return 该学生指定学科下的所有成绩记录
     */
    @Query("SELECT e FROM ExamRecordDO e WHERE e.studentNo = :studentNo AND e.subject = :subject AND e.isDeleted = 0")
    List<ExamRecordDO> findByStudentNoAndSubject(String studentNo, String subject);

    /**
     * 查询所有不重复的学科（从 exam_record.subject 聚合）。
     */
    @Query("SELECT DISTINCT e.subject FROM ExamRecordDO e WHERE e.isDeleted = 0 AND e.subject IS NOT NULL ORDER BY e.subject")
    List<String> findDistinctSubjects();

    /**
     * 按姓名模糊匹配学生（返回不重复的 studentNo/name/className）。
     */
    @Query("SELECT DISTINCT e.studentNo AS studentNo, e.name AS name, e.className AS className " +
           "FROM ExamRecordDO e WHERE e.name LIKE %:name% AND e.isDeleted = 0")
    List<Object[]> findStudentByName(String name);

    /**
     * 按学号精确查找学生。
     */
    @Query("SELECT DISTINCT e.studentNo AS studentNo, e.name AS name, e.className AS className " +
           "FROM ExamRecordDO e WHERE e.studentNo = :studentNo AND e.isDeleted = 0")
    List<Object[]> findStudentByNo(String studentNo);
}