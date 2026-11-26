package cz.insuit.moneta;

/**
 * No answer came: a timeout or a refused connection. An interrupted thread is not one: that is a
 * {@link java.util.concurrent.CancellationException}, so a retry does not repeat work someone cancelled.
 */
public final class MonetaNoAnswerException extends MonetaApiException {
    public MonetaNoAnswerException(String message, Throwable cause) {
        super(message, cause);
    }
}
