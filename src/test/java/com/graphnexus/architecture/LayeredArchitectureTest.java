package com.graphnexus.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * 分层架构约束测试。
 *
 * <p>校验四层架构 L1(api) → L2(application) → L3(infrastructure) 的依赖方向。
 * common 被所有层依赖，自身不依赖任何业务层。</p>
 *
 * <p>规则来源：docs/项目规范.md §1.4.2、§2.3</p>
 *
 * @author GraphNexus
 * @date 2026/06/11
 */
@AnalyzeClasses(
        packages = "com.graphnexus",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class LayeredArchitectureTest {

    /**
     * 四层依赖方向校验。
     *
     * <pre>
     * L1 (api)       → depends on L2, common
     * L2 (application) → depends on L3, common
     * L3 (infrastructure) → depends on common
     * common         → depends on nothing (only JDK + 3rd-party libs)
     * </pre>
     */
    @ArchTest
    static final ArchRule layeredArchitecture = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("L1-api").definedBy("com.graphnexus.api..")
            .layer("L2-application").definedBy("com.graphnexus.application..")
            .layer("L3-infrastructure").definedBy("com.graphnexus.infrastructure..")
            .layer("Common").definedBy("com.graphnexus.common..")

            .whereLayer("L1-api").mayNotBeAccessedByAnyLayer()
            .whereLayer("L1-api").mayOnlyAccessLayers("L2-application", "Common")

            .whereLayer("L2-application").mayOnlyBeAccessedByLayers("L1-api")
            .whereLayer("L2-application").mayOnlyAccessLayers("L3-infrastructure", "Common")

            .whereLayer("L3-infrastructure").mayOnlyBeAccessedByLayers("L2-application")
            .whereLayer("L3-infrastructure").mayOnlyAccessLayers("Common")

            .whereLayer("Common").mayNotAccessAnyLayer();
}