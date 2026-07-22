package com.mossyanwar.ordersinvoicing.common.csv;

import com.mossyanwar.ordersinvoicing.common.money.Money;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Minimal RFC 4180 CSV writer.
 *
 * <p>Text cells that start with {@code = + - @}, tab or carriage return are prefixed with a single
 * quote, so a customer called {@code =HYPERLINK(...)} is shown as text when the export is opened in
 * a spreadsheet instead of being run as a formula (CSV injection). Numbers and dates are written
 * as they are.
 */
public class CsvWriter {

    private static final Set<Character> FORMULA_STARTS = Set.of('=', '+', '-', '@', '\t', '\r');

    private final Writer out;

    public CsvWriter(Writer out) {
        this.out = out;
    }

    public void row(List<?> cells) {
        String line = cells.stream().map(CsvWriter::cell).collect(Collectors.joining(","));
        try {
            out.write(line);
            out.write("\r\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Money money) {
            return money.amount().toPlainString();
        }
        if (value instanceof BigDecimal || value instanceof Number || value instanceof TemporalAccessor || value instanceof Enum<?>) {
            return value.toString();
        }
        return quoteIfNeeded(neutraliseFormula(value.toString()));
    }

    private static String neutraliseFormula(String text) {
        return !text.isEmpty() && FORMULA_STARTS.contains(text.charAt(0)) ? "'" + text : text;
    }

    private static String quoteIfNeeded(String text) {
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
