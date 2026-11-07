package example.cafe.bill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BillStatusTest {

    @ParameterizedTest
    @CsvSource({"0, UNPAID", "30, PARTLY_PAID", "65, PAID", "65.00, PAID", "130, OVERPAID"})
    void isWhatThePaymentsAddUpTo(String paid, BillStatus status) {
        assertEquals(status, BillStatus.of(new BigDecimal("65.00"), new BigDecimal(paid)));
    }
}
