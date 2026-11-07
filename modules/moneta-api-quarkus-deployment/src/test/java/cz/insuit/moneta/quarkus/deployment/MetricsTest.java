package cz.insuit.moneta.quarkus.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.MonetaClient;
import cz.insuit.moneta.MonetaException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** With quarkus-micrometer and micrometer-java11 on the classpath, each call is timed, tagged by endpoint. */
class MetricsTest {

    static final FakeMoneta MONETA = new FakeMoneta();

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(FakeMoneta.class, Registry.class))
            .overrideConfigKey("quarkus.moneta.base-uri", MONETA.baseUri())
            .overrideConfigKey("quarkus.moneta.token", "secret-token")
            .overrideConfigKey("quarkus.moneta.partner.identification-number", "12345678")
            .overrideConfigKey("quarkus.moneta.partner.business-name", "Example s.r.o.")
            .overrideConfigKey("quarkus.moneta.partner.application-name", "Cafe");

    @Inject
    MonetaClient moneta;

    @Inject
    AccessToken token;

    @Inject
    SimpleMeterRegistry meters;

    @Test
    void timesTheCallTaggedByItsTemplateNotTheAccount() {
        // FakeMoneta answers every path with accounts, so reading the balance fails; the call is timed all the same.
        assertThrows(MonetaException.class, () -> moneta.balance(token, "acc-1"));

        var timer = meters.get("http.client.requests")
                .tag("uri", "/api/v4/vip/aisp/my/accounts/{accountId}/balance")
                .timer();
        assertEquals(1, timer.count());
    }

    static class Registry {

        // quarkus-micrometer adds every MeterRegistry bean to the registry it gives the application.
        @Produces
        @Singleton
        SimpleMeterRegistry simple() {
            return new SimpleMeterRegistry();
        }
    }
}
