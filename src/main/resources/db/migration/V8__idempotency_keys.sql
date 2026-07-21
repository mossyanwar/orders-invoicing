-- Remembers the outcome of POST requests that carried an Idempotency-Key header, so a client that
-- retries after a timeout gets the original response instead of creating a second order or payment.
create table idempotency_key (
    scope             varchar(200) not null,
    idempotency_key   varchar(100) not null,
    request_hash      char(64)     not null,
    status            varchar(20)  not null check (status in ('IN_PROGRESS', 'COMPLETED')),
    response_status   integer,
    response_body     text,
    response_location varchar(500),
    created_at        timestamptz  not null,
    primary key (scope, idempotency_key)
);

create index ix_idempotency_key_created_at on idempotency_key (created_at);
