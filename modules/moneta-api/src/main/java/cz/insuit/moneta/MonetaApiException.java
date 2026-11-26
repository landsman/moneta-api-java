package cz.insuit.moneta;

import org.jspecify.annotations.Nullable;

/**
 * A call to MONETA that did not return what was asked for, one subtype per thing a caller does about it, so
 * a {@code switch} over them is exhaustive. The message names the request and, when MONETA sent one, its
 * {@code X-Request-Id}, which its support asks for; never the token, nor MONETA's response body.
 *
 * <p>The subtypes' constructors are public so a caller's own tests can throw them from a fake; the hierarchy
 * stays closed.
 */
public abstract sealed class MonetaApiException extends RuntimeException
        permits MonetaInvalidTokenException,
                MonetaRateLimitedException,
                MonetaHttpErrorException,
                MonetaNoAnswerException,
                MonetaInvalidResponseException {

    MonetaApiException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
