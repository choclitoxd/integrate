package com.integrate.quality.maintainability;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrate.quality.support.QualityIntegrationTest;
import com.integrate.quality.support.QualityMetrics;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * RNF-28 (Mantenibilidad · capacidad para ser modificado): 100 % de los endpoints expuestos documentados en
 * {@code docs/api/openapi.yaml}. Se compara la API real (todas las rutas registradas en Spring MVC) contra el
 * contrato, ruta y método. La parte de linters del RNF la cubre el Quality Gate de la PWA ({@code npm run lint}).
 */
@Tag("rnf-28")
@Tag("iso-mantenibilidad")
@DisplayName("[RNF-28] Endpoints documentados bajo OpenAPI")
class OpenApiCoverageQualityTest extends QualityIntegrationTest {

    private static final Path CONTRACT = Path.of("..", "docs", "api", "openapi.yaml");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("[RNF-28] 100 % de los endpoints implementados están en openapi.yaml")
    void everyImplementedEndpointIsDocumented() throws Exception {
        TreeSet<String> documented = documented();
        TreeSet<String> implemented = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (pattern.startsWith("/api/") && !pattern.startsWith("/api/v1/dev/")) {
                    info.getMethodsCondition().getMethods()
                            .forEach(m -> implemented.add(m.name() + " " + normalize(pattern)));
                }
            }
        }

        List<String> undocumented = implemented.stream().filter(e -> !covered(e, documented)).toList();
        List<String> notImplemented = documented.stream().filter(d -> !d.contains("/api/v1/dev/"))
                .filter(d -> !covered(d, implemented)).toList();

        QualityMetrics.record("RNF-28", "Endpoints implementados documentados en OpenAPI",
                QualityMetrics.percent(implemented.size() - undocumented.size(), implemented.size()), "%", "= 100 %",
                undocumented.isEmpty(), implemented.size() + " operaciones implementadas; sin documentar: "
                        + undocumented + "; documentadas pendientes de implementar: " + notImplemented);
        assertThat(implemented).hasSizeGreaterThan(20);
        assertThat(undocumented).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static TreeSet<String> documented() throws Exception {
        TreeSet<String> operations = new TreeSet<>();
        try (Reader reader = Files.newBufferedReader(CONTRACT, StandardCharsets.UTF_8)) {
            Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
            Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) root.get("paths");
            paths.forEach((path, item) -> item.keySet().stream()
                    .filter(k -> List.of("get", "post", "put", "patch", "delete").contains(k))
                    .forEach(m -> operations.add(m.toUpperCase(Locale.ROOT) + " " + normalize(path))));
        }
        return operations;
    }

    /** Variables de ruta como comodín: {@code /x/{id}/approve} cubre {@code /x/{id}/{action}} y viceversa. */
    private static boolean covered(String operation, TreeSet<String> catalog) {
        String[] target = operation.split("[ /]");
        return catalog.stream().map(c -> c.split("[ /]")).anyMatch(candidate -> {
            if (candidate.length != target.length) {
                return false;
            }
            for (int i = 0; i < target.length; i++) {
                if (!candidate[i].equals(target[i]) && !candidate[i].equals("{}") && !target[i].equals("{}")) {
                    return false;
                }
            }
            return true;
        });
    }

    private static String normalize(String path) {
        return path.replaceAll("\\{[^}]+}", "{}");
    }

}
