package com.mossyanwar.ordersinvoicing.common.csv;

import com.mossyanwar.ordersinvoicing.common.money.Money;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void writesRowsWithCrlfLineEndings() {
        StringWriter out = new StringWriter();
        CsvWriter csv = new CsvWriter(out);

        csv.row(Arrays.asList("invoice_number", "issue_date", "gross_total"));
        csv.row(Arrays.asList("INV-2026-000001", LocalDate.of(2026, 3, 2), Money.of("120")));

        assertThat(out).hasToString("invoice_number,issue_date,gross_total\r\nINV-2026-000001,2026-03-02,120.00\r\n");
    }

    @Test
    void quotesCellsThatContainSeparatorsQuotesOrNewlines() {
        assertThat(CsvWriter.cell("Smith, Jones & Co")).isEqualTo("\"Smith, Jones & Co\"");
        assertThat(CsvWriter.cell("The \"Best\" Ltd")).isEqualTo("\"The \"\"Best\"\" Ltd\"");
        assertThat(CsvWriter.cell("line one\nline two")).isEqualTo("\"line one\nline two\"");
    }

    @Test
    void neutralisesTextThatASpreadsheetWouldRunAsAFormula() {
        assertThat(CsvWriter.cell("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvWriter.cell("+44 113 000")).isEqualTo("'+44 113 000");
        assertThat(CsvWriter.cell("-1+1")).isEqualTo("'-1+1");
        assertThat(CsvWriter.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(CsvWriter.cell("=HYPERLINK(\"http://x\",\"y\")"))
                .isEqualTo("\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\"");
        assertThat(CsvWriter.cell("Acme Ltd")).isEqualTo("Acme Ltd");
    }

    @Test
    void leavesNumbersDatesAndNullsAlone() {
        assertThat(CsvWriter.cell(Money.of("-1.50"))).isEqualTo("-1.50");
        assertThat(CsvWriter.cell(42)).isEqualTo("42");
        assertThat(CsvWriter.cell(null)).isEmpty();
    }
}
