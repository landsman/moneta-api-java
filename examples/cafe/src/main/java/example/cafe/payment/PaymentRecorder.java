package example.cafe.payment;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.Account;
import cz.insuit.moneta.MonetaClient;
import cz.insuit.moneta.MonetaException;
import example.cafe.CafeProperties;
import example.cafe.bill.BillRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Records the payments into the café's account ({@code cafe.iban}), every {@code cafe.check-moneta-every}: a new
 * one as a row linked to the bill its variable symbol names, a booked one onto the row its pending version made.
 * A payment no bill claims stays unlinked, for a person to assign.
 */
@Component
public class PaymentRecorder {

    private static final Logger log = LoggerFactory.getLogger(PaymentRecorder.class);

    /** How long a pending payment waits for its booked version before the read stops reaching back for it. */
    static final int BOOKING_WAIT_DAYS = 14;

    private final PaymentRepository payments;
    private final PaymentWrites writes;
    private final BillRepository bills;
    private final MonetaClient moneta;
    private final AccessToken token;
    private final Clock clock;
    private final CafeProperties cafe;

    PaymentRecorder(
            PaymentRepository payments,
            PaymentWrites writes,
            BillRepository bills,
            MonetaClient moneta,
            AccessToken token,
            Clock clock,
            CafeProperties cafe) {
        this.payments = payments;
        this.writes = writes;
        this.bills = bills;
        this.moneta = moneta;
        this.token = token;
        this.clock = clock;
        this.cafe = cafe;
    }

    @Scheduled(fixedDelayString = "${cafe.check-moneta-every:PT30S}")
    void recordOnSchedule() {
        try {
            record();
        } catch (MonetaException e) {
            // The next round tries again; a bank that is down is not the café's bug.
            log.warn("Could not read payments: {}", e.getMessage());
        }
    }

    public void record() {
        var account = cafeAccount();
        if (account.isEmpty()) {
            log.error("The token does not open the café's account {} (cafe.iban)", cafe.iban());
            return;
        }
        var pending = payments.findByBookedFalse();
        var today = LocalDate.now(clock);
        var from = readFrom(pending, today);
        var answered = moneta.incoming(token, account.get().id(), from, today);
        var references = answered.stream().map(t -> t.entryReference()).collect(Collectors.toSet());
        // A shortcut that saves writing what is known; the writes below decide, should another round race this one.
        var recorded = payments.findByMonetaEntryReferenceIn(references).stream()
                .map(Payment::monetaEntryReference)
                .collect(Collectors.toSet());
        var seenAt = clock.instant();
        int added = 0;
        int booked = 0;
        for (var step : PaymentRecording.steps(answered, recorded, pending)) {
            switch (step) {
                case PaymentRecording.Step.Record(var payment) -> {
                    var row = Payment.of(payment, seenAt, billFor(PaymentRecording.variableSymbolOf(payment)));
                    if (!writes.record(row)) {
                        log.debug("Payment {} was recorded by another round", row.monetaEntryReference());
                        continue;
                    }
                    added++;
                    log.info(
                            "Recorded payment {} of {} {}, {}",
                            row.monetaEntryReference(),
                            row.amount(),
                            row.currency(),
                            row.billId() != null ? "for bill " + row.billId() : "for no bill");
                }
                case PaymentRecording.Step.Book(var earlier, var payment) -> {
                    if (!writes.book(earlier, payment)) {
                        log.debug("Payment {} was booked by another round", earlier.monetaEntryReference());
                        continue;
                    }
                    booked++;
                    log.info("Payment {} booked as {}", earlier.monetaEntryReference(), payment.entryReference());
                }
            }
        }
        log.info("Read {} payments since {}: {} new, {} booked", answered.size(), from, added, booked);
    }

    /**
     * Yesterday, so a payment made at 23:59:55 is not missed by the first round after midnight. Further back for a
     * pending payment, to the day before it was made: once booked, MONETA dates it by that day, and booking can come days
     * later. Never more than {@link #BOOKING_WAIT_DAYS} back, or a payment never booked would widen every read.
     */
    static LocalDate readFrom(List<Payment> pending, LocalDate today) {
        var from = today.minusDays(1);
        var limit = today.minusDays(BOOKING_WAIT_DAYS);
        for (var payment : pending) {
            var madeOn = payment.madeOn();
            if (madeOn == null) {
                continue;
            }
            // A day before the entered date: MONETA filters the booked payment by its value date, which is not
            // shown to equal the entered date for every kind of payment.
            var earliest = madeOn.minusDays(1);
            if (earliest.isBefore(from)) {
                from = earliest.isBefore(limit) ? limit : earliest;
            }
        }
        return from;
    }

    /** The bill a variable symbol names, if the café issued one under it. */
    private @Nullable Long billFor(@Nullable String variableSymbol) {
        if (variableSymbol == null || !variableSymbol.matches("\\d{1,18}")) {
            return null;
        }
        var id = Long.valueOf(variableSymbol);
        return bills.existsById(id) ? id : null;
    }

    private Optional<Account> cafeAccount() {
        return moneta.accounts(token).stream()
                .filter(account -> cafe.iban().equals(account.iban()))
                .findFirst();
    }
}
