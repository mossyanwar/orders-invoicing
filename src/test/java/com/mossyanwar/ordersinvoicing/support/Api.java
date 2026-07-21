package com.mossyanwar.ordersinvoicing.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Small wrapper over {@link TestRestTemplate} so tests read as API calls. Every POST carries a fresh
 * Idempotency-Key unless the test passes its own headers.
 */
public class Api {

    private final TestRestTemplate rest;

    public Api(TestRestTemplate rest) {
        this.rest = rest;
    }

    public ResponseEntity<JsonNode> get(String path) {
        return exchange(HttpMethod.GET, path, null, new HttpHeaders());
    }

    public ResponseEntity<JsonNode> post(String path, Object body) {
        return exchange(HttpMethod.POST, path, body, withKey(UUID.randomUUID().toString()));
    }

    public ResponseEntity<JsonNode> post(String path, Object body, String idempotencyKey) {
        return exchange(HttpMethod.POST, path, body, withKey(idempotencyKey));
    }

    public ResponseEntity<JsonNode> post(String path, Object body, HttpHeaders headers) {
        return exchange(HttpMethod.POST, path, body, headers);
    }

    public ResponseEntity<JsonNode> put(String path, Object body) {
        return exchange(HttpMethod.PUT, path, body, new HttpHeaders());
    }

    public ResponseEntity<JsonNode> delete(String path) {
        return exchange(HttpMethod.DELETE, path, null, new HttpHeaders());
    }

    public <T> ResponseEntity<T> getRaw(String path, Class<T> type) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), type);
    }

    private static HttpHeaders withKey(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        return headers;
    }

    private ResponseEntity<JsonNode> exchange(HttpMethod method, String path, Object body, HttpHeaders headers) {
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
