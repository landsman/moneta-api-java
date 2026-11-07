package cz.insuit.moneta.quarkus.deployment;

import static org.junit.jupiter.api.Assertions.assertSame;

import cz.insuit.moneta.MonetaClient;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OwnClientTest {

    static final MonetaClient OWN = new MonetaClient();

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest().withApplicationRoot(jar -> jar.addClass(Own.class));

    @Inject
    MonetaClient moneta;

    @Test
    void theApplicationsOwnClientReplacesTheExtensions() {
        assertSame(OWN, moneta);
    }

    static class Own {

        @Produces
        @Singleton
        MonetaClient own() {
            return OWN;
        }
    }
}
