package cz.insuit.moneta.spring;

import java.net.http.HttpClient;

/**
 * A bean of this type wraps the HTTP client the auto-configured {@code MonetaClient} sends through, for tracing or
 * metrics. It must delegate to the client it is given, not return another. Several apply in their {@code @Order},
 * the lowest innermost. The client still builds and closes its own HTTP client, so the wrapper does not have to pass
 * {@code close()} on. See {@code MonetaClient.Builder.decorateHttpClient}. Ignored when the application defines its
 * own {@code MonetaClient} bean.
 */
@FunctionalInterface
public interface MonetaHttpClientDecorator {

    HttpClient decorate(HttpClient client);
}
