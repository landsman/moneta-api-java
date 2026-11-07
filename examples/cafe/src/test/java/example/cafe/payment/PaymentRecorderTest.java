package example.cafe.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Where a round's read starts; the rest of the recorder is MONETA and the database. */
class PaymentRecorderTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);

    @Test
    void withNothingPendingTheReadStartsYesterday() {
        assertEquals(MONDAY.minusDays(1), PaymentRecorder.readFrom(List.of(), MONDAY));
    }

    @Test
    void aPaymentPendingSinceFridayIsReadFromFriday() {
        assertEquals(
                LocalDate.of(2026, 11, 6),
                PaymentRecorder.readFrom(List.of(pendingFrom(LocalDate.of(2026, 11, 6))), MONDAY));
    }

    @Test
    void aPaymentNeverBookedStopsWideningTheReadAfterTwoWeeks() {
        assertEquals(
                MONDAY.minusDays(PaymentRecorder.BOOKING_WAIT_DAYS),
                PaymentRecorder.readFrom(List.of(pendingFrom(LocalDate.of(2026, 9, 1))), MONDAY));
    }

    private static Payment pendingFrom(LocalDate madeOn) {
        return new Payment(
                1L, "p", false, BigDecimal.ONE, "CZK", "1007", null, null, null, madeOn, Instant.EPOCH, 1007L, 0);
    }
}
