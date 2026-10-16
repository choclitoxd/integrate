package com.integrate.quality.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.quality.support.QualityIntegrationTest;
import com.integrate.quality.support.QualityMetrics;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * RNF-07 (Seguridad · RBAC): recorre <em>todas</em> las rutas que expone la API, no una lista escrita a mano.
 * Una ruta nueva sin regla en {@code SecurityConfig} queda cubierta (deny-by-default) y si alguien la abre por
 * error, esta prueba lo detecta.
 */
@Tag("rnf-07")
@Tag("iso-seguridad")
@DisplayName("[RNF-07] Control de acceso RBAC en todos los endpoints")
class EndpointSecuritySweepQualityTest extends QualityIntegrationTest {

    private static final AntPathMatcher PATHS = new AntPathMatcher();

    /** Espejo de las reglas {@code permitAll} de {@code SecurityConfig}: lo único que puede responder sin token. */
    private static final List<Rule> PUBLIC = List.of(
            new Rule(HttpMethod.POST, "/api/v1/auth/login"), new Rule(HttpMethod.POST, "/api/v1/auth/register"),
            new Rule(HttpMethod.POST, "/api/v1/auth/refresh"), new Rule(HttpMethod.POST, "/api/v1/auth/logout"),
            new Rule(HttpMethod.POST, "/api/v1/auth/activate"),
            new Rule(HttpMethod.POST, "/api/v1/auth/forgot-password"),
            new Rule(HttpMethod.POST, "/api/v1/auth/reset-password"),
            new Rule(HttpMethod.GET, "/api/v1/experiences"), new Rule(HttpMethod.GET, "/api/v1/experiences/*"),
            new Rule(HttpMethod.GET, "/api/v1/experiences/*/availability"),
            new Rule(HttpMethod.GET, "/api/v1/catalog/**"),
            new Rule(HttpMethod.GET, "/api/v1/privacy-policies/current"),
            new Rule(HttpMethod.POST, "/api/v1/payments/webhook/mock"),
            new Rule(HttpMethod.POST, "/api/v1/payments/webhook/wompi"));

    /** Rutas reservadas a un rol distinto de TOURIST: un turista debe recibir 403. */
    private static final List<Rule> NOT_FOR_TOURISTS = List.of(
            new Rule(null, "/api/v1/admin/**"), new Rule(null, "/api/v1/host/**"),
            new Rule(null, "/api/v1/gestor/**"), new Rule(HttpMethod.POST, "/api/v1/auth/register-assisted"));

    /** Rutas solo para TOURIST: un usuario sin ese rol debe recibir 403. */
    private static final List<Rule> TOURIST_ONLY = List.of(
            new Rule(null, "/api/v1/bookings/**"), new Rule(null, "/api/v1/dev/**"));

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("[RNF-07] 100 % de los endpoints protegidos responden 401 sin token o con token alterado")
    void everyProtectedEndpointRejectsAnonymousAndForgedCalls() throws Exception {
        List<Endpoint> protectedEndpoints = endpoints().stream().filter(e -> !e.matchesAny(PUBLIC)).toList();
        String original = tokenFor(registerTourist());
        String forged = escalateToAdmin(original);
        assertThat(forged).as("el payload alterado debe diferir del original").isNotEqualTo(original);

        List<String> violations = new ArrayList<>();
        for (Endpoint endpoint : protectedEndpoints) {
            int anonymous = call(endpoint, null);
            int tampered = call(endpoint, forged);
            if (anonymous != 401 || tampered != 401) {
                violations.add(endpoint + " → sin token " + anonymous + ", token alterado " + tampered);
            }
        }

        long compliant = protectedEndpoints.size() - violations.size();
        double rate = QualityMetrics.percent(compliant, protectedEndpoints.size());
        QualityMetrics.record("RNF-07", "Endpoints protegidos que rechazan acceso sin token valido", rate, "%",
                "= 100 % (401)", violations.isEmpty(), protectedEndpoints.size() + " endpoints protegidos de "
                        + endpoints().size() + " rutas; token alterado = claims elevados a ROLE_ADMIN sin re-firmar");
        assertThat(protectedEndpoints).hasSizeGreaterThan(20);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("[RNF-07] 100 % de los endpoints de otro rol responden 403 al rol equivocado")
    void endpointsOfAnotherRoleAnswerForbidden() throws Exception {
        String touristToken = tokenFor(registerTourist());
        String hostOnly = userWithRole("solo-anfitrion", RoleCode.HOST);
        jdbc.update("""
                UPDATE auth.user_roles SET revoked_at = now()
                 WHERE role_id = (SELECT id FROM auth.roles WHERE code = 'TOURIST')
                   AND user_id = (SELECT id FROM auth.users WHERE email = ?)""", hostOnly);
        String hostToken = tokenFor(hostOnly);

        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (Endpoint endpoint : endpoints()) {
            String token = endpoint.matchesAny(NOT_FOR_TOURISTS) ? touristToken
                    : endpoint.matchesAny(TOURIST_ONLY) ? hostToken : null;
            if (token == null) {
                continue;
            }
            checked++;
            int status = call(endpoint, token);
            if (status != 403) {
                violations.add(endpoint + " → " + status);
            }
        }

        QualityMetrics.record("RNF-07", "Endpoints de rol que rechazan al rol equivocado",
                QualityMetrics.percent(checked - violations.size(), checked), "%", "= 100 % (403)",
                violations.isEmpty(), checked + " endpoints de ADMIN/HOST/LINK_MANAGER/TOURIST");
        assertThat(checked).isGreaterThan(10);
        assertThat(violations).isEmpty();
    }

    // ---------------------------------------------------------------- utilidades

    private List<Endpoint> endpoints() {
        TreeSet<Endpoint> all = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, ?> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            var methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                for (RequestMethod method : methods.isEmpty() ? List.of(RequestMethod.GET) : methods) {
                    all.add(new Endpoint(HttpMethod.valueOf(method.name()), pattern));
                }
            }
        }
        return List.copyOf(all);
    }

    private int call(Endpoint endpoint, String token) throws Exception {
        String path = endpoint.pattern().replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        var builder = request(endpoint.method(), path).contentType("application/json").content("{}");
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return mvc.perform(builder).andReturn().getResponse().getStatus();
    }

    /** Reescribe el payload del JWT con el rol de administrador y conserva la firma original. */
    private static String escalateToAdmin(String token) {
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"ROLE_TOURIST\"", "\"ROLE_ADMIN\",\"ROLE_TOURIST\"");
        return parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
    }

    private record Rule(HttpMethod method, String pattern) {
    }

    private record Endpoint(HttpMethod method, String pattern) implements Comparable<Endpoint> {

        boolean matchesAny(List<Rule> rules) {
            String concrete = pattern.replaceAll("\\{[^}]+}", "x");
            return rules.stream().anyMatch(r -> (r.method() == null || r.method().equals(method))
                    && PATHS.match(r.pattern(), concrete));
        }

        @Override
        public int compareTo(Endpoint other) {
            return toString().compareTo(other.toString());
        }

        @Override
        public String toString() {
            return method + " " + pattern;
        }
    }
}
