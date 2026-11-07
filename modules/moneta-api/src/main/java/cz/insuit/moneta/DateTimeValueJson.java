package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.time.Instant;

record DateTimeValueJson(Instant dateTime) {
    DateTimeValueJson {
        requireNonNull(dateTime, "dateTime");
    }
}
