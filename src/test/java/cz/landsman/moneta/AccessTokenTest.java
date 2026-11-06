package cz.landsman.moneta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccessTokenTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "secret token", "secret\n", "secret\t", "secret\u007F", "secrét"})
    void refusesWhatTheJdkWouldQuoteWithoutRepeatingIt(String value) {
        var refused = assertThrows(IllegalArgumentException.class, () -> new AccessToken(value));
        assertFalse(
                !value.isEmpty() && Objects.requireNonNull(refused.getMessage()).contains("secret"));
    }

    @Test
    void neverShowsItsValue() {
        assertEquals("AccessToken[redacted]", new AccessToken("secret").toString());
    }
}
