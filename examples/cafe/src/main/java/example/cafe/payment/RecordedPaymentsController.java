package example.cafe.payment;

import example.cafe.bill.BillRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The payments the café recorded, and the one thing a person does with them: link a payment no bill claimed, a
 * mistyped symbol or none at all, to the bill it pays.
 */
@RestController
@Tag(name = "Payments")
class RecordedPaymentsController {

    private final PaymentRepository payments;
    private final BillRepository bills;

    RecordedPaymentsController(PaymentRepository payments, BillRepository bills) {
        this.payments = payments;
        this.bills = bills;
    }

    @GetMapping("/payments/recorded")
    @Operation(
            summary = "List recorded payments",
            description = "Newest first; with unassigned=true only those no bill claimed, which a person sorts out.")
    List<Payment> list(
            @Parameter(description = "only payments linked to no bill")
                    @RequestParam(name = "unassigned", defaultValue = "false")
                    boolean unassigned) {
        return unassigned
                ? payments.findByBillIdIsNullOrderByFirstSeenAtDesc()
                : payments.findAllByOrderByFirstSeenAtDesc();
    }

    @PutMapping("/payments/recorded/{id}/bill")
    @Operation(
            summary = "Assign a payment to a bill",
            description = "For a payment its symbol did not link: a typo, another day's symbol, none. Null unlinks it.")
    Payment assign(@Parameter(example = "1") @PathVariable("id") long id, @RequestBody PaymentAssignment assignment) {
        var payment = payments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no payment " + id));
        var symbol = assignment.variableSymbol();
        Long billId = null;
        if (symbol != null) {
            if (!symbol.matches("\\d{1,18}") || !bills.existsById(Long.valueOf(symbol))) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no bill " + symbol);
            }
            billId = Long.valueOf(symbol);
        }
        return payments.save(payment.linkedTo(billId));
    }
}
