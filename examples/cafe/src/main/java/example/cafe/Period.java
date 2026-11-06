package example.cafe;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The periods a café asks about. Whole days only: MONETA dates a movement but gives it no time, so "the last
 * hour" cannot be answered from its data.
 */
enum Period {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    LAST_WEEK,
    LAST_7_DAYS,
    THIS_MONTH;

    /** {@code last-week} or {@code LAST_WEEK}. */
    static Period parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("period is one of " + names(), e);
        }
    }

    private static String names() {
        return Arrays.stream(values())
                .map(p -> p.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                .collect(Collectors.joining(", "));
    }

    /** Both ends inclusive; weeks start on Monday. */
    DateRange range(LocalDate today) {
        return switch (this) {
            case TODAY -> new DateRange(today, today);
            case YESTERDAY -> new DateRange(today.minusDays(1), today.minusDays(1));
            case THIS_WEEK -> new DateRange(today.with(DayOfWeek.MONDAY), today);
            case LAST_WEEK -> {
                var monday = today.with(DayOfWeek.MONDAY).minusWeeks(1);
                yield new DateRange(monday, monday.plusDays(6));
            }
            case LAST_7_DAYS -> new DateRange(today.minusDays(6), today);
            case THIS_MONTH -> new DateRange(today.withDayOfMonth(1), today);
        };
    }

    record DateRange(LocalDate from, LocalDate to) {}
}
