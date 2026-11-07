package cz.insuit.moneta;

import org.jspecify.annotations.Nullable;

record RemittanceInformationJson(
        @Nullable StructuredJson structured, @Nullable String unstructured) {}
