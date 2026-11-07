package example.cafe.bill;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

/**
 * A bill the café issued. Its id is its variable symbol: ascending from 1000, never reused, so no two bills share
 * one. Whether it is paid is not stored here but summed from the payments linked to it (see {@link BillView}).
 */
public record Bill(
        @Id @Nullable Long id,
        BigDecimal amount,
        String currency,
        Instant createdAt,
        @Version @Nullable Integer version) {

    static Bill issue(BigDecimal amount, Instant now) {
        return new Bill(null, amount, "CZK", now, null);
    }

    public String variableSymbol() {
        return String.valueOf(requireNonNull(id, "an issued bill has an id"));
    }
}
