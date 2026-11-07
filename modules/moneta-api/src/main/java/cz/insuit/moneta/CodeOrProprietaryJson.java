package cz.insuit.moneta;

import org.jspecify.annotations.Nullable;

/** A code MONETA sends may be one the library does not read, so it stays a string there. */
record CodeOrProprietaryJson(@Nullable String code) {}
