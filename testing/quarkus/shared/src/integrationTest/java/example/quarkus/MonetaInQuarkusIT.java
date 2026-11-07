package example.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.main.QuarkusMainIntegrationTest;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import org.junit.jupiter.api.Test;

/**
 * The built application, a native image when built with -Dquarkus.native.enabled=true: the extension's beans,
 * its config and the client's JSON binding all have to survive the image. bin/native-test.sh runs it native.
 */
@QuarkusMainIntegrationTest
@QuarkusTestResource(FakeMoneta.class)
class MonetaInQuarkusIT {

    @Test
    void printsTheTransactionsMonetaReturned(QuarkusMainLauncher launcher) {
        var result = launcher.launch();

        // The application's output in the message, so a CI log shows why it stopped.
        var output = result.getOutput() + "\n" + result.getErrorOutput();
        assertEquals(0, result.exitCode(), output);
        // After the banner and the startup log.
        assertTrue(result.getOutputStream().contains("-12.50 CZK"), output);
    }
}
