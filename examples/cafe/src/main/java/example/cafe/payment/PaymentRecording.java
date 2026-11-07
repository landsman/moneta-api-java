package example.cafe.payment;

import cz.insuit.moneta.Transaction;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What to do with the payments MONETA answered, given what is recorded already. Pure, so the rule is tested
 * without a bank or a database.
 *
 * <p>For each payment, in order:
 *
 * <ol>
 *   <li>a reversal is not recorded: money returned or a payment undone is no payment to the café;
 *   <li>one already recorded under its id is left alone;
 *   <li>a booked one that a recorded pending payment matches (same amount, symbol, payer's account and day) books
 *       that payment: MONETA gives a payment a new id once booked, and this is how the row learns it;
 *   <li>any other is recorded as new.
 * </ol>
 */
final class PaymentRecording {

    sealed interface Step {
        /** A payment not seen before. */
        record Record(Transaction payment) implements Step {}

        /** The booked version of a payment recorded while pending. */
        record Book(Payment pending, Transaction booked) implements Step {}
    }

    static List<Step> steps(List<Transaction> answered, Set<String> recordedReferences, List<Payment> pending) {
        List<Step> steps = new ArrayList<>();
        Set<Payment> bookedThisRound = new HashSet<>();

        for (Transaction payment : answered) {
            if (payment.reversed()) {
                continue;
            }

            if (recordedReferences.contains(payment.entryReference())) {
                continue;
            }

            if (payment.booked()) {
                Payment earlier = pendingVersionOf(payment, pending, bookedThisRound);
                if (earlier != null) {
                    steps.add(new Step.Book(earlier, payment));
                    bookedThisRound.add(earlier);
                    continue;
                }
            }

            steps.add(new Step.Record(payment));
        }

        return steps;
    }

    /** The day a payment was made: its value date once booked, the day MONETA took it in while pending. */
    static @Nullable LocalDate madeOn(Transaction payment) {
        return payment.valueDate() != null ? payment.valueDate() : payment.enteredDate();
    }

    /** A bank reads "0001000" and "1000" as the same symbol, so leading zeros are dropped. */
    static @Nullable String variableSymbolOf(Transaction payment) {
        var symbol = payment.symbols().variable();
        return symbol == null ? null : symbol.replaceFirst("^0+(?=\\d)", "");
    }

    /**
     * An account as one value however MONETA wrote it: pending it sends {@code 4040404040/0800}, booked
     * {@code 0 4040404040/0800}, a zero prefix and a space (live, 2026-11-07). Spaces, the dash a prefix is
     * written with, and leading zeros go.
     */
    static @Nullable String accountKey(@Nullable String account) {
        if (account == null) {
            return null;
        }
        var compact = account.replace(" ", "").replace("-", "");
        var slash = compact.indexOf('/');
        var number = slash < 0 ? compact : compact.substring(0, slash);
        var bank = slash < 0 ? "" : compact.substring(slash);
        return number.replaceFirst("^0+(?=\\d)", "") + bank;
    }

    private static @Nullable Payment pendingVersionOf(
            Transaction booked, List<Payment> pending, Set<Payment> alreadyBooked) {
        for (Payment candidate : pending) {
            if (alreadyBooked.contains(candidate)) {
                continue;
            }
            boolean sameAmount = candidate.amount().compareTo(booked.amount().amount()) == 0;
            boolean sameSymbol = Objects.equals(candidate.variableSymbol(), variableSymbolOf(booked));
            boolean samePayer = Objects.equals(
                    accountKey(candidate.counterpartyAccount()), accountKey(booked.counterpartyAccount()));
            boolean sameDay = Objects.equals(candidate.madeOn(), madeOn(booked));
            if (sameAmount && sameSymbol && samePayer && sameDay) {
                return candidate;
            }
        }
        return null;
    }

    private PaymentRecording() {}
}
