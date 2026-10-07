package com.integrate.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Fronteras entre módulos (backend_guidelines.md §5.1, ADR-0006 §7): un módulo usa la interfaz
 * pública de otro ({@code service}) y nunca sus repositorios ni sus implementaciones.
 */
@AnalyzeClasses(packages = "com.integrate", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

    @ArchTest
    static final ArchRule catalogUsesOnlyThePublicAuthApi = noClasses()
            .that().resideInAPackage("com.integrate.modules.catalog..")
            .should().dependOnClassesThat().resideInAnyPackage("com.integrate.modules.auth.repository..",
                    "com.integrate.modules.auth.service.impl..", "com.integrate.modules.auth.controller..");

    @ArchTest
    static final ArchRule authDoesNotDependOnCatalog = noClasses()
            .that().resideInAPackage("com.integrate.modules.auth..")
            .should().dependOnClassesThat().resideInAnyPackage("com.integrate.modules.catalog..");
}
