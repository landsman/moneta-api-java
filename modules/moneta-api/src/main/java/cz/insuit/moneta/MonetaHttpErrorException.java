package cz.insuit.moneta;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/** Any other status that is not a success. */
public final class MonetaHttpErrorException extends MonetaApiException {
    private final int status;
    private final @Nullable String body;
    private final @Nullable Duration retryAfter;

    public MonetaHttpErrorException(String message, int status, @Nullable String body, @Nullable Duration retryAfter) {
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
