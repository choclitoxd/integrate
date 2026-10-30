package com.integrate.modules.audit.service.impl;

import java.util.List;

/**
 * CSV de la exportación de la bitácora (ADR-0035 §1, RFC 4180). Una celda que empieza con {@code = + - @} se prefija
 * con {@code '} para que una hoja de cálculo no la ejecute como fórmula (inyección de CSV). Función pura.
 */
public final class CsvWriter {

    private static final String FORMULA_STARTS = "=+-@";

    private CsvWriter() {
    }

    public static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        boolean quote = safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0;
        return quote ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }

    /** Una fila terminada en CRLF. */
    public static String row(List<String> cells) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(cell(cells.get(i)));
        }
        return row.append("\r\n").toString();
    }
}