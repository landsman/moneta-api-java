package example.cafe.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import example.cafe.bill.Bill;
import example.cafe.bill.BillRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The whole round, from MONETA to the database, run by eight threads at once: what a scheduled round and the till's
 * lookups do when they overlap. MONETA is a local server answering from files.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentRecorderRaceTest {

    // Nothing until the bill exists: the schedule's first round runs as the context starts, and would record the
    // payments before there is a bill to link them to.
    private static final AtomicReference<String> TRANSACTIONS = new AtomicReference<>("moneta/none.json");
    private static final HttpServer MONETA = monetaServer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        var address = MONETA.getAddress();
        registry.add("moneta.base-uri", () -> "http://" + address.getHostString() + ":" + address.getPort() + "/");
        registry.add("moneta.token", () -> "t");
        registry.add("cafe.iban", () -> "CZ0206000000000000001337");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:race;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        // After its first round the schedule stays out of the way; the threads below are the race.
        registry.add("cafe.check-moneta-every", () -> "PT1H");
    }

    @Autowired
    PaymentRecorder recorder;

    @Autowired
    PaymentRepository payments;

    @Autowired
    BillRepository bills;

    @AfterAll
    static void stopMoneta() {
        MONETA.stop(0);
    }

    @Test
    void eightRoundsAtOnceRecordEachPaymentOnceAndBookItOnce() throws Exception {
        var bill = bills.save(new Bill(null, new BigDecimal("65.00"), "CZK", Instant.now(), null));
        assertEquals("1000", bill.variableSymbol());

        TRANSACTIONS.set("moneta/pending.json");
        raceRounds();

        var pending = payments.findAllByOrderByFirstSeenAtDesc();
        assertEquals(2, pending.size(), pending.toString());
        assertTrue(pending.stream().noneMatch(Payment::booked));

        TRANSACTIONS.set("moneta/booked.json");
        raceRounds();

        var booked = payments.findAllByOrderByFirstSeenAtDesc();
        assertEquals(2, booked.size(), booked.toString());
        assertTrue(booked.stream().allMatch(Payment::booked));
        var forBill = payments.findByBillIdOrderByFirstSeenAt(1000L);
        assertEquals(1, forBill.size());
        assertEquals("KAVARNA UCET 1000", forBill.getFirst().message());
        assertEquals("Novak, Jan", forBill.getFirst().counterpartyName());
    }

    private void raceRounds() throws Exception {
        var start = new CountDownLatch(1);
        var rounds = new ArrayList<Callable<Void>>();
        for (int i = 0; i < 8; i++) {
            rounds.add(() -> {
                start.await();
                recorder.record();
                return null;
            });
        }
        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = rounds.stream().map(executor::submit).toList();
            start.countDown();
            for (var result : results) {
                result.get();
            }
        }
    }

    private static HttpServer monetaServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.setExecutor(Executors.newFixedThreadPool(8));
            server.createContext("/", exchange -> {
                var path = exchange.getRequestURI().getPath();
                var body = read(path.endsWith("/transactions") ? TRANSACTIONS.get() : "moneta/accounts.json");
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] read(String resource) throws IOException {
        try (var in = Objects.requireNonNull(
                PaymentRecorderRaceTest.class.getClassLoader().getResourceAsStream(resource), resource)) {
            return in.readAllBytes();
        }
    }
}
