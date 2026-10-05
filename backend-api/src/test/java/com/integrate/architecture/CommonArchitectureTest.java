package com.integrate.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Reglas de ADR-0003 y backend_guidelines.md §5 para el paquete transversal {@code common}. */
@AnalyzeClasses(packages = "com.integrate", importOptions = ImportOption.DoNotIncludeTests.class)
class CommonArchitectureTest {

    @ArchTest
    static final ArchRule commonDoesNotDependOnModulesOrSecurity = noClasses()
            .that().resideInAPackage("com.integrate.common..")
            .should().dependOnClassesThat().resideInAnyPackage("com.integrate.modules..", "com.integrate.security..");

    @ArchTest
    static final ArchRule onlyCommonExceptionTranslatesErrors = noClasses()
            .that().resideOutsideOfPackage("com.integrate.common.exception")
            .should().beAnnotatedWith(RestControllerAdvice.class)
            .orShould().beAnnotatedWith(ControllerAdvice.class)
            .orShould().beAssignableTo(ResponseEntityExceptionHandler.class);
}
