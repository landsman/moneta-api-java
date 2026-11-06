package example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cz.landsman.moneta.MonetaException;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Outside the library's package on purpose: that this compiles is the test that a caller can build them. */
class CallerTest {

    @Test
    void aCallersFakeCanThrowEveryFailure() {
        var failures = new MonetaException[] {
            new MonetaException.InvalidToken("refused", 401, null),
            new MonetaException.RateLimited("slow", Duration.ofSeconds(5)),
            new MonetaException.HttpError("down", 503, null, null),
            new MonetaException.NoAnswer("gone", new IOException("reset")),
            new MonetaException.InvalidResponse("odd", null)
        };
        assertEquals(5, failures.length);
    }
}
