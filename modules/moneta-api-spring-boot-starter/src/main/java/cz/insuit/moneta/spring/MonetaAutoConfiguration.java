package cz.insuit.moneta.spring;

import static java.util.Objects.requireNonNull;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.HttpClientCustomizer;
import cz.insuit.moneta.HttpClientDecorator;
import cz.insuit.moneta.MonetaClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.java11.instrument.binder.jdk.MicrometerHttpClient;
import io.micrometer.observation.ObservationRegistry;
import javax.net.ssl.SSLParameters;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * One {@link MonetaClient} for the application, built from {@code moneta.*}, unless the application defines its
 * own. Spring closes it on shutdown, which releases the HTTP client it made. Each {@link HttpClientCustomizer}
 * bean changes that HTTP client before it is built, and each {@link HttpClientDecorator} wraps it, in order.
 */
@AutoConfiguration
@EnableConfigurationProperties(MonetaProperties.class)
public class MonetaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MonetaClient monetaClient(
            MonetaProperties properties,
            ObjectProvider<SslBundles> sslBundles,
            ObjectProvider<HttpClientCustomizer> customizers,
            ObjectProvider<HttpClientDecorator> decorators) {
        // Only what the application set is passed on, so each default lives in one place: the client.
        var builder = MonetaClient.builder();
        var baseUri = properties.baseUri();
        if (baseUri != null) {
            builder.baseUri(baseUri);
        }
        var requestTimeout = properties.requestTimeout();
        if (requestTimeout != null) {
            builder.requestTimeout(requestTimeout);
        }
        var partner = properties.partner();
        if (partner != null) {
            builder.partner(new MonetaClient.Partner(
                    partner.identificationNumber(), partner.businessName(), partner.applicationName()));
        }
        var ssl = properties.ssl();
        var bundleName = ssl == null ? null : ssl.bundle();
        if (bundleName != null) {
            // Resolved now, so an unknown name stops the application rather than the first call.
            var bundle = sslBundles.getObject().getBundle(bundleName);
            var context = bundle.createSslContext();
            var options = bundle.getOptions();
            builder.configureHttpClient(http -> {
                http.sslContext(context);
                if (options.isSpecified()) {
                    var parameters = new SSLParameters(options.getCiphers(), options.getEnabledProtocols());
                    http.sslParameters(parameters);
                }
            });
        }
        // After the bundle, so a customizer can still change what it set.
        customizers.orderedStream().forEach(customizer -> builder.configureHttpClient(customizer::customize));
        decorators.orderedStream().forEach(decorator -> builder.decorateHttpClient(decorator::decorate));
        return builder.build();
    }

    /** Only with {@code moneta.token} set; one with a space or a control character anywhere, such as the newline an
     * environment variable often ends in, stops the application. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty("moneta.token")
    public AccessToken monetaAccessToken(MonetaProperties properties) {
        return new AccessToken(requireNonNull(properties.token(), "moneta.token"));
    }

    /**
     * Metrics, and traces when Micrometer Tracing is set up, for every call, tagged by {@link
     * MonetaClient#uriTemplate}: on when {@code micrometer-java11} is on the classpath and a {@code MeterRegistry}
     * exists. Off under the OpenTelemetry Java agent, which instruments the JDK client already; a second wrapper
     * would add a second span per call. An {@code HttpClientDecorator} bean of the same name replaces it, and
     * one returning its argument turns it off.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MicrometerHttpClient.class)
    @ConditionalOnMissingClass("io.opentelemetry.javaagent.bootstrap.AgentInitializer")
    static class Observation {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        @ConditionalOnMissingBean(name = "monetaHttpClientObservation")
        HttpClientDecorator monetaHttpClientObservation(
                ObjectProvider<MeterRegistry> meters, ObjectProvider<ObservationRegistry> observations) {
            // Read when the client is built, not here: no auto-configuration order to depend on.
            return http -> {
                var registry = meters.getIfAvailable();
                if (registry == null) {
                    return http;
                }
                var instrumented = MicrometerHttpClient.instrumentationBuilder(http, registry)
                        .uriMapper(MonetaClient::uriTemplate);
                observations.ifAvailable(instrumented::observationRegistry);
                return instrumented.build();
            };
        }
    }
}
