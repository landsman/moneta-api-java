package cz.insuit.moneta.quarkus.deployment;

import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class UnknownTlsTest {

    @RegisterExtension
    static final QuarkusUnitTest APP =
            Refusal.of("quarkus.moneta.tls-configuration-name", "nope", "no quarkus.tls.nope.* configuration");

    @Test
    void anUnknownTlsConfigurationStopsTheStart() {}
}
