package cz.insuit.moneta.internal.json;

import org.jspecify.annotations.Nullable;

public record RemittanceInformation(
        @Nullable Structured structured, @Nullable String unstructured) {}
