package cz.insuit.moneta.quarkus.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.QuarkusUnitTest;

/** An application that must not start, because the client refuses one value. */
final class Refusal {

    static QuarkusUnitTest of(String key, String value, String message) {
        return new QuarkusUnitTest().overrideConfigKey(key, value).assertException(failure -> {
            var causes = new StringBuilder();
            for (Throwable t = failure; t != null; t = t.getCause()) {
                causes.append(t.getMessage()).append('\n');
            }
            assertTrue(causes.toString().contains(message), causes.toString());
        });
    }

    private Refusal() {}
}
