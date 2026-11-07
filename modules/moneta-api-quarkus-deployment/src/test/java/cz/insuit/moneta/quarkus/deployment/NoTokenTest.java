package cz.insuit.moneta.quarkus.deployment;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.MonetaClient;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InactiveBeanException;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** An application serving many merchants sets no token: it starts, and only AccessToken is missing. */
class NoTokenTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest();

    @Inject
    MonetaClient moneta;

    @Test
    void startsWithAClientAndAnInactiveToken() {
        assertNotNull(moneta);
        var failure = assertThrows(
                InactiveBeanException.class,
                () -> Arc.container().select(AccessToken.class).get());
        assertTrue(failure.getMessage().contains("quarkus.moneta.token is not set"), failure.getMessage());
    }
}
