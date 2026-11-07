package cz.insuit.moneta.quarkus.deployment;

import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class BadTokenTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = Refusal.of(
            "quarkus.moneta.token", "secret-token\n", "The token is empty or holds a space or a control character");

    @Test
    void aTokenWithAStrayNewlineStopsTheStart() {}
}
