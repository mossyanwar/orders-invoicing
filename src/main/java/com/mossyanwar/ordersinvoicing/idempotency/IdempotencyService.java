package com.mossyanwar.ordersinvoicing.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mossyanwar.ordersinvoicing.security.CurrentUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Makes a create endpoint safe to retry, following the IETF Idempotency-Key header draft.
 *
 * <ol>
 *   <li>The key is claimed by inserting a row in its own, immediately committed transaction. The
 *       primary key means only one request can claim a given key.</li>
 *   <li>The action runs, and the response is stored on that row <em>in the same transaction as the
 *       business change</em>, so there is never an order without a stored response or the other
 *       way round.</li>
 *   <li>If the action fails, the claim is deleted so the client can try again with the same key.</li>
 * </ol>
 *
 * A request that finds the key already claimed gets the stored response replayed if the body is
 * the same (with an {@code Idempotent-Replayed: true} header), 422 if the body differs, or 409 if
 * the first request has not finished yet.
 *
 * <p>Keys are scoped to the operation and the authenticated user, so two clients that happen to
 * pick the same key cannot see each other's responses.
 */
@Service
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    public static final String HEADER_DESCRIPTION =
            "Unique per logical request, for example a UUID. A retry with the same key and body replays the first response.";

    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_\\-:.]{1,100}");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate inCurrentOrNewTransaction;
    private final TransactionTemplate inSeparateTransaction;
    private final Clock clock;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.inCurrentOrNewTransaction = new TransactionTemplate(transactionManager);
        this.inSeparateTransaction = new TransactionTemplate(transactionManager);
        this.inSeparateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public <T> ResponseEntity<T> execute(String operation, String key, Object request, Class<T> responseType,
                                         Supplier<ResponseEntity<T>> action) {
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw IdempotencyProblems.invalidKey();
        }
        String scope = operation + ":" + CurrentUser.name();
        String requestHash = hash(request);

        if (!claim(scope, key, requestHash)) {
            return replay(scope, key, requestHash, responseType);
        }
        try {
            return inCurrentOrNewTransaction.execute(status -> {
                ResponseEntity<T> response = action.get();
                complete(scope, key, response);
                return response;
            });
        } catch (RuntimeException e) {
            release(scope, key);
            throw e;
        }
    }

    /** Deletes stored keys older than the cut-off. Returns how many were removed. */
    public int deleteOlderThan(OffsetDateTime cutOff) {
        return jdbc.update("delete from idempotency_key where created_at < ?", cutOff);
    }

    private boolean claim(String scope, String key, String requestHash) {
        Boolean claimed = inSeparateTransaction.execute(status -> jdbc.update("""
                insert into idempotency_key (scope, idempotency_key, request_hash, status, created_at)
                values (?, ?, ?, 'IN_PROGRESS', ?)
                on conflict (scope, idempotency_key) do nothing
                """, scope, key, requestHash, OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC)) == 1);
        return Boolean.TRUE.equals(claimed);
    }

    private void complete(String scope, String key, ResponseEntity<?> response) {
        URI location = response.getHeaders().getLocation();
        jdbc.update("""
                update idempotency_key
                set status = 'COMPLETED', response_status = ?, response_body = ?, response_location = ?
                where scope = ? and idempotency_key = ?
                """, response.getStatusCode().value(), toJson(response.getBody()),
                location == null ? null : location.toString(), scope, key);
    }

    private void release(String scope, String key) {
        inSeparateTransaction.executeWithoutResult(status -> jdbc.update(
                "delete from idempotency_key where scope = ? and idempotency_key = ? and status = 'IN_PROGRESS'",
                scope, key));
    }

    private <T> ResponseEntity<T> replay(String scope, String key, String requestHash, Class<T> responseType) {
        Optional<StoredKey> stored = jdbc.query("""
                        select request_hash, status, response_status, response_body, response_location
                        from idempotency_key where scope = ? and idempotency_key = ?
                        """,
                (rs, row) -> new StoredKey(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5)),
                scope, key).stream().findFirst();
        if (stored.isEmpty()) {
            // the first request failed and released the key between our insert and this read
            throw IdempotencyProblems.stillInProgress();
        }
        StoredKey record = stored.get();
        if (!record.requestHash().equals(requestHash)) {
            throw IdempotencyProblems.reusedWithDifferentRequest();
        }
        if (!"COMPLETED".equals(record.status())) {
            throw IdempotencyProblems.stillInProgress();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(REPLAYED_HEADER, "true");
        if (record.location() != null) {
            headers.setLocation(URI.create(record.location()));
        }
        return ResponseEntity.status(record.responseStatus()).headers(headers).body(fromJson(record.body(), responseType));
    }

    private String hash(Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(request));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("could not hash request", e);
        }
    }

    private String toJson(Object body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not store response", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json.getBytes(StandardCharsets.UTF_8), type);
        } catch (IOException e) {
            throw new IllegalStateException("could not read stored response", e);
        }
    }

    private record StoredKey(String requestHash, String status, int responseStatus, String body, String location) {
    }
}
