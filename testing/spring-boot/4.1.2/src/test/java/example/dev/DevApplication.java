package example.dev;

import cz.landsman.moneta.AccessToken;
import cz.landsman.moneta.Account;
import cz.landsman.moneta.MonetaClient;
import cz.landsman.moneta.Transaction;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The library the way an application uses it, for trying it against a real account: `make dev` with
 * MONETA_TOKEN set, then http://localhost:8080/accounts and /transactions?days=7. Never part of the build's
 * tests; a scratch app in the test sources.
 */
@SpringBootApplication
public class DevApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevApplication.class, args);
    }

    @Bean
    MonetaClient moneta() {
        return new MonetaClient();
    }

    @RestController
    static class Endpoints {

        private final MonetaClient moneta;
        private final AccessToken token;

        Endpoints(MonetaClient moneta, @Value("${MONETA_TOKEN}") String token) {
            this.moneta = moneta;
            this.token = new AccessToken(token);
        }

        @GetMapping("/accounts")
        List<Account> accounts() {
            return moneta.accounts(token);
        }

        /** Everything mapped, but not {@code raw}: that holds the counterparty's name and account. */
        @GetMapping("/transactions")
        List<Movement> transactions(@RequestParam(defaultValue = "7") int days) {
            var today = LocalDate.now(ZoneId.of("Europe/Prague"));
            return moneta.accounts(token).stream()
                    .flatMap(account -> moneta.transactions(token, account.id(), today.minusDays(days), today).stream())
                    .map(Movement::of)
                    .toList();
        }
    }

    record Movement(
            String entryReference,
            Transaction.Status status,
            Transaction.Direction direction,
            String amount,
            LocalDate enteredDate,
            LocalDate bookingDate,
            LocalDate valueDate,
            String variableSymbol,
            String constantSymbol,
            String specificSymbol) {

        static Movement of(Transaction t) {
            return new Movement(
                    t.entryReference(),
                    t.status(),
                    t.direction(),
                    t.amount().toString(),
                    t.enteredDate(),
                    t.bookingDate(),
                    t.valueDate(),
                    t.symbols().variable(),
                    t.symbols().constant(),
                    t.symbols().specific());
        }
    }
}
