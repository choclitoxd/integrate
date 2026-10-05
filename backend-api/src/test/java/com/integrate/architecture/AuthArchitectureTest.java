package com.integrate.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Reglas de capas de backend_guidelines.md §5 para el módulo {@code auth} y {@code security} (ADR-0005 §12). */
@AnalyzeClasses(packages = "com.integrate", importOptions = ImportOption.DoNotIncludeTests.class)
class AuthArchitectureTest {

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that().resideInAPackage("com.integrate.modules..controller..")
            .should().dependOnClassesThat().resideInAnyPackage("com.integrate.modules..repository..");

    @ArchTest
    static final ArchRule securityDoesNotReachIntoRepositories = noClasses()
            .that().resideInAPackage("com.integrate.security..")
            .should().dependOnClassesThat().resideInAnyPackage("com.integrate.modules..repository..",
                    "com.integrate.modules..entity..", "com.integrate.modules..service.impl..");

    @ArchTest
    static final ArchRule servicesDoNotDependOnWebLayer = noClasses()
            .that().resideInAPackage("com.integrate.modules..service..")
            .should().dependOnClassesThat().resideInAnyPackage("com.integrate.modules..controller..",
                    "jakarta.servlet..");
}
