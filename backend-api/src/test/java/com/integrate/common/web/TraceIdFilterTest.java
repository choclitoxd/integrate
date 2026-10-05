package com.integrate.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void generatesTraceIdWhenAbsentAndPublishesItEverywhere() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> mdcDuringChain.set(MDC.get(TraceIdFilter.MDC_KEY)));

        String header = response.getHeader(TraceIdFilter.HEADER);
        assertThat(header).matches(TraceIdFilter.VALID_TRACE_ID);
        assertThat(mdcDuringChain.get()).isEqualTo(header);
        assertThat(TraceIdFilter.currentTraceId(request)).isEqualTo(header);
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).as("MDC se limpia al terminar").isNull();
    }

    @Test
    void reusesValidInboundTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "c4b9-8f21-4d1a");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo("c4b9-8f21-4d1a");
    }

    @Test
    void discardsMaliciousInboundTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "abc\r\nFAKE LOG LINE");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(TraceIdFilter.HEADER))
                .doesNotContain("FAKE")
                .matches(TraceIdFilter.VALID_TRACE_ID);
    }
}
