package example.cafe.payment;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.Balance;
import cz.insuit.moneta.MonetaClient;
import example.cafe.CafeProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code GET /payments?period=yesterday}, or {@code ?from=2026-11-01&to=2026-11-06} for a range of its own, which
 * wins over the period. Only money in; the counterparty's account and name stay out of the answer. {@code GET
 * /balance} is the café account's balance.
 */
@RestController
@Tag(name = "Payments")
class PaymentsController {

    private final MonetaClient moneta;
    private final AccessToken token;
    private final Clock clock;
    private final CafeProperties cafe;

    PaymentsController(MonetaClient moneta, AccessToken token, Clock clock, CafeProperties cafe) {
        this.moneta = moneta;
        this.token = token;
        this.clock = clock;
        this.cafe = cafe;
    }

    @GetMapping("/balance")
    @Operation(
            summary = "The café account's balance",
            description = "Booked and available, with the instant MONETA computed it.")
    Balance balance() {
        var account = moneta.accounts(token).stream()
                .filter(a -> cafe.iban().equals(a.iban()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "the token does not open the café's account " + cafe.iban()));
        return moneta.balance(token, account.id());
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
                .flatMap(account -> moneta.incoming(token, account.id(), range.from(), range.to()).stream())
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
