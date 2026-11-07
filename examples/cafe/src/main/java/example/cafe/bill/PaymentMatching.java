package example.cafe.bill;

import cz.insuit.moneta.Transaction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which incoming payments pay which unpaid bills. Pure, so the rule is tested without a bank or a database.
 *
 * <p>A payment pays a bill when all of these hold:
 *
 * <ol>
 *   <li>it is money in, not money out;
 *   <li>it carries a variable symbol;
 *   <li>a bill with that symbol is still looked for: unpaid, or paid by a payment not booked yet;
 *   <li>it pays the bill's amount exactly, in the bill's currency;
 *   <li>if the bill is already paid by a pending payment, this one is booked: MONETA gives a payment a new id once
 *       booked, and this is how the bill learns it.
 * </ol>
 *
 * Each bill takes the first payment that does.
 */
final class PaymentMatching {

    record Match(Bill bill, Transaction payment) {}

    // ponytail: exact amount only; an underpayment or a tip stays unmatched, a person sorts those out.
    static List<Match> match(List<Bill> lookedFor, List<Transaction> payments) {
        Map<String, Bill> billsBySymbol = bySymbol(lookedFor);
        List<Match> matches = new ArrayList<>();

        for (Transaction payment : payments) {
            if (!isMoneyIn(payment)) {
                continue;
            }

            Optional<String> symbol = variableSymbolOf(payment);
            if (symbol.isEmpty()) {
                continue;
            }

            Bill bill = billsBySymbol.get(symbol.get());
            if (bill == null) {
                // No bill has this symbol, or an earlier payment in this round already took it.
                continue;
            }

            if (!paysExactly(payment, bill)) {
                continue;
            }

            if (bill.paid() && !payment.booked()) {
                // Paid already by this pending payment, or another; only its booked version moves it on.
                continue;
            }

            matches.add(new Match(bill, payment));
            billsBySymbol.remove(symbol.get());
        }

        return matches;
    }

    private static Map<String, Bill> bySymbol(List<Bill> bills) {
        Map<String, Bill> bySymbol = new HashMap<>();
        for (Bill bill : bills) {
            bySymbol.put(bill.variableSymbol(), bill);
        }
        return bySymbol;
    }

    private static boolean isMoneyIn(Transaction payment) {
        return payment.direction() == Transaction.Direction.CREDIT;
    }

    /** A bank reads "0001000" and "1000" as the same symbol, so leading zeros are dropped. */
    private static Optional<String> variableSymbolOf(Transaction payment) {
        return Optional.ofNullable(payment.symbols().variable()).map(symbol -> symbol.replaceFirst("^0+(?=\\d)", ""));
    }

    private static boolean paysExactly(Transaction payment, Bill bill) {
        boolean sameAmount = payment.amount().amount().compareTo(bill.amount()) == 0;
        boolean sameCurrency = payment.amount().currency().getCurrencyCode().equals(bill.currency());
        return sameAmount && sameCurrency;
    }

    private PaymentMatching() {}
}
