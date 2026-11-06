package example.cafe.bill;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * The body of {@code POST /bills}.
 *
 * @param amount in crowns, at most two decimals
 */
record NewBill(@Schema(example = "1.00") BigDecimal amount) {}
