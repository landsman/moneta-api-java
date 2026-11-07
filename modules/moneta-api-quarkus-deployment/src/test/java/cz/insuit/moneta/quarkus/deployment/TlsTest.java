package cz.insuit.moneta.quarkus.deployment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import cz.insuit.moneta.HttpClientDecorator;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.net.http.HttpClient;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** The HTTP client takes its SSLContext and protocols from the named quarkus.tls configuration. */
class TlsTest {

    static final AtomicReference<HttpClient> HTTP = new AtomicReference<>();

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClass(Capture.class))
            .overrideConfigKey("quarkus.moneta.tls-configuration-name", "moneta")
            .overrideConfigKey("quarkus.tls.moneta.trust-all", "true")
            .overrideConfigKey("quarkus.tls.moneta.protocols", "TLSv1.3");

    @Test
    void usesTheNamedConfiguration() throws NoSuchAlgorithmException {
        var http = HTTP.get();
        assertNotSame(SSLContext.getDefault(), http.sslContext());
        assertArrayEquals(new String[] {"TLSv1.3"}, http.sslParameters().getProtocols());
    }

    static class Capture {

        @Produces
        @Singleton
        HttpClientDecorator capture() {
            return http -> {
                HTTP.set(http);
                return http;
            };
        }
    }
}
