package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.time.Instant;

record DateTimeValue(Instant dateTime) {
    DateTimeValue {
        requireNonNull(dateTime, "dateTime");
    }
}
