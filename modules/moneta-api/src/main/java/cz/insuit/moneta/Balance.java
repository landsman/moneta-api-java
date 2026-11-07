package cz.insuit.moneta;

import java.time.Instant;
import java.util.List;

/**
 * An account's balance as MONETA reports it. Both amounts are signed, so an overdraft is below zero.
 *
 * @param booked what is booked on the account, MONETA's {@code CLBD}
 * @param available what can be spent, MONETA's {@code CLAV}; a credit line, if the account has one, is in it
 * @param at when MONETA computed the available balance: a balance carries a time of day, unlike a movement
 */
public record Balance(Money booked, Money available, Instant at) {

    static Balance from(String what, BalancesJson json) {
        var booked = ofType(what, json.balances(), BalanceCode.CLBD);
        var available = ofType(what, json.balances(), BalanceCode.CLAV);
        return new Balance(
                CreditDebit.of(booked.creditDebitIndicator()).signed(booked.amount()),
                CreditDebit.of(available.creditDebitIndicator()).signed(available.amount()),
                available.date().dateTime());
    }

    private static BalanceJson ofType(String what, List<BalanceJson> balances, BalanceCode code) {
        return balances.stream()
                .filter(balance ->
                        code.name().equals(balance.type().codeOrProprietary().code()))
                .findFirst()
                .orElseThrow(() -> new MonetaException.InvalidResponse(what + " sent no " + code + " balance", null));
    }
}
