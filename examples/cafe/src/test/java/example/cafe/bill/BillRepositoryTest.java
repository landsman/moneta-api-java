package example.cafe.bill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import example.cafe.payment.Payment;
import example.cafe.payment.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;

/** The two tables as schema.sql makes them, on H2 like the app. */
@DataJdbcTest
class BillRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Autowired
    BillRepository bills;

    @Autowired
    PaymentRepository payments;

    @Test
    void symbolsAscendFromAThousand() {
        var coffee = bills.save(Bill.issue(new BigDecimal("65"), NOW));
        var cake = bills.save(Bill.issue(new BigDecimal("89"), NOW));

        // A rolled-back test does not give its ids back, and neither does production: a symbol is never reused.
        assertTrue(Long.parseLong(coffee.variableSymbol()) >= 1000);
        assertEquals(Long.parseLong(coffee.variableSymbol()) + 1, Long.parseLong(cake.variableSymbol()));
    }

    @Test
    void aBillsPaymentsAreTheOnesLinkedToIt() {
        var coffee = bills.save(Bill.issue(new BigDecimal("65"), NOW));
        payments.save(payment("p1", coffee.id()));
        payments.save(payment("p2", null));

        assertEquals(1, payments.findByBillIdOrderByFirstSeenAt(coffee.id()).size());
        assertEquals(1, payments.findByBillIdIsNullOrderByFirstSeenAtDesc().size());
    }

    @Test
    void onePaymentIsRecordedOnce() {
        payments.save(payment("p1", null));

        assertThrows(RuntimeException.class, () -> payments.save(payment("p1", null)));
    }

    private static Payment payment(String reference, Long billId) {
        return new Payment(
                null,
                reference,
                false,
                new BigDecimal("65"),
                "CZK",
                null,
                null,
                null,
                LocalDate.of(2026, 11, 6),
                NOW,
                billId,
                null);
    }
}
