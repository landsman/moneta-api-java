package cz.insuit.moneta.micronaut;

import static java.util.Objects.requireNonNull;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.HttpClientCustomizer;
import cz.insuit.moneta.HttpClientDecorator;
import cz.insuit.moneta.MonetaClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.java11.instrument.binder.jdk.MicrometerHttpClient;
import io.micrometer.observation.ObservationRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * One {@link MonetaClient} for the application, built from {@code moneta.*}, unless the application defines its
 * own. Closed with the context, which releases the HTTP client it made. Each {@link HttpClientCustomizer} bean
 * changes that HTTP client before it is built, and each {@link HttpClientDecorator} wraps it, in {@code @Order}.
 */
@Factory
public class MonetaFactory {

    /** Eager, so a value the client refuses stops startup, as it does under Spring Boot. */
    @Context
    @Bean(preDestroy = "close")
    @Requires(missingBeans = MonetaClient.class)
    public MonetaClient monetaClient(
            MonetaProperties properties, List<HttpClientCustomizer> customizers, List<HttpClientDecorator> decorators) {
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
        // Micronaut hands both lists sorted by @Order.
        customizers.forEach(customizer -> builder.configureHttpClient(customizer::customize));
        decorators.forEach(decorator -> builder.decorateHttpClient(decorator::decorate));
        return builder.build();
    }

    /** Only with {@code moneta.token} set; one with a space or a control character anywhere, such as the newline an
     * environment variable often ends in, stops the application. */
    @Context
    @Requires(property = "moneta.token")
    @Requires(missingBeans = AccessToken.class)
    public AccessToken monetaAccessToken(MonetaProperties properties) {
        return new AccessToken(requireNonNull(properties.token(), "moneta.token"));
    }

    /**
     * Metrics, and traces when an {@code ObservationRegistry} exists, for every call, tagged by {@link
     * MonetaClient#uriTemplate}: on when {@code micrometer-java11} is on the classpath and a {@code MeterRegistry}
     * exists. Off under the OpenTelemetry Java agent, which instruments the JDK client already. A decorator with
     * {@code @Replaces(bean = HttpClientDecorator.class, named = "monetaHttpClientObservation")} replaces it, and
     * one returning its argument turns it off.
     */
    @Singleton
    @Named("monetaHttpClientObservation")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Requires(classes = MicrometerHttpClient.class, beans = MeterRegistry.class)
    @Requires(missingClasses = "io.opentelemetry.javaagent.bootstrap.AgentInitializer")
    public HttpClientDecorator monetaHttpClientObservation(
            MeterRegistry meters, Optional<ObservationRegistry> observations) {
        return http -> {
            var instrumented =
                    MicrometerHttpClient.instrumentationBuilder(http, meters).uriMapper(MonetaClient::uriTemplate);
            observations.ifPresent(instrumented::observationRegistry);
            return instrumented.build();
        };
    }
}
