package cz.landsman.moneta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PaymentSymbolsTest {

    @Test
    void readsSpaceSeparatedSymbols() {
        assertEquals(new PaymentSymbols("123456", "0308", "0001"), PaymentSymbols.parse("VS:123456 KS:0308 SS:0001"));
    }

    @Test
    void readsCommaSeparatedSymbols() {
        assertEquals(
                new PaymentSymbols("123456", "456789", "879213546"),
                PaymentSymbols.parse("VS:123456,KS:456789,SS:879213546"));
    }

    @Test
    void leavesOutWhatIsMissing() {
        assertEquals(new PaymentSymbols("42", null, null), PaymentSymbols.parse("vs: 42"));
        assertEquals(new PaymentSymbols(null, null, null), PaymentSymbols.parse(null));
        assertEquals(new PaymentSymbols(null, null, null), PaymentSymbols.parse("payment for invoice"));
    }

    @Test
    void doesNotReadASymbolInsideAnotherWord() {
        assertEquals(new PaymentSymbols(null, null, null), PaymentSymbols.parse("ABCVS:123"));
    }
}
