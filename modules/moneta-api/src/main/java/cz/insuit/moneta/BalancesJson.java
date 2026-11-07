package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.util.List;

/** {@code GET …/accounts/{id}/balance}: one entry per type, {@code CLBD} and {@code CLAV} (live, 2026-11-07). */
record BalancesJson(List<BalanceJson> balances) {
    BalancesJson {
        requireNonNull(balances, "balances");
    }
}
