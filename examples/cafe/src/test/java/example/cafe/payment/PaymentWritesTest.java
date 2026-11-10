package example.cafe.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cz.insuit.moneta.Money;
import cz.insuit.moneta.PaymentSymbols;
import cz.insuit.moneta.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The two queries through their caller, on H2 in PostgreSQL mode as the app runs it. */
@DataJdbcTest
@Import(PaymentWrites.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:payments;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class PaymentWritesTest {

    private static final Instant SEEN = Instant.parse("2026-11-06T21:02:01Z");

    @Autowired
    PaymentWrites writes;

    @Autowired
    PaymentRepository payments;

    @Test
    void aPaymentIsRecordedOnceHoweverOftenItIsWritten() {
        var payment = Payment.of(pending("p-once"), SEEN, null);

        assertTrue(writes.record(payment));
        assertFalse(writes.record(payment));
        assertEquals(1, payments.findByMonetaEntryReferenceIn(List.of("p-once")).size());
    }

    @Test
    void aPendingPaymentIsBookedOnce() {
        writes.record(Payment.of(pending("p-book"), SEEN, null));
        var row = payments.findByMonetaEntryReferenceIn(List.of("p-book")).getFirst();

        assertTrue(writes.book(row, booked("b-book")));
        assertFalse(writes.book(row, booked("b-book-again")));

        var stored = payments.findById(row.id()).orElseThrow();
        assertEquals("b-book", stored.monetaEntryReference());
        assertTrue(stored.booked());
        assertEquals("Novak, Jan", stored.counterpartyName());
        assertEquals("KAVARNA UCET 1007", stored.message());
        assertEquals(SEEN, stored.firstSeenAt());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoRoundsRacingOnOnePaymentRecordItOnce() throws Exception {
        var payment = Payment.of(pending("p-race"), SEEN, null);
        var start = new CountDownLatch(1);
        var rounds = new ArrayList<Callable<Boolean>>();
        for (int i = 0; i < 8; i++) {
            rounds.add(() -> {
                start.await();
                return writes.record(payment);
            });
        }
        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = rounds.stream().map(executor::submit).toList();
            start.countDown();
            long recorded = 0;
            for (var result : results) {
                if (result.get()) {
                    recorded++;
                }
            }

            assertEquals(1, recorded);
            assertEquals(
                    1, payments.findByMonetaEntryReferenceIn(List.of("p-race")).size());
        } finally {
            payments.deleteAll(payments.findByMonetaEntryReferenceIn(List.of("p-race")));
        }
    }

    private static Transaction pending(String reference) {
        return new Transaction(
                reference,
                Transaction.Status.PENDING,
                Transaction.Direction.CREDIT,
                new Money(BigDecimal.ONE, Currency.getInstance("CZK")),
                LocalDate.of(2026, 11, 6),
                null,
                null,
                new PaymentSymbols("1007", null, null),
                "Okamžitá platba",
                null,
                "4040404040/0800",
                false,
                Transaction.Method.TRANSFER,
                "{}");
    }

    private static Transaction booked(String reference) {
        return new Transaction(
                reference,
                Transaction.Status.BOOKED,
                Transaction.Direction.CREDIT,
                new Money(BigDecimal.ONE, Currency.getInstance("CZK")),
                null,
                LocalDate.of(2026, 11, 9),
                LocalDate.of(2026, 11, 6),
                new PaymentSymbols("1007", null, null),
                "KAVARNA UCET 1007",
                "Novak, Jan",
                "0 4040404040/0800",
                false,
                Transaction.Method.TRANSFER,
                "{}");
    }
}
