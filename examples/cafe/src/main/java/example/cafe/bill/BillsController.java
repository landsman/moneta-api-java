package example.cafe.bill;

import example.cafe.CafeProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code POST /bills} with {@code {"amount": 1.00}} issues a bill and answers with its QR payment to show the
 * customer; {@code GET /bills} lists them; {@code GET /bills/{variableSymbol}} answers whether one is paid,
 * looking in MONETA first when it is not yet.
 */
@RestController
@Tag(name = "Bills")
class BillsController {

    private static final Logger log = LoggerFactory.getLogger(BillsController.class);

    private final BillRepository bills;
    private final BillChecker checker;
    private final Clock clock;
    private final CafeProperties cafe;

    BillsController(BillRepository bills, BillChecker checker, Clock clock, CafeProperties cafe) {
        this.bills = bills;
        this.checker = checker;
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
        var iban = cafe.iban();
        log.info(
                "Issued bill {}: {}",
                bill.variableSymbol(),
                QrPayment.spayd(iban, bill.amount(), bill.variableSymbol()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Variable-Symbol", bill.variableSymbol())
                .cacheControl(CacheControl.noStore())
                .body(QrPayment.png(iban, bill.amount(), bill.variableSymbol()));
    }

    @GetMapping("/bills")
    @Operation(
            summary = "List every bill",
            description = "Newest first; paid or not, by which payment, first seen when.")
    List<BillView> list() {
        return bills.findAllByOrderByIdDesc().stream().map(BillView::of).toList();
    }

    /**
     * What the till asks after showing the QR code: is this bill paid? An unpaid bill is looked for in MONETA first,
     * so the answer does not wait for the next scheduled round.
     */
    @GetMapping("/bills/{variableSymbol}")
    @Operation(
            summary = "Look up a bill",
            description = "Paid or not; still unpaid, it is first looked for among the payments into the café's"
                    + " account.")
    BillView lookUp(@Parameter(example = "1000") @PathVariable("variableSymbol") long variableSymbol) {
        var bill = bills.findById(variableSymbol).orElseThrow(() -> noBill(variableSymbol));
        if (!bill.paid()) {
            checker.check();
            bill = bills.findById(variableSymbol).orElseThrow(() -> noBill(variableSymbol));
        }
        return BillView.of(bill);
    }

    private static ResponseStatusException noBill(long variableSymbol) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "no bill " + variableSymbol);
    }
}
