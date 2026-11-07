package example.cafe.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cz.insuit.moneta.Money;
import cz.insuit.moneta.PaymentSymbols;
import cz.insuit.moneta.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PaymentRecordingTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 6);
    private static final Instant SEEN = Instant.parse("2026-11-06T21:02:01Z");

    @Test
    void aPaymentNotSeenBeforeIsRecorded() {
        var payment = pending("2600000000000000001", "1007", "4040404040/0800");

        assertEquals(
                List.of(new PaymentRecording.Step.Record(payment)),
                PaymentRecording.steps(List.of(payment), Set.of(), List.of()));
    }

    @Test
    void aPaymentRecordedUnderItsIdIsLeftAlone() {
        var payment = pending("2600000000000000001", "1007", "4040404040/0800");

        assertEquals(List.of(), PaymentRecording.steps(List.of(payment), Set.of("2600000000000000001"), List.of()));
    }

    @Test
    void theBookedVersionBooksThePendingRowEvenWithTheAccountWrittenDifferently() {
        // Pending MONETA wrote the payer's account 4040404040/0800, booked 0 4040404040/0800 (live, 2026-11-07).
        var row = Payment.of(pending("2600000000000000001", "1007", "4040404040/0800"), SEEN, 1007L);
        var booked = booked("0000001337:20261109:00001:1", "1007", "0 4040404040/0800");

        assertEquals(
                List.of(new PaymentRecording.Step.Book(row, booked)),
                PaymentRecording.steps(List.of(booked), Set.of(), List.of(row)));
    }

    @Test
    void aBookedPaymentWithNoPendingRowIsRecordedAsNew() {
        var booked = booked("0000001337:20261109:00002:2", "1007", "0 4040404040/0800");

        assertEquals(
                List.of(new PaymentRecording.Step.Record(booked)),
                PaymentRecording.steps(List.of(booked), Set.of(), List.of()));
    }

    @Test
    void twoIdenticalPaymentsStayTwoWhenBooked() {
        // Paid twice from one account: each booked payment takes one pending row, none is lost.
        var first = Payment.of(pending("p1", "1007", "4040404040/0800"), SEEN, 1007L);
        var second = Payment.of(pending("p2", "1007", "4040404040/0800"), SEEN, 1007L);
        var bookedFirst = booked("b1", "1007", "0 4040404040/0800");
        var bookedSecond = booked("b2", "1007", "0 4040404040/0800");

        assertEquals(
                List.of(
                        new PaymentRecording.Step.Book(first, bookedFirst),
                        new PaymentRecording.Step.Book(second, bookedSecond)),
                PaymentRecording.steps(List.of(bookedFirst, bookedSecond), Set.of(), List.of(first, second)));
    }

    @Test
    void theBookedVersionBooksThePendingRowWhateverDayItCarries() {
        // Pending is dated by enteredDate, booked by valueDate; for some payments they may differ.
        var row = Payment.of(pending("p1", "1007", "4040404040/0800"), SEEN, 1007L);
        var booked = new Transaction(
                "b1",
                Transaction.Status.BOOKED,
                Transaction.Direction.CREDIT,
                czk("1"),
                null,
                FRIDAY.plusDays(3),
                FRIDAY.plusDays(1),
                new PaymentSymbols("1007", null, null),
                "KAVARNA UCET 1007",
                "Novak, Jan",
                "0 4040404040/0800",
                false,
                false,
                "{}");

        assertEquals(
                List.of(new PaymentRecording.Step.Book(row, booked)),
                PaymentRecording.steps(List.of(booked), Set.of(), List.of(row)));
    }

    @Test
    void aReversalIsNotRecorded() {
        var reversal = new Transaction(
                "rev",
                Transaction.Status.BOOKED,
                Transaction.Direction.CREDIT,
                czk("1"),
                null,
                null,
                FRIDAY,
                new PaymentSymbols("1007", null, null),
                null,
                null,
                "0 4040404040/0800",
                true,
                false,
                "{}");

        assertEquals(List.of(), PaymentRecording.steps(List.of(reversal), Set.of(), List.of()));
    }

    @Test
    void anAccountIsOneValueHoweverItIsWritten() {
        assertEquals("4040404040/0800", PaymentRecording.accountKey("0 4040404040/0800"));
        assertEquals("192000145399/0800", PaymentRecording.accountKey("19-2000145399/0800"));
        assertEquals("192000145399/0800", PaymentRecording.accountKey("19 2000145399/0800"));
    }

    private static Transaction pending(String reference, @Nullable String symbol, String account) {
        return new Transaction(
                reference,
                Transaction.Status.PENDING,
                Transaction.Direction.CREDIT,
                czk("1"),
                FRIDAY,
                null,
                null,
                new PaymentSymbols(symbol, null, null),
                "Okamžitá platba",
                null,
                account,
                false,
                false,
                "{}");
    }

    private static Transaction booked(String reference, @Nullable String symbol, String account) {
        return new Transaction(
                reference,
                Transaction.Status.BOOKED,
                Transaction.Direction.CREDIT,
                czk("1"),
                null,
                FRIDAY.plusDays(3),
                FRIDAY,
                new PaymentSymbols(symbol, null, null),
                "KAVARNA UCET 1007",
                "Novak, Jan",
                account,
                false,
                false,
                "{}");
    }

    private static Money czk(String amount) {
        return new Money(new BigDecimal(amount), Currency.getInstance("CZK"));
    }
}
