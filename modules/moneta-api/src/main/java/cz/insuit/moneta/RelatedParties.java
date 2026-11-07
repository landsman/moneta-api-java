package cz.insuit.moneta;

import org.jspecify.annotations.Nullable;

record RelatedParties(
        @Nullable Party debtor,
        @Nullable Party creditor,
        @Nullable PartyAccount debtorAccount,
        @Nullable PartyAccount creditorAccount) {}
