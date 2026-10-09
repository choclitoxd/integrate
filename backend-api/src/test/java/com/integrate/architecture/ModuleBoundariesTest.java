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

    /** El inventario de cupos es de {@code catalog}: {@code booking} solo usa {@code SlotInventory} (ADR-0008 §2). */
    @ArchTest
    static final ArchRule bookingUsesOnlyThePublicCatalogAndAuthApis = noClasses()
            .that().resideInAPackage("com.integrate.modules.booking..")
            .should().dependOnClassesThat().resideInAnyPackage("com.integrate.modules.catalog.repository..",
                    "com.integrate.modules.catalog.entity..", "com.integrate.modules.catalog.service.impl..",
                    "com.integrate.modules.catalog.controller..", "com.integrate.modules.auth.repository..",
                    "com.integrate.modules.auth.service.impl..", "com.integrate.modules.auth.controller..");

    @ArchTest
    static final ArchRule catalogAndAuthDoNotDependOnBooking = noClasses()
            .that().resideInAnyPackage("com.integrate.modules.catalog..", "com.integrate.modules.auth..")
            .should().dependOnClassesThat().resideInAnyPackage("com.integrate.modules.booking..");
}
