package cz.landsman.moneta;

import org.jspecify.annotations.Nullable;

/** A call to MONETA that did not return what was asked for. The message never carries the token. */
public sealed class MonetaException extends RuntimeException
        permits MonetaException.InvalidToken, MonetaException.RateLimited {

    private final int status;

    MonetaException(String message, int status, @Nullable Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** The HTTP status, or 0 when no answer came. */
    public int status() {
        return status;
    }

    /** The token was refused: revoked, expired, or without the rights the call needs. Retrying will not help. */
    public static final class InvalidToken extends MonetaException {
        InvalidToken(String message, int status) {
            super(message, status, null);
        }
    }

    /** Too many calls. Retrying later will. */
    public static final class RateLimited extends MonetaException {
        RateLimited(String message) {
            super(message, 429, null);
        }
    }
}
