package cz.insuit.moneta;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** A pending movement sends {@code {"date": null}} for its booking and value dates (live, 2026-11-06). */
record DateValueJson(@Nullable LocalDate date) {}
