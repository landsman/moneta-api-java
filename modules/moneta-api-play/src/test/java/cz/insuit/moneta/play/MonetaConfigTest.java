package cz.insuit.moneta.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import cz.insuit.moneta.AccessToken;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The mapping from {@code moneta.*}; testing/play starts a real application with the module. */
class MonetaConfigTest {

    @Test
    void theClientCallsTheConfiguredHostWithThePartnerHeaders() throws IOException {
        var partnerHeader = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            partnerHeader.set(exchange.getRequestHeaders().getFirst("identification_number"));
            var bytes = "{\"transactions\": [], \"pageCount\": 1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        var config = ConfigFactory.parseString("""
                moneta {
                  base-uri = "http://%s:%d/"
                  request-timeout = 5s
                  partner { identification-number = "12345678", business-name = "Kavarna", application-name = "cafe" }
                }
                """.formatted(
                        server.getAddress().getHostString(), server.getAddress().getPort()));
        try (var client = MonetaConfig.builder(config).build()) {
            client.transactions(new AccessToken("t"), "acc-1", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
            assertEquals("12345678", partnerHeader.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aPartnerMissingAKeyStopsTheStart() {
        var config = ConfigFactory.parseString("moneta.partner.business-name = Kavarna");
        assertThrows(ConfigException.Missing.class, () -> MonetaConfig.builder(config));
    }
}
