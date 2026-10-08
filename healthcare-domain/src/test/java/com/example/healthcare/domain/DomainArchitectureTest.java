package com.example.healthcare.domain;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces that the domain model depends only on the JDK and its own packages.
 *
 * <p>Runs under Surefire as a unit test. Module-wide rules live in
 * {@code ModuleDependencyIT} in the integration test module.
 */
@AnalyzeClasses(
        packages = "com.example.healthcare.domain",
        importOptions = ImportOption.DoNotIncludeTests.class)
class DomainArchitectureTest {

    @ArchTest
    static final ArchRule domain_depends_only_on_the_jdk_and_itself =
            classes()
                    .that().resideInAPackage("com.example.healthcare.domain..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("com.example.healthcare.domain..", "java..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_has_no_spring_imports =
            noClasses()
                    .that().resideInAPackage("com.example.healthcare.domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.springframework..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_has_no_jakarta_imports =
            noClasses()
                    .that().resideInAPackage("com.example.healthcare.domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("jakarta..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_has_no_hapi_imports =
            noClasses()
                    .that().resideInAPackage("com.example.healthcare.domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("ca.uhn..")
                    .allowEmptyShould(true);
}
