package example.cafe.bill;

import cz.landsman.moneta.AccessToken;
import cz.landsman.moneta.MonetaClient;
import cz.landsman.moneta.MonetaException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Looks for the unpaid bills among the incoming payments, every {@code cafe.check-moneta-every}. Reads from the day
 * the oldest unpaid bill was issued, since a payment cannot come before its bill.
 */
@Component
class BillChecker {

    private static final Logger log = LoggerFactory.getLogger(BillChecker.class);

    private final BillRepository bills;
    private final MonetaClient moneta;
    private final AccessToken token;
    private final Clock clock;

    BillChecker(BillRepository bills, MonetaClient moneta, AccessToken token, Clock clock) {
        this.bills = bills;
        this.moneta = moneta;
        this.token = token;
        this.clock = clock;
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

    /** The number of bills it found paid. */
    int check() {
        var unpaid = bills.findByMonetaEntryReferenceIsNullOrderById();
        if (unpaid.isEmpty()) {
            log.debug("No unpaid bills, nothing to look for");
            return 0;
        }
        var oldest = unpaid.stream()
                .map(Bill::createdAt)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        var from = LocalDate.ofInstant(oldest, clock.getZone());
        var today = LocalDate.now(clock);
        var payments = moneta.accounts(token).stream()
                .flatMap(account -> moneta.transactions(token, account.id(), from, today).stream())
                .toList();
        var matches = PaymentMatching.match(unpaid, payments);
        log.info(
                "Checked {} unpaid bills against {} movements since {}: {} paid",
                unpaid.size(),
                payments.size(),
                from,
                matches.size());
        var seenAt = clock.instant();
        for (var match : matches) {
            bills.save(match.bill().paidWith(match.payment().entryReference(), seenAt));
            log.info(
                    "Bill {} paid by {}",
                    match.bill().variableSymbol(),
                    match.payment().entryReference());
        }
        return matches.size();
    }
}
