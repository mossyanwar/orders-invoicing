package com.mossyanwar.ordersinvoicing.invoice.numbering;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out gapless, per-year document numbers.
 *
 * <p>The counter row is incremented with {@code UPDATE ... RETURNING}. That takes a row lock which
 * is held until the surrounding transaction ends, so two requests issuing at the same moment get
 * consecutive numbers rather than the same one, and if the transaction that created the document
 * rolls back the increment rolls back with it. That is why this must run inside the caller's
 * transaction ({@link Propagation#MANDATORY}): in a transaction of its own the number would be
 * committed even if the invoice was not, leaving a gap.
 *
 * <p>The cost is that issuing is serialised per series and year. Callers should allocate the
 * number as late as possible, after validation, to keep the lock short.
 */
@Component
public class DocumentNumberAllocator {

    private final JdbcTemplate jdbc;

    public DocumentNumberAllocator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentSeries series, int year) {
        // first document of the year creates the counter; if another transaction is creating it
        // at the same time, this waits for that one and then does nothing
        jdbc.update("""
                insert into document_sequence (series, doc_year, last_value) values (?, ?, 0)
                on conflict (series, doc_year) do nothing
                """, series.name(), year);
        Long value = jdbc.queryForObject("""
                update document_sequence set last_value = last_value + 1
                where series = ? and doc_year = ?
                returning last_value
                """, Long.class, series.name(), year);
        return series.format(year, value);
    }
}
