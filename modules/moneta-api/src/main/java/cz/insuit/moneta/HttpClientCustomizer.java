package cz.insuit.moneta;

import java.net.http.HttpClient;

/**
 * Changes the HTTP client a framework integration's {@code MonetaClient} builds, before it is built: a proxy, an
 * {@code SSLContext} for mTLS or a trust store. As a bean, several apply in their order; the client still owns and
 * closes the result. See {@link MonetaClient.Builder#configureHttpClient}. Ignored when the application defines its
 * own {@code MonetaClient} bean.
 */
@FunctionalInterface
public interface HttpClientCustomizer {

    void customize(HttpClient.Builder builder);
}
