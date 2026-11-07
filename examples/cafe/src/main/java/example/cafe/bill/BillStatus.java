package example.cafe.bill;

import java.math.BigDecimal;

/** How much of a bill its linked payments cover. */
public enum BillStatus {
    UNPAID,
    PARTLY_PAID,
    PAID,
    /** More came than the bill asked: most likely paid twice, which a person sorts out. */
    OVERPAID;

    static BillStatus of(BigDecimal billed, BigDecimal paid) {
        if (paid.signum() == 0) {
            return UNPAID;
        }
        return switch (Integer.signum(paid.compareTo(billed))) {
            case -1 -> PARTLY_PAID;
            case 0 -> PAID;
            default -> OVERPAID;
        };
    }
}
