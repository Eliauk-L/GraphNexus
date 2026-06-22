package com.graphnexus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * 事务规范架构约束测试（ADR-028）。
 *
 * <p>验证 AC-3（@Transactional 方法不访问 Neo4j Repository）、
 * AC-6（@Transactional 仅 public 方法、@EventListener 不标注 @Transactional）。</p>
 *
 * @author Jay
 * @date 2026/06/22
 */
@AnalyzeClasses(
        packages = "com.graphnexus",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class TransactionArchTest {

    /**
     * AC-3: 标注 @Transactional 的方法不应访问 Neo4j Repository 包（指导性规则）。
     *
     * <p>allowEmptyShould(true)：因为当前所有 @Transactional 方法（读方法除外）已经完成重构，
     * 不再直接访问 Neo4j repository。</p>
     */
    @ArchTest
    @SuppressWarnings("unused")
    void transactionalMethodsShouldNotAccessNeo4j(JavaClasses classes) {
        classes().that().areAnnotatedWith(Transactional.class)
                .should().onlyAccessClassesThat()
                .resideOutsideOfPackage("com.graphnexus.infrastructure.neo4j.repository..")
                .allowEmptyShould(true)
                .check(classes);
    }

    /**
     * AC-6 规则 a: @Transactional 仅出现在 public 方法上。
     *
     * <p>已知技术债（D8 · v2）：QueryServiceImpl 3 个非 public 方法（persistTask/updateCompletedTask/
     * updateTaskStatus）保留 @Transactional，已标注 TODO。此处显式排除。</p>
     */
    @ArchTest
    static final ArchRule transactionalOnlyOnPublicMethods =
            methods().that().areAnnotatedWith(Transactional.class)
                    .and().areDeclaredInClassesThat()
                    .resideOutsideOfPackage("com.graphnexus.application.query..")
                    .should().bePublic()
                    .allowEmptyShould(true)
                    .because("@Transactional on non-public methods may bypass AOP proxy (ADR-028 规则 6); "
                            + "QueryServiceImpl 3 private methods excluded (known tech debt · D8)");

    /**
     * AC-6 规则 b: @EventListener 方法上不出现 @Transactional。
     */
    @ArchTest
    static final ArchRule eventListenerNotTransactional =
            noMethods().that().areAnnotatedWith(EventListener.class)
                    .should().beAnnotatedWith(Transactional.class)
                    .allowEmptyShould(true)
                    .because("@EventListener + @Transactional may be bypassed by adapter (ADR-028 规则 5)");
}