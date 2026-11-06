package example.cafe.bill;

import com.google.zxing.WriterException;
import io.github.janhalasa.spaydqr.model.BankAccount;
import io.github.janhalasa.spaydqr.model.Payment;
import io.github.janhalasa.spaydqr.service.SpaydQrCodeGenerator;
import io.github.janhalasa.spaydqr.service.SpaydSerializer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;

/**
 * A bill as a QR payment, the SPAYD format of https://qr-platba.cz/pro-vyvojare/specifikace-formatu/: the string
 * a Czech banking app reads, and the code it scans. A reader cuts an over-long value rather than refusing it, so
 * a symbol or an amount that does not fit is refused here first: a symbol cut short is a payment that never
 * finds its bill.
 */
final class QrPayment {

    // Large enough to scan from a screen across a counter; a PNG of one colour costs little at any size.
    private static final int PNG_SIZE = 600;

    static String spayd(String iban, BigDecimal amount, String variableSymbol) {
        return SpaydSerializer.serialize(payment(iban, amount, variableSymbol));
    }

    static byte[] png(String iban, BigDecimal amount, String variableSymbol) {
        try {
            return SpaydQrCodeGenerator.generateQrCode(payment(iban, amount, variableSymbol), PNG_SIZE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (WriterException e) {
            throw new IllegalStateException("could not draw the QR code", e);
        }
    }

    private static Payment payment(String iban, BigDecimal amount, String variableSymbol) {
        if (!variableSymbol.matches("\\d{1,10}")) {
            throw new IllegalArgumentException("a variable symbol is 1 to 10 digits");
        }
        if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2 || amount.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("an amount is above 0, at most 9999999.99, with two decimals");
        }
        return Payment.builder()
                .bankAccount(new BankAccount(iban))
                .amount(amount)
                .currencyCode("CZK")
                .variableSymbol(variableSymbol)
                .paymentNote("KAVARNA UCET " + variableSymbol)
                // PT:IP, so a bank that offers instant payments offers one: the café is waiting at the till.
                .instantPayment(true)
                .build();
    }

    private static final BigDecimal MAX = new BigDecimal("9999999.99");

    private QrPayment() {}
}
