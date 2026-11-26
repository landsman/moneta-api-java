package cz.insuit.moneta;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/** Too many calls. Retrying later will help. */
public final class MonetaRateLimitedException extends MonetaApiException {
    private final @Nullable Duration retryAfter;

    public MonetaRateLimitedException(String message, @Nullable Duration retryAfter) {
        super(message, null);
        this.retryAfter = retryAfter;
    }

    /** How long MONETA asked to wait, when it said so in seconds. */
    public @Nullable Duration retryAfter() {
        return retryAfter;
    }
}
