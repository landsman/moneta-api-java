package cz.insuit.moneta;

import org.jspecify.annotations.Nullable;

/** The token was refused: revoked, expired, or without the rights the call needs. Retrying will not help. */
public final class MonetaInvalidTokenException extends MonetaApiException {
    private final int status;
    private final @Nullable String body;

    public MonetaInvalidTokenException(String message, int status, @Nullable String body) {
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
