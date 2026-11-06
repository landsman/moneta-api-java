package example.cafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;

/** The bill table as schema.sql makes it, on H2 like the app. */
@DataJdbcTest
class BillRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Autowired
    BillRepository bills;

    @Test
    void symbolsAscendFromAThousandAndOnlyUnpaidBillsAreLookedFor() {
        var coffee = bills.save(Bill.issue(new BigDecimal("65"), NOW));
        var cake = bills.save(Bill.issue(new BigDecimal("89"), NOW));
        bills.save(cake.paidBy("t1", NOW));

        // A rolled-back test does not give its ids back, and neither does production: a symbol is never reused.
        assertTrue(Long.parseLong(coffee.variableSymbol()) >= 1000);
        assertEquals(Long.parseLong(coffee.variableSymbol()) + 1, Long.parseLong(cake.variableSymbol()));
        assertEquals(
                List.of(coffee.variableSymbol()),
                bills.findByPaidByIsNullOrderById().stream()
                        .map(Bill::variableSymbol)
                        .toList());
    }

    @Test
    void oneSymbolCannotPayTwoBills() {
        var coffee = bills.save(Bill.issue(new BigDecimal("65"), NOW));
        var cake = bills.save(Bill.issue(new BigDecimal("65"), NOW));
        bills.save(coffee.paidBy("t1", NOW));

        assertThrows(RuntimeException.class, () -> bills.save(cake.paidBy("t1", NOW)));
    }
}
