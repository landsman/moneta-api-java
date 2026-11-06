package example.cafe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class QrPaymentTest {

    private static final String IBAN = "CZ0206000000000000001337";

    @Test
    void writesTheAccountAmountAndSymbolAsABankingAppReadsThem() {
        assertEquals(
                "SPD*1.0*ACC:CZ0206000000000000001337*AM:65*CC:CZK*MSG:KAVARNA UCET 1000*PT:IP*X-VS:1000",
                QrPayment.spayd(IBAN, new BigDecimal("65"), "1000"));
    }

    @Test
    void drawsThePaymentAsAPng() {
        var png = QrPayment.png(IBAN, new BigDecimal("65"), "1000");
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'}, Arrays.copyOf(png, 4));
    }

    @Test
    void refusesWhatAReaderWouldCutRatherThanReject() {
        assertThrows(IllegalArgumentException.class, () -> QrPayment.spayd(IBAN, BigDecimal.ONE, "12345678901"));
        assertThrows(IllegalArgumentException.class, () -> QrPayment.spayd(IBAN, new BigDecimal("10000000"), "1"));
        assertThrows(IllegalArgumentException.class, () -> QrPayment.spayd(IBAN, new BigDecimal("1.005"), "1"));
    }
}
