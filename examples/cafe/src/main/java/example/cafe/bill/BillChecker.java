package example.cafe.bill;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.Account;
import cz.insuit.moneta.MonetaClient;
import cz.insuit.moneta.MonetaException;
import example.cafe.CafeProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Looks for the unpaid bills among the payments into the café's account ({@code cafe.iban}), every {@code
 * cafe.check-moneta-every}. Reads yesterday and today, further back only for a bill paid by a payment not yet
 * booked, and only that account: a payment into another account the token opens pays no bill.
 */
@Component
class BillChecker {

    private static final Logger log = LoggerFactory.getLogger(BillChecker.class);

    private final BillRepository bills;
    private final MonetaClient moneta;
    private final AccessToken token;
    private final Clock clock;
    private final CafeProperties cafe;

    BillChecker(BillRepository bills, MonetaClient moneta, AccessToken token, Clock clock, CafeProperties cafe) {
        this.bills = bills;
        this.moneta = moneta;
        this.token = token;
        this.clock = clock;
        this.cafe = cafe;
    }

    @Scheduled(fixedDelayString = "${cafe.check-moneta-every:PT30S}")
    void checkOnSchedule() {
        try {
            check();
        } catch (MonetaException e) {
            // The next round tries again; a bank that is down is not the café's bug.
            log.warn("Could not check payments: {}", e.getMessage());
        }
    }

    void check() {
        var lookedFor = bills.findByPaymentBookedFalseOrderById();
        if (lookedFor.isEmpty()) {
            log.debug("No bill waits for a payment, nothing to look for");
            return;
        }
        var today = LocalDate.now(clock);
        var from = from(lookedFor, today);
        var account = cafeAccount();
        if (account.isEmpty()) {
            log.error(
                    "The token does not open the café's account {} (cafe.iban), so no bill can be found paid",
                    cafe.iban());
            return;
        }
        var payments = moneta.incoming(token, account.get().id(), from, today);
        var matches = PaymentMatching.match(lookedFor, payments);
        log.info(
                "Checked {} bills unpaid or awaiting booking against {} payments since {}: {} matched",
                lookedFor.size(),
                payments.size(),
                from,
                matches.size());
        var seenAt = clock.instant();
        for (var match : matches) {
            var payment = match.payment();
            bills.save(match.bill().paidWith(payment, seenAt));
            log.info(
                    "Bill {} paid by {} ({})",
                    match.bill().variableSymbol(),
                    payment.entryReference(),
                    payment.booked() ? "booked" : "pending");
        }
    }

    /**
     * Yesterday, for an unpaid bill: a payment made at 23:59:55 is dated yesterday, and the first check after
     * midnight would miss it if it read today alone. Further back for a bill paid by a pending payment: once booked,
     * MONETA dates that payment by the day it was made, and booking can come days later, over a weekend.
     */
    private LocalDate from(List<Bill> lookedFor, LocalDate today) {
        var from = today.minusDays(1);
        for (var bill : lookedFor) {
            var seenAt = bill.paidSeenAt();
            if (seenAt != null) {
                var seenOn = LocalDate.ofInstant(seenAt, clock.getZone());
                if (seenOn.isBefore(from)) {
                    from = seenOn;
                }
            }
        }
        return from;
    }

    /** The account the bills are paid into: the one with cafe.iban, among those the token opens. */
    private Optional<Account> cafeAccount() {
        return moneta.accounts(token).stream()
                .filter(account -> cafe.iban().equals(account.iban()))
                .findFirst();
    }
}
