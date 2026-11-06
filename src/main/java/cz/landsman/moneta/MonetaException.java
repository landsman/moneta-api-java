package cz.landsman.moneta;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * A call to MONETA that did not return what was asked for, one subtype per thing a caller does about it, so
 * a {@code switch} over them is exhaustive. The message never carries the token, nor MONETA's response body.
 *
 * <p>The subtypes' constructors are public so a caller's own tests can throw them from a fake; the hierarchy
 * stays closed.
 */
public abstract sealed class MonetaException extends RuntimeException {

    MonetaException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    /** The token was refused: revoked, expired, or without the rights the call needs. Retrying will not help. */
    public static final class InvalidToken extends MonetaException {
        private final int status;
        private final @Nullable String body;

        public InvalidToken(String message, int status, @Nullable String body) {
            super(message, null);
            this.status = status;
            this.body = body;
        }

        /** 401 or 403. */
        public int status() {
            return status;
        }

        /** The start of MONETA's answer, which may say why; kept out of the message so logging it is safe. */
        public @Nullable String body() {
            return body;
        }
    }

    /** Too many calls. Retrying later will help. */
    public static final class RateLimited extends MonetaException {
        private final @Nullable Duration retryAfter;

        public RateLimited(String message, @Nullable Duration retryAfter) {
            super(message, null);
            this.retryAfter = retryAfter;
        }

        /** How long MONETA asked to wait, when it said so in seconds. */
        public @Nullable Duration retryAfter() {
            return retryAfter;
        }
    }

    /** Any other status that is not a success. */
    public static final class HttpError extends MonetaException {
        private final int status;
        private final @Nullable String body;
        private final @Nullable Duration retryAfter;

        public HttpError(String message, int status, @Nullable String body, @Nullable Duration retryAfter) {
            super(message, null);
            this.status = status;
            this.body = body;
            this.retryAfter = retryAfter;
        }

        /** The HTTP status MONETA answered with. */
        public int status() {
            return status;
        }

        /** The first 2,000 characters of MONETA's answer, which usually say what was wrong with the request. */
        public @Nullable String body() {
            return body;
        }

        /** Set when MONETA said when to come back, as it may on a 503 during maintenance. */
        public @Nullable Duration retryAfter() {
            return retryAfter;
        }
    }

    /**
     * No answer came: a timeout or a refused connection. An interrupted thread is not one: that is a
     * {@link java.util.concurrent.CancellationException}, so a retry does not repeat work someone cancelled.
     */
    public static final class NoAnswer extends MonetaException {
        public NoAnswer(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** An answer came, but not in a shape this library can read: not JSON, or a required key missing. */
    public static final class InvalidResponse extends MonetaException {
        public InvalidResponse(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }
}
