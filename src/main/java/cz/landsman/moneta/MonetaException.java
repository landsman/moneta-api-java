package cz.landsman.moneta;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * A call to MONETA that did not return what was asked for, one subtype per thing a caller does about it, so
 * a {@code switch} over them is exhaustive. The message never carries the token.
 */
public abstract sealed class MonetaException extends RuntimeException {

    MonetaException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    /** The token was refused: revoked, expired, or without the rights the call needs. Retrying will not help. */
    public static final class InvalidToken extends MonetaException {
        private final int status;

        InvalidToken(String message, int status) {
            super(message, null);
            this.status = status;
        }

        /** 401 or 403. */
        public int status() {
            return status;
        }
    }

    /** Too many calls. Retrying later will help. */
    public static final class RateLimited extends MonetaException {
        private final @Nullable Duration retryAfter;

        RateLimited(String message, @Nullable Duration retryAfter) {
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

        HttpError(String message, int status) {
            super(message, null);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /** No answer came: a timeout, a refused connection, an interrupted thread. */
    public static final class NoAnswer extends MonetaException {
        NoAnswer(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** An answer came, but not in a shape this library can read: not JSON, or a required key missing. */
    public static final class InvalidResponse extends MonetaException {
        InvalidResponse(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }
}
