package example.cafe.bill;

import java.math.BigDecimal;

/**
 * What {@code POST /bills} answers.
 *
 * @param qrPayment the SPAYD string the code holds
 * @param qrCode where the code itself is, as a PNG
 */
record IssuedBill(String variableSymbol, BigDecimal amount, String currency, String qrPayment, String qrCode) {}
