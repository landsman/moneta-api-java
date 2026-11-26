package example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cz.insuit.moneta.MonetaApiException;
import cz.insuit.moneta.MonetaHttpErrorException;
import cz.insuit.moneta.MonetaInvalidResponseException;
import cz.insuit.moneta.MonetaInvalidTokenException;
import cz.insuit.moneta.MonetaNoAnswerException;
import cz.insuit.moneta.MonetaRateLimitedException;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Outside the library's package on purpose: that this compiles is the test that a caller can build them. */
class CallerTest {

    @Test
    void aCallersFakeCanThrowEveryFailure() {
        var failures = new MonetaApiException[] {
            new MonetaInvalidTokenException("refused", 401, null),
            new MonetaRateLimitedException("slow", Duration.ofSeconds(5)),
            new MonetaHttpErrorException("down", 503, null, null),
            new MonetaNoAnswerException("gone", new IOException("reset")),
            new MonetaInvalidResponseException("odd", null)
        };
        assertEquals(5, failures.length);
    }
}
