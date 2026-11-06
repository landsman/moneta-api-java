package example.cafe.bill;

import cz.landsman.moneta.AccessToken;
import cz.landsman.moneta.MonetaClient;
import example.cafe.CafeProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /bills} with {@code {"amount": 1.00}} issues a bill and answers with its QR payment to show the
 * customer; {@code GET /bills} lists them, paid or not; {@code POST /bills/check} looks for payments now
 * rather than at the next scheduled round.
 */
@RestController
@Tag(name = "Bills")
class BillsController {

    private static final Logger log = LoggerFactory.getLogger(BillsController.class);

    private final BillRepository bills;
    private final BillChecker checker;
    private final MonetaClient moneta;
    private final AccessToken token;
    private final Clock clock;
    private final CafeProperties cafe;

    BillsController(
            BillRepository bills,
            BillChecker checker,
            MonetaClient moneta,
            AccessToken token,
            Clock clock,
            CafeProperties cafe) {
        this.bills = bills;
        this.checker = checker;
        this.moneta = moneta;
        this.token = token;
        this.clock = clock;
        this.cafe = cafe;
    }

    /**
     * Answers with the bill's QR payment, the PNG the customer's banking app scans, so the till shows it at once.
     * Never cached: the code holds the café's IBAN as MONETA reports it now.
     */
    @PostMapping(path = "/bills", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(
            summary = "Issue a bill",
            description = "Answers with its QR payment as a PNG; the variable symbol, ascending from 1000, is in the"
                    + " X-Variable-Symbol header.")
    ResponseEntity<byte[]> issue(@RequestBody NewBill request) {
        var amount = request.amount();
        if (amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("amount is above 0, in crowns with at most two decimals");
        }
        var bill = bills.save(Bill.issue(amount, clock.instant()));
        var iban = cafeIban();
        log.info(
                "Issued bill {}: {}",
                bill.variableSymbol(),
                QrPayment.spayd(iban, bill.amount(), bill.variableSymbol()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Variable-Symbol", bill.variableSymbol())
                .cacheControl(CacheControl.noStore())
                .body(QrPayment.png(iban, bill.amount(), bill.variableSymbol()));
    }

    /** {@code cafe.iban} when set; otherwise the first account the token sees, asked of MONETA each time. */
    private String cafeIban() {
        var configured = cafe.iban();
        if (configured != null) {
            return configured;
        }
        var iban = moneta.accounts(token).getFirst().iban();
        if (iban == null) {
            throw new IllegalStateException("MONETA sent the café's account without an IBAN");
        }
        return iban;
    }

    @GetMapping("/bills")
    @Operation(
            summary = "List every bill, newest first",
            description = "Paid or not, by which payment, first seen when.")
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
}
