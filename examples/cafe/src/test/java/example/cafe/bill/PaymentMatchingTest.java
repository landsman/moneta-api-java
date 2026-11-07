package example.cafe.bill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cz.insuit.moneta.Money;
import cz.insuit.moneta.PaymentSymbols;
import cz.insuit.moneta.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PaymentMatchingTest {

    private static final Instant SEEN = Instant.parse("2026-11-06T10:00:00Z");
    private static final Bill COFFEE = bill(1000, "65.00");
    private static final Bill CAKE = bill(1001, "89.00");

    @Test
    void aPaymentWithTheBillsSymbolAndAmountPaysIt() {
        var payment = pending("t1", "1001", "89");

        assertEquals(
                List.of(new PaymentMatching.Match(CAKE, payment)),
                PaymentMatching.match(List.of(COFFEE, CAKE), List.of(payment)));
    }

    @Test
    void leadingZerosInTheSymbolDoNotMatter() {
        assertEquals(
                1,
                PaymentMatching.match(List.of(COFFEE), List.of(pending("t1", "0001000", "65")))
                        .size());
    }

    @Test
    void aWrongAmountAMissingSymbolOrMoneyOutPaysNothing() {
        var out = new Transaction(
                "out",
                Transaction.Status.BOOKED,
                Transaction.Direction.DEBIT,
                czk("-65"),
                null,
                null,
                null,
                new PaymentSymbols("1000", null, null),
                null,
                null,
                null,
                false,
                "{}");

        assertEquals(
                List.of(),
                PaymentMatching.match(
                        List.of(COFFEE),
                        List.of(pending("short", "1000", "60"), pending("no-symbol", null, "65"), out)));
    }

    @Test
    void aReversalPaysNothing() {
        var reversal = new Transaction(
                "rev",
                Transaction.Status.BOOKED,
                Transaction.Direction.CREDIT,
                czk("65"),
                null,
                null,
                LocalDate.of(2026, 11, 6),
                new PaymentSymbols("1000", null, null),
                null,
                null,
                null,
                true,
                "{}");

        assertEquals(List.of(), PaymentMatching.match(List.of(COFFEE), List.of(reversal)));
    }

    @Test
    void aBillIsPaidOnceEvenIfItIsPaidTwice() {
        var matches = PaymentMatching.match(
                List.of(COFFEE), List.of(pending("t1", "1000", "65"), pending("t2", "1000", "65")));

        assertEquals(
                List.of("t1"),
                matches.stream().map(m -> m.payment().entryReference()).toList());
    }

    @Test
    void aBillPaidByAPendingPaymentIsNotPaidAgainByIt() {
        var paid = COFFEE.paidWith(pending("t1", "1000", "65"), SEEN);

        assertEquals(List.of(), PaymentMatching.match(List.of(paid), List.of(pending("t1", "1000", "65"))));
    }

    @Test
    void theBookedPaymentTakesOverFromThePendingOneKeepingWhenItWasSeen() {
        var paid = COFFEE.paidWith(pending("t1", "1000", "65"), SEEN);
        var booked = booked("1337:20261109:00001:1", "1000", "65", "KAVARNA UCET 1000");

        var matches = PaymentMatching.match(List.of(paid), List.of(booked));
        var updated = matches.getFirst().bill().paidWith(booked, SEEN.plusSeconds(86_400));

        assertEquals("1337:20261109:00001:1", updated.monetaEntryReference());
        assertEquals("KAVARNA UCET 1000", updated.paymentMessage());
        assertEquals(true, updated.paymentBooked());
        assertEquals(SEEN, updated.paidSeenAt());
    }

    @Test
    void aMessageLongerThanTheColumnIsCut() {
        var paid = COFFEE.paidWith(booked("r", "1000", "65", "x".repeat(300)), SEEN);

        assertEquals(Bill.MESSAGE_LENGTH, String.valueOf(paid.paymentMessage()).length());
    }

    private static Bill bill(long id, String amount) {
        return new Bill(id, new BigDecimal(amount), "CZK", SEEN, null, null, false, null, 0);
    }

    private static Transaction pending(String reference, @Nullable String variableSymbol, String amount) {
        return payment(reference, Transaction.Status.PENDING, variableSymbol, amount, "Okamžitá platba");
    }

    private static Transaction booked(String reference, String variableSymbol, String amount, String message) {
        return payment(reference, Transaction.Status.BOOKED, variableSymbol, amount, message);
    }

    private static Transaction payment(
            String reference,
            Transaction.Status status,
            @Nullable String variableSymbol,
            String amount,
            @Nullable String message) {
        return new Transaction(
                reference,
                status,
                Transaction.Direction.CREDIT,
                czk(amount),
                LocalDate.of(2026, 11, 6),
                null,
                null,
                new PaymentSymbols(variableSymbol, null, null),
                message,
                null,
                null,
                false,
                "{}");
    }

    private static Money czk(String amount) {
        return new Money(new BigDecimal(amount), Currency.getInstance("CZK"));
    }
}
