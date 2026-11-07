package example.cafe.bill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Where a check's read starts; the rest of the checker is MONETA and the database. */
class BillCheckerTest {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);

    @Test
    void anUnpaidBillIsLookedForSinceYesterday() {
        assertEquals(MONDAY.minusDays(1), BillChecker.readFrom(List.of(unpaid()), MONDAY, PRAGUE));
    }

    @Test
    void aPaymentFirstSeenJustAfterMidnightIsReadFromTheDayBefore() {
        // Paid Friday 23:59:55, seen Saturday 00:00:30, booked Monday and dated Friday.
        var seen = Instant.parse("2026-11-06T23:00:30Z");

        assertEquals(LocalDate.of(2026, 11, 6), BillChecker.readFrom(List.of(paidPending(seen)), MONDAY, PRAGUE));
    }

    @Test
    void aPaymentNeverBookedStopsWideningTheReadAfterTwoWeeks() {
        var longAgo = Instant.parse("2026-09-01T10:00:00Z");

        assertEquals(
                MONDAY.minusDays(BillChecker.BOOKING_WAIT_DAYS),
                BillChecker.readFrom(List.of(paidPending(longAgo)), MONDAY, PRAGUE));
    }

    private static Bill unpaid() {
        return new Bill(
                1000L, BigDecimal.ONE, "CZK", Instant.parse("2026-11-09T08:00:00Z"), null, null, false, null, 0);
    }

    private static Bill paidPending(Instant seenAt) {
        return new Bill(1000L, BigDecimal.ONE, "CZK", seenAt, "2606928699810906118", null, false, seenAt, 0);
    }
}
