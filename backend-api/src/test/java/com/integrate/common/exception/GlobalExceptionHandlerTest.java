package com.integrate.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.common.web.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Verifica la tabla de traducción de ADR-0003 contra el contrato RFC 9457 (dtos_contrato_api.md §2). */
@WebMvcTest
@Import(GlobalExceptionHandlerTest.ProbeController.class)
@WithMockUser
class GlobalExceptionHandlerTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String TRACE = "trace-1234-abcd";

    @Autowired
    private MockMvc mvc;

    @Test
    void businessRuleExceptionUsesCatalogAndExtensions() throws Exception {
        mvc.perform(get("/probe/business").header(TraceIdFilter.HEADER, TRACE))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(header().string(TraceIdFilter.HEADER, TRACE))
                .andExpect(jsonPath("$.type").value("https://api.integrate.co/problems/insufficient-slots"))
                .andExpect(jsonPath("$.title").value("Insufficient Available Slots"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("BOOKING_INSUFFICIENT_SLOTS"))
                .andExpect(jsonPath("$.availableSlots").value(2))
                .andExpect(jsonPath("$.traceId").value(TRACE))
                .andExpect(jsonPath("$.instance").value("/probe/business"));
    }

    @Test
    void bodyValidationReturns422WithIndexedInvalidParams() throws Exception {
        String body = """
                {"name": "", "participants": [{"docNumber": "A1234"}, {"docNumber": ""}]}
                """;
        mvc.perform(post("/probe/validate").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.type").value("https://api.integrate.co/problems/validation-failed"))
                .andExpect(jsonPath("$.invalidParams[?(@.name == 'name')]").exists())
                .andExpect(jsonPath("$.invalidParams[?(@.name == 'participants[1].docNumber')]").exists())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void parameterValidationReturns422() throws Exception {
        mvc.perform(get("/probe/page").param("size", "0"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("size"));
    }

    @Test
    void malformedJsonReturns400AboutBlankWithoutEchoingInput() throws Exception {
        mvc.perform(post("/probe/validate").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": SECRET_FRAGMENT"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(content().string(not(containsString("SECRET_FRAGMENT"))));
    }

    @Test
    void typeMismatchReturns400() throws Exception {
        mvc.perform(get("/probe/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("id"))
                // El valor recibido no se refleja en detail ni en invalidParams (solo en instance, que es la ruta).
                .andExpect(jsonPath("$.detail").value("A parameter has an incompatible type."))
                .andExpect(jsonPath("$.invalidParams[0].reason").value("Incompatible type"));
    }

    @Test
    void lockTimeoutReturns503WithRetryAfter() throws Exception {
        mvc.perform(get("/probe/lock"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.code").value("CONCURRENCY_LOCK_TIMEOUT"));
    }

    @Test
    void accessDeniedReturns403Rbac() throws Exception {
        mvc.perform(get("/probe/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RBAC_FORBIDDEN"));
    }

    @Test
    void frameworkErrorsAlsoCarryTypeAndTraceId() throws Exception {
        mvc.perform(post("/probe/business").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void unknownRouteReturns404Problem() throws Exception {
        mvc.perform(get("/probe/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unexpectedExceptionReturns500WithoutInternalDetails() throws Exception {
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(content().string(not(containsString("db-password-leak"))));
    }

    record Participant(@NotBlank String docNumber) {
    }

    record ProbeRequest(@NotBlank String name, @NotEmpty List<@Valid Participant> participants) {
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @GetMapping("/business")
        void business() {
            throw new BusinessRuleException(ErrorCode.BOOKING_INSUFFICIENT_SLOTS).with("availableSlots", 2);
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody ProbeRequest request) {
        }

        @GetMapping("/page")
        void page(@RequestParam @Min(1) int size) {
        }

        @GetMapping("/items/{id}")
        void item(@PathVariable Long id) {
        }

        @GetMapping("/lock")
        void lock() {
            throw new CannotAcquireLockException("canceling statement due to lock timeout");
        }

        @GetMapping("/denied")
        void denied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("db-password-leak");
        }
    }
}
