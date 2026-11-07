package cz.insuit.moneta.internal.json;

import org.jspecify.annotations.Nullable;

public record RelatedParties(
        @Nullable Party debtor,
        @Nullable Party creditor,
        @Nullable PartyAccount debtorAccount,
        @Nullable PartyAccount creditorAccount) {}
