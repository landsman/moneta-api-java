package cz.insuit.moneta;

import org.jspecify.annotations.Nullable;

record TransactionDetailsJson(
        @Nullable RemittanceInformationJson remittanceInformation,
        @Nullable RelatedPartiesJson relatedParties) {}
