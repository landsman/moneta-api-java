package example.cafe;

import cz.landsman.moneta.Transaction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Which incoming payments pay which unpaid bills. Pure, so the rule is tested without a bank or a database. */
final class PaymentMatching {

    record Match(Bill bill, Transaction payment) {}

    /**
     * A payment pays a bill when it is money in, its variable symbol is the bill's, and the amount and currency
     * are the bill's exactly. Each bill is paid once, by the first such payment.
     */
    // ponytail: exact amount only; an underpayment or a tip stays unmatched, a person sorts those out.
    static List<Match> match(List<Bill> unpaid, List<Transaction> payments) {
        var byVariableSymbol = new HashMap<String, Bill>();
        for (var bill : unpaid) {
            byVariableSymbol.put(bill.variableSymbol(), bill);
        }
        var matches = new ArrayList<Match>();
        for (var payment : payments) {
            var symbol = normalised(payment.symbols().variable());
            var bill = symbol == null ? null : byVariableSymbol.get(symbol);
            if (payment.direction() == Transaction.Direction.CREDIT
                    && bill != null
                    && payment.amount().amount().compareTo(bill.amount()) == 0
                    && payment.amount().currency().getCurrencyCode().equals(bill.currency())) {
                matches.add(new Match(bill, payment));
                byVariableSymbol.remove(symbol);
            }
        }
        return matches;
    }

    /** A bank reads "0001000" and "1000" as the same symbol, so leading zeros do not decide it. */
    private static @Nullable String normalised(@Nullable String symbol) {
        return symbol == null ? null : symbol.replaceFirst("^0+(?=\\d)", "");
    }

    private PaymentMatching() {}
}
