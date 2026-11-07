package cz.insuit.moneta;

import org.jspecify.annotations.Nullable;

record RelatedPartiesJson(
        @Nullable PartyJson debtor,
        @Nullable PartyJson creditor,
        @Nullable PartyAccountJson debtorAccount,
        @Nullable PartyAccountJson creditorAccount) {}
