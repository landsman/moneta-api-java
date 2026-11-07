package cz.insuit.moneta.micronaut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.HttpClientCustomizer;
import cz.insuit.moneta.HttpClientDecorator;
import cz.insuit.moneta.MonetaClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MonetaFactoryTest {

    @Test
    void givesAClientAndNoTokenWhenNoneIsSet() {
        try (var app = ApplicationContext.run()) {
            assertNotNull(app.getBean(MonetaClient.class));
            assertFalse(app.containsBean(AccessToken.class));
            var properties = app.getBean(MonetaProperties.class);
            assertNull(properties.baseUri());
            assertNull(properties.partner());
        }
    }

    @Test
    void givesTheTokenOfTheOneAccountWhenItIsSet() {
        try (var app = ApplicationContext.run(Map.of("moneta.token", "secret-token"))) {
            assertEquals(new AccessToken("secret-token"), app.getBean(AccessToken.class));
        }
    }

    @Test
    void aTokenWithAStrayNewlineStopsTheApplication() {
        var failure = assertThrows(
                RuntimeException.class, () -> ApplicationContext.run(Map.of("moneta.token", "secret-token\n")));
        assertRefused("The token is empty or holds a space or a control character", failure);
    }

    @Test
    void readsTheHostTimeoutAndPartner() {
        try (var app = ApplicationContext.run(Map.of(
                "moneta.base-uri", "https://sandbox.example/moneta",
                "moneta.request-timeout", "5s",
                "moneta.partner.identification-number", "12345678",
                "moneta.partner.business-name", "Example s.r.o.",
                "moneta.partner.application-name", "Cafe"))) {
            var properties = app.getBean(MonetaProperties.class);
            assertEquals(URI.create("https://sandbox.example/moneta"), properties.baseUri());
            assertEquals(Duration.ofSeconds(5), properties.requestTimeout());
            assertEquals(new MonetaProperties.Partner("12345678", "Example s.r.o.", "Cafe"), properties.partner());
        }
    }

    @Test
    void partnerHeadersWithDiacriticsStopTheApplication() {
        var failure = assertThrows(
                RuntimeException.class,
                () -> ApplicationContext.run(Map.of(
                        "moneta.partner.identification-number", "12345678",
                        "moneta.partner.business-name", "Kavárna",
                        "moneta.partner.application-name", "Cafe")));
        assertRefused("businessName must be printable ASCII; transliterate diacritics", failure);
    }

    /** The client refused the value, not something else that broke the start, such as a property not read. */
    private static void assertRefused(String message, Throwable failure) {
        var cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertEquals(message, cause.getMessage());
    }

    @Test
    void stepsBackForAClientTheApplicationDefines() {
        try (var app = ApplicationContext.run(Map.of("spec.name", "own-client"))) {
            assertSame(OwnClient.CLIENT, app.getBean(MonetaClient.class));
            assertEquals(1, app.getBeansOfType(MonetaClient.class).size());
        }
    }

    @Test
    void customizesThenDecoratesTheHttpClientByOrderNotByDefinition() {
        try (var app = ApplicationContext.run(Map.of("spec.name", "http-hooks"))) {
            app.getBean(MonetaClient.class);
            var hooks = app.getBean(HttpHooks.class);
            assertEquals(List.of("customize 1", "customize 2", "decorate 1", "decorate 2"), hooks.calls);
            assertEquals(HttpClient.Redirect.ALWAYS, hooks.decorated.getFirst().followRedirects());
        }
    }

    @Test
    void closesTheClientWithTheContext() {
        var app = ApplicationContext.run(Map.of("spec.name", "http-hooks"));
        var http = app.getBean(HttpHooks.class).decorated.getFirst();
        app.close();
        assertTrue(http.isTerminated());
    }

    @Test
    void tagsEachCallWithItsTemplateWhenAMeterRegistryExists() throws IOException {
        var server = accountsServer();
        try (var app = ApplicationContext.run(Map.of(
                "spec.name",
                "meters",
                "moneta.base-uri",
                "http://localhost:" + server.getAddress().getPort()))) {
            app.getBean(MonetaClient.class).accounts(new AccessToken("secret-token"));
            var timer = app.getBean(MeterRegistry.class)
                    .find("http.client.requests")
                    .tag("uri", "/api/v4/vip/aisp/my/accounts")
                    .timer();
            assertNotNull(timer);
            assertEquals(1, timer.count());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aDecoratorReplacingTheObservationOneTurnsItOff() throws IOException {
        var server = accountsServer();
        try (var app = ApplicationContext.run(Map.of(
                "spec.name",
                "meters",
                "observation",
                "off",
                "moneta.base-uri",
                "http://localhost:" + server.getAddress().getPort()))) {
            app.getBean(MonetaClient.class).accounts(new AccessToken("secret-token"));
            assertNull(app.getBean(MeterRegistry.class)
                    .find("http.client.requests")
                    .timer());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void noObservationWithoutAMeterRegistry() {
        try (var app = ApplicationContext.run()) {
            assertFalse(app.containsBean(HttpClientDecorator.class));
        }
    }

    /** Answers every call with no accounts. */
    private static HttpServer accountsServer() throws IOException {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            var body = "{\"accounts\": []}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Factory
    @Requires(property = "spec.name", value = "own-client")
    static class OwnClient {
        static final MonetaClient CLIENT = MonetaClient.builder().build();

        @Singleton
        MonetaClient own() {
            return CLIENT;
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "meters")
    static class Meters {
        @Singleton
        MeterRegistry meters() {
            return new SimpleMeterRegistry();
        }

        /** What the README shows: one returning its argument turns the observation off. */
        @Singleton
        @Requires(property = "observation", value = "off")
        @Replaces(bean = HttpClientDecorator.class, named = "monetaHttpClientObservation")
        HttpClientDecorator noObservation() {
            return http -> http;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "http-hooks")
    static class HttpHooks {
        final List<String> calls = new ArrayList<>();
        final List<HttpClient> decorated = new ArrayList<>();
    }

    /** Declared out of order, so only @Order can put them right. */
    @Factory
    @Requires(property = "spec.name", value = "http-hooks")
    static class HttpHookBeans {
        @Singleton
        @Order(2)
        HttpClientCustomizer secondCustomizer(HttpHooks hooks) {
            return http -> hooks.calls.add("customize 2");
        }

        @Singleton
        @Order(1)
        HttpClientCustomizer firstCustomizer(HttpHooks hooks) {
            return http -> {
                hooks.calls.add("customize 1");
                http.followRedirects(HttpClient.Redirect.ALWAYS);
            };
        }

        @Singleton
        @Order(2)
        HttpClientDecorator outerDecorator(HttpHooks hooks) {
            return http -> {
                hooks.calls.add("decorate 2");
                return http;
            };
        }

        @Singleton
        @Order(1)
        HttpClientDecorator innerDecorator(HttpHooks hooks) {
            return http -> {
                hooks.calls.add("decorate 1");
                hooks.decorated.add(http);
                return http;
            };
        }
    }
}
