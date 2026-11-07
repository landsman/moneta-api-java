package cz.insuit.moneta.internal.json;

import org.jspecify.annotations.Nullable;

public record TransactionDetails(
        @Nullable RemittanceInformation remittanceInformation,
        @Nullable RelatedParties relatedParties) {}
