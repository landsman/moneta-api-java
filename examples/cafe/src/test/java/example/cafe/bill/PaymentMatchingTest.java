package example.cafe.bill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cz.landsman.moneta.Money;
import cz.landsman.moneta.PaymentSymbols;
import cz.landsman.moneta.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PaymentMatchingTest {

    private static final Bill COFFEE = bill(1000, "65.00");
    private static final Bill CAKE = bill(1001, "89.00");

    @Test
    void aPaymentWithTheBillsSymbolAndAmountPaysIt() {
        var matches = PaymentMatching.match(List.of(COFFEE, CAKE), List.of(payment("t1", "1001", "89")));

        assertEquals(List.of(new PaymentMatching.Match(CAKE, payment("t1", "1001", "89"))), matches);
    }

    @Test
    void leadingZerosInTheSymbolDoNotMatter() {
        assertEquals(
                1,
                PaymentMatching.match(List.of(COFFEE), List.of(payment("t1", "0001000", "65")))
                        .size());
    }

    @Test
    void aWrongAmountAMissingSymbolOrMoneyOutPaysNothing() {
        var payments = List.of(
                payment("short", "1000", "60"),
                payment("no-symbol", null, "65"),
                new Transaction(
                        "out",
                        Transaction.Status.BOOKED,
                        Transaction.Direction.DEBIT,
                        czk("-65"),
                        null,
                        null,
                        null,
                        new PaymentSymbols("1000", null, null),
                        "{}"));

        assertEquals(List.of(), PaymentMatching.match(List.of(COFFEE), payments));
    }

    @Test
    void aBillIsPaidOnceEvenIfItIsPaidTwice() {
        var matches = PaymentMatching.match(
                List.of(COFFEE), List.of(payment("t1", "1000", "65"), payment("t2", "1000", "65")));

        assertEquals(
                List.of("t1"),
                matches.stream().map(m -> m.payment().entryReference()).toList());
    }

    private static Bill bill(long id, String amount) {
        return new Bill(id, new BigDecimal(amount), "CZK", Instant.parse("2026-11-06T10:00:00Z"), null, null, 0);
    }

    private static Transaction payment(String reference, @Nullable String variableSymbol, String amount) {
        return new Transaction(
                reference,
                Transaction.Status.PENDING,
                Transaction.Direction.CREDIT,
                czk(amount),
                LocalDate.of(2026, 11, 6),
                null,
                null,
                new PaymentSymbols(variableSymbol, null, null),
                "{}");
    }

    private static Money czk(String amount) {
        return new Money(new BigDecimal(amount), Currency.getInstance("CZK"));
    }
}
