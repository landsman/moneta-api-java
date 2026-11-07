package cz.insuit.moneta.internal.json;

import org.jspecify.annotations.Nullable;

/** A code MONETA sends may be one the library does not read, so it stays a string there. */
public record CodeOrProprietary(@Nullable String code) {}
