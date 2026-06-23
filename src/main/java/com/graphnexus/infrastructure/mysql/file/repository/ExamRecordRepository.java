package com.graphnexus.infrastructure.mysql.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;

import java.util.List;

/**
 * 考试成绩记录 Repository。
 *
 * <p>复杂查询（LIKE + DISTINCT + GROUP BY + Object[] 投影）保留 {@link Query} 注解。
 * 条件查询通过 {@link JpaSpecificationExecutor} 动态组合。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Repository
public interface ExamRecordRepository extends JpaRepository<ExamRecordDO, Long>,
        JpaSpecificationExecutor<ExamRecordDO> {

    /** 按考试编号查询成绩记录。 */
    List<ExamRecordDO> findByExamNo(String examNo);

    /** 判断指定考试编号是否存在记录（用于上传判重）。 */
    boolean existsByExamNo(String examNo);

    /** 按学号查询成绩记录。 */
    List<ExamRecordDO> findByStudentNo(String studentNo);

    /** 按学号 + 学科查询成绩记录（智能问答 MASTERS 降级用）。 */
    List<ExamRecordDO> findByStudentNoAndSubject(String studentNo, String subject);

    /** 查询所有不重复的学科（排除空学科，按学科名排序）。 */
    @Query("SELECT DISTINCT e.subject FROM ExamRecordDO e WHERE e.subject IS NOT NULL ORDER BY e.subject")
    List<String> findDistinctSubjectBySubjectIsNotNullOrderBySubject();

    /**
     * 按姓名模糊匹配学生（返回不重复的 studentNo/name/className）。
     */
    @Query("SELECT DISTINCT e.studentNo AS studentNo, e.name AS name, e.className AS className " +
           "FROM ExamRecordDO e WHERE e.name LIKE %:name%")
    List<Object[]> findStudentByName(String name);

    /**
     * 按学号精确查找学生。
     */
    @Query("SELECT DISTINCT e.studentNo AS studentNo, e.name AS name, e.className AS className " +
           "FROM ExamRecordDO e WHERE e.studentNo = :studentNo")
    List<Object[]> findStudentByNo(String studentNo);

    /**
     * 按班级名查询该班级所有不重复的学生。
     */
    @Query("SELECT DISTINCT e.studentNo AS studentNo, e.name AS name, e.className AS className " +
           "FROM ExamRecordDO e WHERE e.className = :className")
    List<Object[]> findDistinctStudentsByClassName(String className);

    /**
     * 按学号列表批量查询学生考试记录（班级概览降级路径用）。
     */
    List<ExamRecordDO> findByStudentNoIn(List<String> studentNos);

    /**
     * 分页查询不重复的考试元数据（按 examNo 分组）。
     */
    @Query("SELECT DISTINCT e.examNo AS examNo, e.examName AS examName, "
         + "e.examDate AS examDate, e.subject AS subject, "
         + "COUNT(e) AS studentCount "
         + "FROM ExamRecordDO e "
         + "GROUP BY e.examNo, e.examName, e.examDate, e.subject "
         + "ORDER BY e.examDate DESC")
    Page<Object[]> findDistinctExams(Pageable pageable);
}
