package cz.insuit.moneta.quarkus.deployment;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * MONETA answering one account, and only to the token and partner the tests configure: QuarkusUnitTest loads the
 * test class again in the application's class loader, so what this server saw cannot be read back from there.
 */
final class FakeMoneta {

    // language=JSON
    private static final String ONE_ACCOUNT = """
            {"accounts": [{"id": "acc-1", "currency": "CZK"}], "pageNumber": 0, "pageCount": 1}""";

    private final HttpServer server;

    FakeMoneta() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            var headers = exchange.getRequestHeaders();
            var expected = "Bearer secret-token".equals(headers.getFirst("Authorization"))
                    && "12345678".equals(headers.getFirst("identification_number"));
            var bytes = ONE_ACCOUNT.getBytes(StandardCharsets.UTF_8);
            if (!expected) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    String baseUri() {
        return "http://" + server.getAddress().getHostString() + ":"
                + server.getAddress().getPort() + "/";
    }
}
