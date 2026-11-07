package example.quarkus;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;

/** A local MONETA answering every call with testing/resources/transactions.json, and the config pointing at it. */
public class FakeMoneta implements QuarkusTestResourceLifecycleManager {

    private HttpServer server;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            byte[] bytes;
            try (var in = Objects.requireNonNull(FakeMoneta.class.getResourceAsStream("/transactions.json"))) {
                bytes = in.readAllBytes();
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        var address = server.getAddress();
        return Map.of(
                "quarkus.moneta.base-uri",
                "http://" + address.getHostString() + ":" + address.getPort() + "/",
                "quarkus.moneta.token",
                "t");
    }

    @Override
    public void stop() {
        server.stop(0);
    }
}
