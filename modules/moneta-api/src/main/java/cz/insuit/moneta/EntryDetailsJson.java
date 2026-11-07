package cz.insuit.moneta;

import org.jspecify.annotations.Nullable;

record EntryDetailsJson(@Nullable TransactionDetailsJson transactionDetails) {}
