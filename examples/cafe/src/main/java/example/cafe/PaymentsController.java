package example.cafe;

import cz.landsman.moneta.AccessToken;
import cz.landsman.moneta.MonetaClient;
import cz.landsman.moneta.Transaction;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /payments?period=yesterday}, or {@code ?from=2026-11-01&to=2026-11-06} for a range of its own, which
 * wins over the period. Only money in; the counterparty's account and name stay out of the answer.
 */
@RestController
class PaymentsController {

    private final MonetaClient moneta;
    private final AccessToken token;
    private final Clock clock;

    PaymentsController(MonetaClient moneta, AccessToken token, Clock clock) {
        this.moneta = moneta;
        this.token = token;
        this.clock = clock;
    }

    @GetMapping("/payments")
    List<ReceivedPayment> payments(
            @RequestParam(name = "period", defaultValue = "today") String period,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable
                    LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable
                    LocalDate to) {
        var range = range(period, from, to);
        return moneta.accounts(token).stream()
                .flatMap(account -> moneta.transactions(token, account.id(), range.from(), range.to()).stream())
                .filter(t -> t.direction() == Transaction.Direction.CREDIT)
                .map(ReceivedPayment::of)
                .toList();
    }

    private Period.DateRange range(String period, @Nullable LocalDate from, @Nullable LocalDate to) {
        if (from == null && to == null) {
            return Period.parse(period).range(LocalDate.now(clock));
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to go together");
        }
        return new Period.DateRange(from, to);
    }

    /** An unknown period, half a range or one that ends before it starts. */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String badRequest(IllegalArgumentException e) {
        return String.valueOf(e.getMessage());
    }

    /**
     * What the till needs to match a payment: how much, which variable symbol, and whether it is booked yet.
     *
     * @param day the booking day, or the day MONETA took it in while it is pending
     */
    record ReceivedPayment(
            String reference,
            BigDecimal amount,
            String currency,
            @Nullable String variableSymbol,
            boolean booked,
            @Nullable LocalDate day) {

        static ReceivedPayment of(Transaction t) {
            return new ReceivedPayment(
                    t.entryReference(),
                    t.amount().amount(),
                    t.amount().currency().getCurrencyCode(),
                    t.symbols().variable(),
                    t.booked(),
                    t.bookingDate() != null ? t.bookingDate() : t.enteredDate());
        }
    }
}
