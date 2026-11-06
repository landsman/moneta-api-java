package example.cafe;

import cz.landsman.moneta.AccessToken;
import cz.landsman.moneta.MonetaClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code POST /bills} with {@code {"amount": 1.00}} issues a bill and answers with its variable symbol and the QR payment to show
 * the customer; {@code GET /bills} lists them, paid or not; {@code POST /bills/check} looks for payments now
 * rather than at the next scheduled round.
 */
@RestController
@Tag(name = "Bills", description = "Bills the café issues, and whether they are paid")
class BillsController {

    private final BillRepository bills;
    private final BillChecker checker;
    private final MonetaClient moneta;
    private final AccessToken token;
    private final Clock clock;

    BillsController(BillRepository bills, BillChecker checker, MonetaClient moneta, AccessToken token, Clock clock) {
        this.bills = bills;
        this.checker = checker;
        this.moneta = moneta;
        this.token = token;
        this.clock = clock;
    }

    @PostMapping("/bills")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue a bill", description = "Its variable symbol is the next one, ascending from 1000.")
    IssuedBill issue(@RequestBody NewBill request) {
        var amount = request.amount();
        if (amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("amount is above 0, in crowns with at most two decimals");
        }
        var bill = bills.save(Bill.issue(amount, clock.instant()));
        return new IssuedBill(
                bill.variableSymbol(),
                bill.amount(),
                bill.currency(),
                QrPayment.spayd(cafeIban(), bill.amount(), bill.variableSymbol()),
                "/bills/" + bill.variableSymbol() + "/qr.png");
    }

    /** The code to show the customer: their banking app scans it and pre-fills the payment. */
    @GetMapping(path = "/bills/{variableSymbol}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "The bill's QR payment", description = "The PNG a banking app scans to pay it.")
    byte[] qr(@Parameter(example = "1000") @PathVariable("variableSymbol") long variableSymbol) {
        var bill = bills.findById(variableSymbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no bill " + variableSymbol));
        return QrPayment.png(cafeIban(), bill.amount(), bill.variableSymbol());
    }

    private String cafeIban() {
        var iban = moneta.accounts(token).getFirst().iban();
        if (iban == null) {
            throw new IllegalStateException("MONETA sent the café's account without an IBAN");
        }
        return iban;
    }

    @GetMapping("/bills")
    @Operation(summary = "Every bill, newest first", description = "Paid or not, by which payment, first seen when.")
    List<BillView> list() {
        return bills.findAllByOrderByIdDesc().stream().map(BillView::of).toList();
    }

    @PostMapping("/bills/check")
    @Operation(
            summary = "Look for payments now",
            description = "Rather than at the next scheduled round; answers how many bills it found paid.")
    Map<String, Integer> check() {
        return Map.of("paid", checker.check());
    }

    /** @param amount in crowns, at most two decimals */
    record NewBill(@Schema(example = "1.00") BigDecimal amount) {}

    /**
     * @param qrPayment the SPAYD string the code holds
     * @param qrCode where the code itself is, as a PNG
     */
    record IssuedBill(String variableSymbol, BigDecimal amount, String currency, String qrPayment, String qrCode) {}

    record BillView(
            String variableSymbol,
            BigDecimal amount,
            String currency,
            Instant issuedAt,
            boolean paid,
            @Nullable String paidBy,
            @Nullable Instant paidSeenAt) {

        static BillView of(Bill bill) {
            return new BillView(
                    bill.variableSymbol(),
                    bill.amount(),
                    bill.currency(),
                    bill.createdAt(),
                    bill.paid(),
                    bill.paidBy(),
                    bill.paidSeenAt());
        }
    }
}
