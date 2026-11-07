package cz.insuit.moneta.internal.json;

import static java.util.Objects.requireNonNull;

import java.time.Instant;

public record DateTimeValue(Instant dateTime) {
    public DateTimeValue {
        requireNonNull(dateTime, "dateTime");
    }
}
