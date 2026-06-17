package com.graphnexus.infrastructure.mysql.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 考试成绩记录 Repository。
 *
 * <p>延用显式 JPQL 模式（参考 {@link DocumentRepository}），
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
}