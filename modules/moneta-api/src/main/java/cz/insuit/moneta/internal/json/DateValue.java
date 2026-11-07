package cz.insuit.moneta.internal.json;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** A pending movement sends {@code {"date": null}} for its booking and value dates (live, 2026-11-06). */
public record DateValue(@Nullable LocalDate date) {}
