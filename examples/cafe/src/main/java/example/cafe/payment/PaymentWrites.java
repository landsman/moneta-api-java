package example.cafe.payment;

import static java.nio.charset.StandardCharsets.UTF_8;

import cz.insuit.moneta.Transaction;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The two writes that two rounds can race on, each one statement whose row count says who won. A lookup first
 * would let both rounds find nothing and both write; here the database decides, and the loser learns it from a 0.
 */
@Component
class PaymentWrites {

    private static final String RECORD = query("examples/cafe/src/main/resources/queries/record-payment.sql");
    private static final String BOOK = query("examples/cafe/src/main/resources/queries/book-payment.sql");

    private final JdbcClient jdbc;

    PaymentWrites(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Whether this call recorded the payment; false when another round already had. */
    boolean record(Payment payment) {
        return jdbc.sql(RECORD)
                        .param("monetaEntryReference", payment.monetaEntryReference())
                        .param("booked", payment.booked())
                        .param("amount", payment.amount())
                        .param("currency", payment.currency())
                        .param("variableSymbol", payment.variableSymbol())
                        .param("counterpartyAccount", payment.counterpartyAccount())
                        .param("counterpartyName", payment.counterpartyName())
                        .param("message", payment.message())
                        .param("madeOn", payment.madeOn())
                        .param("firstSeenAt", OffsetDateTime.ofInstant(payment.firstSeenAt(), ZoneOffset.UTC))
                        .param("billId", payment.billId())
                        .update()
                == 1;
    }

    /** Whether this call booked the pending row; false when another round already had. */
    boolean book(Payment pending, Transaction booked) {
        return jdbc.sql(BOOK)
                        .param("id", pending.id())
                        .param("monetaEntryReference", booked.entryReference())
                        .param("counterpartyName", booked.counterpartyName())
                        .param("message", booked.message())
                        .param("madeOn", PaymentRecording.madeOn(booked))
                        .update()
                == 1;
    }

    /** By its path from the repository root, so the name opens the file; read from the classpath. */
    private static String query(String path) {
        var resource = new ClassPathResource(path.substring(path.indexOf("queries/")));
        try {
            return resource.getContentAsString(UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
