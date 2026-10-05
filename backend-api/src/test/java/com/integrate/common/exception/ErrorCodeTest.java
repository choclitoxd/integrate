package com.integrate.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void codesMatchContractPattern() {
        assertThat(ErrorCode.values())
                .allSatisfy(code -> assertThat(code.name()).matches("^[A-Z][A-Z0-9_]+$"));
    }

    @Test
    void everyCodeIsAnErrorStatusWithTitle() {
        assertThat(ErrorCode.values()).allSatisfy(code -> {
            assertThat(code.status().value()).isBetween(400, 599);
            assertThat(code.title()).isNotBlank();
        });
    }

    @Test
    void domainTypesAreUnique() {
        long domainCodes = Arrays.stream(ErrorCode.values())
                .filter(c -> !c.type().toString().equals("about:blank"))
                .count();
        long distinctTypes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::type)
                .filter(t -> !t.toString().equals("about:blank"))
                .distinct()
                .count();
        assertThat(distinctTypes).isEqualTo(domainCodes);
    }
}
