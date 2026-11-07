package cz.insuit.moneta.quarkus.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.HttpClientCustomizer;
import cz.insuit.moneta.HttpClientDecorator;
import cz.insuit.moneta.MonetaClient;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.http.HttpClient;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** The client and the token from quarkus.moneta.*, with the application's customizer and decorators. */
class ClientTest {

    static final FakeMoneta MONETA = new FakeMoneta();
    static final List<String> APPLIED = new CopyOnWriteArrayList<>();
    // A JDK-wide map, the one place both class loaders see: the hook runs in the application's, the check after
    // shutdown in JUnit's.
    private static final String HTTP = ClientTest.class.getName() + ".http";

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(FakeMoneta.class, Hooks.class))
            .overrideConfigKey("quarkus.moneta.base-uri", MONETA.baseUri())
            .overrideConfigKey("quarkus.moneta.token", "secret-token")
            .overrideConfigKey("quarkus.moneta.partner.identification-number", "12345678")
            .overrideConfigKey("quarkus.moneta.partner.business-name", "Example s.r.o.")
            .overrideConfigKey("quarkus.moneta.partner.application-name", "Cafe")
            .setAfterAllCustomizer(() -> assertTrue(
                    ((HttpClient) System.getProperties().remove(HTTP)).isTerminated(), "the HTTP client is closed"));

    @Inject
    MonetaClient moneta;

    @Inject
    AccessToken token;

    @Test
    void readsMonetaWithTheConfiguredTokenAndPartner() {
        // FakeMoneta answers 401 to any other token or partner.
        assertEquals("acc-1", moneta.accounts(token).getFirst().id());
    }

    @Test
    void appliesTheCustomizerThenTheDecoratorsHighestPriorityFirst() {
        assertEquals(List.of("customizer", "decorator 20", "decorator 10"), APPLIED);
    }

    static class Hooks {

        @Produces
        @Singleton
        HttpClientCustomizer customizer() {
            return http -> APPLIED.add("customizer");
        }

        @Produces
        @Singleton
        @Priority(10)
        HttpClientDecorator low() {
            return http -> {
                APPLIED.add("decorator 10");
                return http;
            };
        }

        @Produces
        @Singleton
        @Priority(20)
        HttpClientDecorator high() {
            return http -> {
                APPLIED.add("decorator 20");
                System.getProperties().put(HTTP, http);
                return http;
            };
        }
    }
}
