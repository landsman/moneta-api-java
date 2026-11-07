package example.cafe.bill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import example.cafe.payment.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BillViewTest {

    private static final BigDecimal SIXTY_FIVE = new BigDecimal("65.00");
    private static final Instant FIRST = Instant.parse("2026-11-06T10:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-11-06T10:05:00Z");
    private static final Instant THIRD = Instant.parse("2026-11-06T10:10:00Z");

    @Test
    void anOverpaidBillWasCoveredByThePaymentThatReachedItsAmountNotTheExtraOne() {
        var payments = List.of(payment("65", FIRST), payment("65", SECOND));

        assertEquals(FIRST, BillView.coveredAt(SIXTY_FIVE, payments));
    }

    @Test
    void twoHalvesCoverItWithTheSecond() {
        var payments = List.of(payment("30", SECOND), payment("35", THIRD));

        assertEquals(THIRD, BillView.coveredAt(SIXTY_FIVE, payments));
    }

    @Test
    void anUncoveredBillHasNoTime() {
        assertNull(BillView.coveredAt(SIXTY_FIVE, List.of(payment("30", FIRST))));
    }

    private static Payment payment(String amount, Instant seenAt) {
        return new Payment(
                1L, "p", true, new BigDecimal(amount), "CZK", "1000", null, null, null, null, seenAt, 1000L, 0);
    }
}
