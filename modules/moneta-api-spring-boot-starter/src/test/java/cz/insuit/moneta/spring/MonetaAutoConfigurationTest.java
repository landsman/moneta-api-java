package cz.insuit.moneta.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.MonetaClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

class MonetaAutoConfigurationTest {

    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MonetaAutoConfiguration.class));

    @Test
    void givesAClientAndNoTokenWhenNoneIsSet() {
        context.run(app -> {
            assertNotNull(app.getBean(MonetaClient.class));
            assertFalse(app.containsBean("monetaAccessToken"));
        });
    }

    @Test
    void leavesTheHostAndTimeoutToTheClientWhenUnset() {
        context.run(app -> {
            var properties = app.getBean(MonetaProperties.class);
            assertNull(properties.baseUri());
            assertNull(properties.requestTimeout());
        });
    }

    @Test
    void givesTheTokenOfTheOneAccountWhenItIsSet() {
        context.withPropertyValues("moneta.token=secret-token").run(app -> {
            assertEquals(new AccessToken("secret-token"), app.getBean(AccessToken.class));
        });
    }

    @Test
    void aTokenWithAStrayNewlineStopsTheApplication() {
        context.withPropertyValues("moneta.token=secret token").run(app -> assertNotNull(app.getStartupFailure()));
    }

    @Test
    void readsTheHostTimeoutAndPartner() {
        context.withPropertyValues(
                        "moneta.base-uri=https://sandbox.example/moneta",
                        "moneta.request-timeout=5s",
                        "moneta.partner.identification-number=12345678",
                        "moneta.partner.business-name=Example s.r.o.",
                        "moneta.partner.application-name=Cafe")
                .run(app -> {
                    var properties = app.getBean(MonetaProperties.class);
                    assertEquals(URI.create("https://sandbox.example/moneta"), properties.baseUri());
                    assertEquals(Duration.ofSeconds(5), properties.requestTimeout());
                    assertNotNull(properties.partner());
                    assertNotNull(app.getBean(MonetaClient.class));
                });
    }

    @Test
    void partnerHeadersWithDiacriticsStopTheApplication() {
        context.withPropertyValues(
                        "moneta.partner.identification-number=12345678",
                        "moneta.partner.business-name=Kavárna",
                        "moneta.partner.application-name=Cafe")
                .run(app -> assertNotNull(app.getStartupFailure()));
    }

    @Test
    void stepsBackForAClientTheApplicationDefines() {
        var own = MonetaClient.builder().build();
        context.withBean(MonetaClient.class, () -> own).run(app -> assertSame(own, app.getBean(MonetaClient.class)));
    }

    @Test
    void customizesThenDecoratesTheHttpClientByOrderNotByDefinition() {
        context.withUserConfiguration(HttpHooks.class).run(app -> {
            app.getBean(MonetaClient.class);
            var hooks = app.getBean(HttpHooks.class);
            assertEquals(List.of("customize 1", "customize 2", "decorate 1", "decorate 2"), hooks.calls);
            assertEquals(HttpClient.Redirect.ALWAYS, hooks.decorated.getFirst().followRedirects());
        });
    }

    @Test
    void tagsEachCallWithItsTemplateWhenAMeterRegistryExists() throws IOException {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            var body = "{\"accounts\": []}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            context.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                    .withPropertyValues("moneta.base-uri=http://localhost:"
                            + server.getAddress().getPort())
                    .run(app -> {
                        app.getBean(MonetaClient.class).accounts(new AccessToken("secret-token"));
                        var timer = app.getBean(MeterRegistry.class)
                                .find("http.client.requests")
                                .tag("uri", "/api/v4/vip/aisp/my/accounts")
                                .timer();
                        assertNotNull(timer);
                        assertEquals(1, timer.count());
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void leavesTheClientUninstrumentedWithoutAMeterRegistry() {
        context.run(app -> {
            var decorator = app.getBean("monetaHttpClientObservation", MonetaHttpClientDecorator.class);
            var http = HttpClient.newHttpClient();
            assertSame(http, decorator.decorate(http));
        });
    }

    @Test
    void writesMetadataForTheEditor() throws IOException {
        assertTrue(MonetaProperties.class
                .getClassLoader()
                .getResources("META-INF/spring-configuration-metadata.json")
                .hasMoreElements());
    }

    /** Each hook declared with order 2 first, so the order seen can only come from {@code @Order}. */
    @Configuration(proxyBeanMethods = false)
    static class HttpHooks {

        final List<String> calls = new ArrayList<>();
        final List<HttpClient> decorated = new ArrayList<>();

        @Bean
        @Order(2)
        MonetaHttpClientCustomizer secondCustomizer() {
            return http -> calls.add("customize 2");
        }

        @Bean
        @Order(1)
        MonetaHttpClientCustomizer firstCustomizer() {
            return http -> {
                calls.add("customize 1");
                http.followRedirects(HttpClient.Redirect.ALWAYS);
            };
        }

        @Bean
        @Order(2)
        MonetaHttpClientDecorator outerDecorator() {
            return http -> {
                calls.add("decorate 2");
                return http;
            };
        }

        @Bean
        @Order(1)
        MonetaHttpClientDecorator innerDecorator() {
            return http -> {
                calls.add("decorate 1");
                decorated.add(http);
                return http;
            };
        }
    }
}
