package cz.insuit.moneta;

import java.net.http.HttpClient;

/**
 * Wraps the HTTP client a framework integration's {@code MonetaClient} sends through, for tracing or metrics. It
 * must delegate to the client it is given, not return another. As a bean, several apply in their order, the lowest
 * innermost. The client still builds and closes its own HTTP client, so the wrapper does not have to pass {@code
 * close()} on. See {@link MonetaClient.Builder#decorateHttpClient}. Ignored when the application defines its own
 * {@code MonetaClient} bean.
 */
@FunctionalInterface
public interface HttpClientDecorator {

    HttpClient decorate(HttpClient client);
}
