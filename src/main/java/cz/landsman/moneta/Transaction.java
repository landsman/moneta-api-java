package cz.landsman.moneta;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * One movement on an account.
 *
 * @param entryReference MONETA's id of the movement
 * @param status {@code BOOK} once booked; anything else may still change
 * @param amount always positive, as MONETA sends it; {@link #signedAmount()} applies the direction
 * @param raw the movement exactly as MONETA sent it, as JSON, for whatever this record does not map
 */
public record Transaction(
        String entryReference,
        String status,
        Direction direction,
        BigDecimal amount,
        String currency,
        @Nullable LocalDate bookingDate,
        @Nullable LocalDate valueDate,
        PaymentSymbols symbols,
        String raw) {

    /** Whether the movement is booked and will not change. */
    public boolean booked() {
        return "BOOK".equals(status);
    }

    /** Positive for money in, negative for money out. */
    public BigDecimal signedAmount() {
        return direction == Direction.DEBIT ? amount.negate() : amount;
    }

    public enum Direction {
        CREDIT,
        DEBIT
    }

    static Transaction from(JsonNode node) {
        var indicator = node.path("creditDebitIndicator").asString();
        var direction = switch (indicator) {
            case "CRDT" -> Direction.CREDIT;
            case "DBIT" -> Direction.DEBIT;
            default -> throw new IllegalArgumentException("Unknown creditDebitIndicator: " + indicator);
        };
        var reference = node.path("entryDetails").path("transactionDetails").path("remittanceInformation")
                .path("structured").path("creditorReferenceInformation").path("reference");
        return new Transaction(
                node.path("entryReference").asString(),
                node.path("status").asString(),
                direction,
                node.path("amount").path("value").decimalValue(),
                node.path("amount").path("currency").asString(),
                date(node.path("bookingDate")),
                date(node.path("valueDate")),
                PaymentSymbols.parse(reference.isString() ? reference.stringValue() : null),
                node.toString());
    }

    private static @Nullable LocalDate date(JsonNode node) {
        var date = node.path("date");
        return date.isString() ? LocalDate.parse(date.stringValue()) : null;
    }
}
