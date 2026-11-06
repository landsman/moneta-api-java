package example.cafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PeriodTest {

    /** A Friday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    @ParameterizedTest
    @CsvSource({
        "today,       2026-11-06, 2026-11-06",
        "yesterday,   2026-11-05, 2026-11-05",
        "this-week,   2026-11-02, 2026-11-06",
        "last-week,   2026-10-26, 2026-11-01",
        "last-7-days, 2026-10-31, 2026-11-06",
        "this-month,  2026-11-01, 2026-11-06",
    })
    void coversTheWholeDaysOfEachPeriod(String period, LocalDate from, LocalDate to) {
        assertEquals(new Period.DateRange(from, to), Period.parse(period).range(TODAY));
    }

    @Test
    void lastWeekOnAMondayIsTheWeekThatJustEnded() {
        assertEquals(
                new Period.DateRange(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)),
                Period.LAST_WEEK.range(LocalDate.of(2026, 11, 2)));
    }

    @Test
    void refusesAPeriodItDoesNotKnow() {
        assertThrows(IllegalArgumentException.class, () -> Period.parse("last-hour"));
    }
}
