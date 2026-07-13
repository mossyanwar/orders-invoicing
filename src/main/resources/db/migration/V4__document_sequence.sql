-- Gapless numbering for invoices and credit notes: one row per series per year holding the last
-- number handed out. It is incremented with UPDATE ... RETURNING inside the same transaction that
-- creates the document, so the row lock makes concurrent issuers queue and a rollback gives the
-- number back. A PostgreSQL sequence would be faster but is not transactional and leaves gaps.
create table document_sequence (
    series     varchar(20) not null,
    doc_year   integer     not null,
    last_value bigint      not null check (last_value >= 0),
    primary key (series, doc_year)
);
