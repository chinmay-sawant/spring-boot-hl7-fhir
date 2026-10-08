package com.example.healthcare.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * Architecture rules for the five Maven modules.
 *
 * <p>Runs under Failsafe during {@code verify} because the class name ends in
 * {@code IT}. The application module may depend on the domain, HL7, and FHIR
 * modules; the rules below forbid every other direction.
 */
@AnalyzeClasses(
        packages = "com.example.healthcare",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleDependencyIT {

    @ArchTest
    static final ArchRule domain_depends_on_no_other_module =
            noClasses()
                    .that().resideInAPackage("com.example.healthcare.domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.example.healthcare.hl7..",
                            "com.example.healthcare.fhir..",
                            "com.example.healthcare.application..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule hl7_does_not_depend_on_fhir_or_application =
            noClasses()
                    .that().resideInAPackage("com.example.healthcare.hl7..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.example.healthcare.fhir..",
                            "com.example.healthcare.application..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule fhir_does_not_depend_on_hl7_or_application =
            noClasses()
                    .that().resideInAPackage("com.example.healthcare.fhir..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.example.healthcare.hl7..",
                            "com.example.healthcare.application..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule modules_are_free_of_cycles =
            SlicesRuleDefinition.slices()
                    .matching("com.example.healthcare.(*)..")
                    .should().beFreeOfCycles()
                    .allowEmptyShould(true);
}
