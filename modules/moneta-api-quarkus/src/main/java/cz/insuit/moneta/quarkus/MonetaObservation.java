package cz.insuit.moneta.quarkus;

import cz.insuit.moneta.HttpClientDecorator;
import cz.insuit.moneta.MonetaClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.java11.instrument.binder.jdk.MicrometerHttpClient;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Times every call as {@code http.client.requests}, tagged by {@link MonetaClient#uriTemplate}, so the tag holds
 * no account id. Registered at build time only when the application has {@code quarkus-micrometer} and {@code
 * micrometer-java11}, and {@code quarkus.moneta.metrics.enabled} is not false. Applied first, before the
 * application's own decorators, as in the starter.
 */
@Singleton
public class MonetaObservation {

    private static final String OPENTELEMETRY_AGENT = "io.opentelemetry.javaagent.bootstrap.AgentInitializer";

    @Produces
    @Singleton
    @Named("monetaHttpClientObservation")
    @Priority(Integer.MAX_VALUE)
    HttpClientDecorator monetaHttpClientObservation(MeterRegistry meters) {
        // The OpenTelemetry Java agent instruments the JDK client already; a second wrapper would count each call
        // twice. It attaches when the application starts, so this is decided then, not at build time.
        if (underOpenTelemetryAgent()) {
            return http -> http;
        }
        return http -> MicrometerHttpClient.instrumentationBuilder(http, meters)
                .uriMapper(MonetaClient::uriTemplate)
                .build();
    }

    private static boolean underOpenTelemetryAgent() {
        try {
            Class.forName(OPENTELEMETRY_AGENT, false, ClassLoader.getSystemClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
