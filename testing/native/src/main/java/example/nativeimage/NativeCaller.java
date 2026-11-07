package example.nativeimage;

import com.sun.net.httpserver.HttpServer;
import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.MonetaClient;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Reads accounts, a balance and transactions from a fake MONETA serving the client's test fixtures, and prints
 * what the client returned. bin/native-test.sh runs it on the JVM and as a native image and compares the two: a
 * record the image cannot bind fails the call, and a component it cannot see prints as null.
 *
 * <p>Usage: {@code NativeCaller <dir of modules/moneta-api/src/test/resources/moneta>}
 */
public final class NativeCaller {

    private static final String ACCOUNTS = "/api/v4/vip/aisp/my/accounts";

    public static void main(String[] args) throws IOException {
        var fixtures = Path.of(args[0]);
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serve(server, ACCOUNTS, fixtures.resolve("accounts.json"));
        serve(server, ACCOUNTS + "/acc-1/balance", fixtures.resolve("live/balance.json"));
        serve(server, ACCOUNTS + "/acc-1/transactions", fixtures.resolve("live/booked.json"));
        server.start();
        var address = server.getAddress();
        var base = URI.create("http://" + address.getHostString() + ":" + address.getPort() + "/");
        var token = new AccessToken("t");
        try (var moneta = MonetaClient.builder().baseUri(base).build()) {
            moneta.accounts(token).forEach(System.out::println);
            System.out.println(moneta.balance(token, "acc-1"));
            moneta.transactions(token, "acc-1", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
                    .forEach(System.out::println);
        } finally {
            server.stop(0);
        }
    }

    private static void serve(HttpServer server, String path, Path body) {
        server.createContext(path, exchange -> {
            var bytes = Files.readAllBytes(body);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    private NativeCaller() {}
}
