package example.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import cz.landsman.moneta.AccessToken;
import cz.landsman.moneta.MonetaClient;
import cz.landsman.moneta.Money;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * The library inside a Spring Boot application whose BOM picks the Jackson versions, the way a caller's build
 * does. Shared by every testing/spring-boot/<version> project; each sets a different Spring Boot version.
 */
class MonetaInSpringBootTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {

        @Bean
        MonetaClient moneta(Environment environment) {
            return MonetaClient.builder()
                    .baseUri(URI.create(environment.getRequiredProperty("moneta.base-uri")))
                    .build();
        }
    }

    @Test
    void aClientTheContextBuiltReadsTransactions() throws IOException {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes;
            try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/transactions.json"))) {
                bytes = in.readAllBytes();
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        var base = "http://" + server.getAddress().getHostString() + ":"
                + server.getAddress().getPort() + "/";
        try (var context = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.NONE)
                .properties("moneta.base-uri=" + base)
                .run()) {
            var moneta = context.getBean(MonetaClient.class);

            var transactions = moneta.transactions(
                    new AccessToken("t"), "acc-1", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

            assertEquals(
                    new Money(new BigDecimal("-12.50"), Currency.getInstance("CZK")),
                    transactions.getFirst().amount());
        } finally {
            server.stop(0);
        }
    }
}
