package cz.insuit.moneta;

import org.jspecify.annotations.Nullable;

/** An answer came, but not in a shape this library can read: not JSON, or a required key missing. */
public final class MonetaInvalidResponseException extends MonetaApiException {
    public MonetaInvalidResponseException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
