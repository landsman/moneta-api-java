package cz.insuit.moneta.quarkus.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cz.insuit.moneta.HttpClientDecorator;
import io.quarkus.arc.All;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** quarkus.moneta.metrics.enabled=false leaves the HTTP client unwrapped, with Micrometer present. */
class MetricsOffTest {

    @RegisterExtension
    static final QuarkusUnitTest APP =
            new QuarkusUnitTest().overrideConfigKey("quarkus.moneta.metrics.enabled", "false");

    @Inject
    @All
    List<HttpClientDecorator> decorators;

    @Test
    void registersNoDecorator() {
        assertTrue(decorators.isEmpty(), decorators.toString());
    }
}
