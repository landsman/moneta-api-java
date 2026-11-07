package example.quarkus;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * The built application, a native image when built with -Dquarkus.native.enabled=true: the extension's beans,
 * its config and the client's JSON binding all have to survive the image. bin/native-test.sh runs it native.
 */
@QuarkusMainIntegrationTest
@QuarkusTestResource(FakeMoneta.class)
class MonetaInQuarkusIT {

    @Test
    @Launch
    void printsTheTransactionsMonetaReturned(LaunchResult result) {
        // After the banner and the startup log.
        assertTrue(result.getOutputStream().contains("-12.50 CZK"), result.getOutput());
    }
}
