package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * MONETA's {@code {"dateTime": …}}, which its specification calls "date or date and time". Live it has been an
 * instant in UTC; a bare date is read as the start of that day in Prague.
 */
record DateTimeValueJson(String dateTime) {
    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");

    DateTimeValueJson {
        requireNonNull(dateTime, "dateTime");
    }

    Instant instant() {
        return dateTime.length() == "yyyy-MM-dd".length()
                ? LocalDate.parse(dateTime).atStartOfDay(PRAGUE).toInstant()
                : OffsetDateTime.parse(dateTime).toInstant();
    }
}
