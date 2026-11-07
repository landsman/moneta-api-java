package example.cafe.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * The body of {@code PUT /payments/recorded/{id}/bill}.
 *
 * @param variableSymbol the bill the payment pays; null to unlink it
 */
record PaymentAssignment(@Schema(example = "1000") @Nullable String variableSymbol) {}
