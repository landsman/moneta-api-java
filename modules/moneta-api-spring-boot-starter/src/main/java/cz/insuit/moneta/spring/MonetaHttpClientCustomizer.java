package cz.insuit.moneta.spring;

import java.net.http.HttpClient;

/**
 * A bean of this type changes the HTTP client the auto-configured {@code MonetaClient} builds, before it is built:
 * a proxy, an {@code SSLContext} for mTLS or a trust store. Several apply in their {@code @Order}; the client still
 * owns and closes the result. See {@code MonetaClient.Builder.configureHttpClient}. Ignored when the application
 * defines its own {@code MonetaClient} bean.
 */
@FunctionalInterface
public interface MonetaHttpClientCustomizer {

    void customize(HttpClient.Builder builder);
}
