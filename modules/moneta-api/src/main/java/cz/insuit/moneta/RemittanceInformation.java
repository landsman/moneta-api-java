package cz.insuit.moneta;

import org.jspecify.annotations.Nullable;

record RemittanceInformation(
        @Nullable Structured structured, @Nullable String unstructured) {}
