package cz.insuit.moneta;

import org.jspecify.annotations.Nullable;

record TransactionDetailsJson(
        @Nullable ReferencesJson references,
        @Nullable RemittanceInformationJson remittanceInformation,
        @Nullable RelatedPartiesJson relatedParties) {}
