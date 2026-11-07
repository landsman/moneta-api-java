package cz.insuit.moneta;

import org.jspecify.annotations.Nullable;

record TransactionDetails(
        @Nullable RemittanceInformation remittanceInformation,
        @Nullable RelatedParties relatedParties) {}
