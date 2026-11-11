package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.time.OffsetDateTime;

/** {@code GET /api/v1/vip/token/introspect}: {@code {"expiration": "2026-03-06T11:38:09+01:00"}}. */
record TokenIntrospectionJson(OffsetDateTime expiration) {
    TokenIntrospectionJson {
        requireNonNull(expiration, "expiration");
    }
}
