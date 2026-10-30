package com.integrate.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.integrate.common.audit.SensitiveAccessRecorder;
import com.integrate.common.crypto.SensitiveDataCipher;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Barrera estructural de ADR-0035 §2: descifrar un dato sensible deja rastro. Toda clase fuera de {@code common.crypto}
 * que llame a {@code SensitiveDataCipher.decrypt} debe depender también de {@link SensitiveAccessRecorder}; así el rastro
 * deja de ser una convención que alguien debe recordar.
 */
@AnalyzeClasses(packages = "com.integrate", importOptions = ImportOption.DoNotIncludeTests.class)
class SensitiveAccessArchitectureTest {

    private static final DescribedPredicate<JavaClass> CALLS_DECRYPT =
            new DescribedPredicate<>("call SensitiveDataCipher.decrypt") {
                @Override
                public boolean test(JavaClass type) {
                    return type.getMethodCallsFromSelf().stream().anyMatch(call ->
                            call.getTargetOwner().isAssignableTo(SensitiveDataCipher.class)
                                    && call.getName().equals("decrypt"));
                }
            };

    @ArchTest
    static final ArchRule decryptingRequiresRecordingTheAccess = classes()
            .that().resideOutsideOfPackage("com.integrate.common.crypto..")
            .and(CALLS_DECRYPT)
            .should().dependOnClassesThat().areAssignableTo(SensitiveAccessRecorder.class)
            .allowEmptyShould(true);
}