package com.integrate.quality.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Registro de la evidencia cuantitativa de la suite de calidad (ADR-0019 §7): cada verificación deja el valor
 * medido junto al umbral del Plan de Gestión de la Calidad en {@code target/quality-metrics/*.json}, que
 * {@code qa/scripts/build-evidence-report.mjs} convierte en la sección 5 del plan.
 *
 * <p>Se registra <em>antes</em> de afirmar, para que una no conformidad también deje su valor medido.
 */
public final class QualityMetrics {

    private static final Path DIR = Path.of("target", "quality-metrics");

    private QualityMetrics() {
    }

    /**
     * @param rnf       identificador del RNF en el plan (campo "ID" de su tabla), p. ej. {@code RNF-16}
     * @param metric    nombre de la métrica tal como la nombra el plan
     * @param value     valor medido
     * @param unit      unidad del valor ({@code %}, {@code ms}, {@code s}, {@code puntos}...)
     * @param threshold criterio de aceptación del plan, legible ({@code "= 0,0 %"}, {@code "p95 <= 200 ms"})
     * @param pass      si el valor cumple el umbral
     * @param detail    contexto de la medición (tamaño de muestra, condiciones)
     */
    public static synchronized void record(String rnf, String metric, double value, String unit, String threshold,
                                           boolean pass, String detail) {
        String slug = (rnf + "-" + metric).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        String json = """
                {"rnf":"%s","metric":"%s","value":%s,"unit":"%s","threshold":"%s","pass":%s,"detail":"%s",\
                "test":"%s","measuredAt":"%s"}
                """.formatted(esc(rnf), esc(metric), format(value), esc(unit), esc(threshold), pass, esc(detail),
                esc(callerClass()), Instant.now());
        try {
            Files.createDirectories(DIR);
            Files.writeString(DIR.resolve(slug + ".json"), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Percentil por rango más cercano (nearest-rank), el mismo que reporta k6 para {@code p(95)}. */
    public static double percentile(List<Long> samplesNanos, double percentile) {
        List<Long> sorted = new ArrayList<>(samplesNanos);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.size());
        return sorted.get(Math.max(0, rank - 1)) / 1_000_000.0;
    }

    public static double percent(long part, long total) {
        return total == 0 ? 100.0 : 100.0 * part / total;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String callerClass() {
        return StackWalker.getInstance().walk(frames -> frames
                .map(StackWalker.StackFrame::getClassName)
                .filter(name -> !name.startsWith(QualityMetrics.class.getPackageName()))
                .findFirst().orElse("?"));
    }

    private static String esc(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
