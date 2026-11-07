package cz.insuit.moneta.quarkus;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.HttpClientCustomizer;
import cz.insuit.moneta.HttpClientDecorator;
import cz.insuit.moneta.MonetaClient;
import io.quarkus.arc.All;
import io.quarkus.arc.DefaultBean;
import io.quarkus.runtime.Startup;
import io.quarkus.tls.TlsConfigurationRegistry;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * One {@link MonetaClient} for the application, built from {@code quarkus.moneta.*} at startup, unless the
 * application produces its own. Closed on shutdown, which releases the HTTP client it made. Each {@link
 * HttpClientCustomizer} bean changes that HTTP client before it is built, and each {@link HttpClientDecorator}
 * wraps it, in {@code @Priority} order, highest first.
 */
@Singleton
public class MonetaProducer {

    // At startup, so a token, partner or TLS name the client refuses stops the application, not the first call.
    @Produces
    @Singleton
    @DefaultBean
    @Startup
    MonetaClient monetaClient(
            MonetaConfig config,
            TlsConfigurationRegistry tlsRegistry,
            @All List<HttpClientCustomizer> customizers,
            @All List<HttpClientDecorator> decorators) {
        config.token().ifPresent(AccessToken::new); // throws for a value it refuses; the bean is made when injected
        // Only what the application set is passed on, so each default lives in one place: the client.
        var builder = MonetaClient.builder();
        config.baseUri().ifPresent(builder::baseUri);
        config.requestTimeout().ifPresent(builder::requestTimeout);
        config.partner()
                .ifPresent(partner -> builder.partner(new MonetaClient.Partner(
                        partner.identificationNumber(), partner.businessName(), partner.applicationName())));
        config.tlsConfigurationName().ifPresent(name -> tls(builder, tlsRegistry, name));
        // After TLS, so a customizer can still change what it set.
        customizers.forEach(customizer -> builder.configureHttpClient(customizer::customize));
        decorators.forEach(decorator -> builder.decorateHttpClient(decorator::decorate));
        return builder.build();
    }

    void close(@Disposes MonetaClient client) {
        client.close();
    }

    private static void tls(MonetaClient.Builder builder, TlsConfigurationRegistry registry, String name) {
        var tls = registry.get(name)
                .orElseThrow(() -> new IllegalStateException("quarkus.moneta.tls-configuration-name is " + name
                        + ", and no quarkus.tls." + name + ".* configuration exists"));
        SSLContext context;
        try {
            context = tls.createSSLContext();
        } catch (Exception e) {
            throw new IllegalStateException("quarkus.tls." + name + ".* does not make an SSLContext", e);
        }
        var parameters = new SSLParameters();
        var options = tls.getSSLOptions();
        if (options != null) {
            // Only what the configuration names; an empty list would leave the handshake nothing to agree on.
            if (!options.getEnabledSecureTransportProtocols().isEmpty()) {
                parameters.setProtocols(
                        options.getEnabledSecureTransportProtocols().toArray(String[]::new));
            }
            if (!options.getEnabledCipherSuites().isEmpty()) {
                parameters.setCipherSuites(options.getEnabledCipherSuites().toArray(String[]::new));
            }
        }
        builder.configureHttpClient(http -> {
            http.sslContext(context);
            if (parameters.getProtocols() != null || parameters.getCipherSuites() != null) {
                http.sslParameters(parameters);
            }
        });
    }
}
