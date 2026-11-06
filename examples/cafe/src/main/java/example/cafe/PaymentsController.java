package example.cafe;

import cz.landsman.moneta.AccessToken;
import cz.landsman.moneta.MonetaClient;
import cz.landsman.moneta.Transaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /payments?period=yesterday}, or {@code ?from=2026-11-01&to=2026-11-06} for a range of its own, which
 * wins over the period. Only money in; the counterparty's account and name stay out of the answer.
 */
@RestController
@Tag(name = "Payments", description = "Incoming payments on the café's account, by period or range")
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
    @Operation(
            summary = "Incoming payments",
            description = "Whole days only: MONETA dates a payment, it gives no time.")
    List<ReceivedPayment> payments(
            @Parameter(
                            description =
                                    "today, yesterday, this-week, last-week, last-7-days or this-month; ignored with from and to")
                    @RequestParam(name = "period", defaultValue = "today")
                    String period,
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
}
