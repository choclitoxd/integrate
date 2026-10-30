package com.integrate.modules.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrate.modules.audit.service.impl.CsvWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CSV de la exportación de la bitácora (ADR-0035 §1): escape correcto y sin inyección de fórmulas. */
class CsvWriterTest {

    @Test
    void plainCellsAreWrittenAsIs() {
        assertThat(CsvWriter.cell("PAYMENT_APPROVED")).isEqualTo("PAYMENT_APPROVED");
        assertThat(CsvWriter.cell(null)).isEmpty();
    }

    @Test
    void commasQuotesAndLineBreaksAreQuotedAndEscaped() {
        assertThat(CsvWriter.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvWriter.cell("dijo \"hola\"")).isEqualTo("\"dijo \"\"hola\"\"\"");
        assertThat(CsvWriter.cell("línea1\nlínea2")).isEqualTo("\"línea1\nlínea2\"");
    }

    @Test
    void cellsThatStartLikeAFormulaAreNeutralised() {
        assertThat(CsvWriter.cell("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvWriter.cell("+57300")).isEqualTo("'+57300");
        assertThat(CsvWriter.cell("-2")).isEqualTo("'-2");
        assertThat(CsvWriter.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        // Neutralizada y además con comas: se prefija y se entrecomilla.
        assertThat(CsvWriter.cell("=A1,B1")).isEqualTo("\"'=A1,B1\"");
    }

    @Test
    void aRowIsJoinedWithCommasAndTerminatedWithCrLf() {
        assertThat(CsvWriter.row(List.of("1", "a,b", ""))).isEqualTo("1,\"a,b\",\r\n");
    }
}