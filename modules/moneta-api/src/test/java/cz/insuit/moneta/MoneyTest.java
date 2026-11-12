package cz.insuit.moneta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency CZK = Currency.getInstance("CZK");

    @Test
    void equalsDoesNotDependOnHowTheNumberWasWritten() {
        var whole = new Money(new BigDecimal("1500"), CZK);
        var cents = new Money(new BigDecimal("1500.00"), CZK);
        assertEquals(cents, whole);
        assertEquals(cents.hashCode(), whole.hashCode());
    }

    @Test
    void neverRounds() {
        assertEquals(new BigDecimal("0.125"), new Money(new BigDecimal("0.125"), CZK).amount());
        assertEquals(new BigDecimal("100"), new Money(new BigDecimal("100.0"), Currency.getInstance("JPY")).amount());
    }

    @Test
    void negatesWithTheSameCurrency() {
        assertEquals(new Money(new BigDecimal("-12.50"), CZK), new Money(new BigDecimal("12.5"), CZK).negate());
    }

    @Test
    void aCurrencyWithoutMinorDigitsKeepsTheAmountAsWritten() {
        // XXX has getDefaultFractionDigits() == -1, which would otherwise be a negative scale.
        var none = Currency.getInstance("XXX");
        assertEquals(new BigDecimal("12.5"), new Money(new BigDecimal("12.50"), none).amount());
        assertEquals(new BigDecimal("100"), new Money(new BigDecimal("100"), none).amount());
    }

    @Test
    void printsAsAnAmountAndACode() {
        assertEquals("1234.10 CZK", new Money(new BigDecimal("1234.1"), CZK).toString());
    }
}
