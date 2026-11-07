package example.quarkus;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.MonetaClient;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import java.time.LocalDate;

/** Prints each transaction MONETA returns for August 2026; what the native integration test runs. */
@QuarkusMain
public class PrintTransactions implements QuarkusApplication {

    @Inject
    MonetaClient moneta;

    @Inject
    AccessToken token;

    @Override
    public int run(String... args) {
        moneta.transactions(token, "acc-1", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
                .forEach(t -> System.out.println(t.amount()));
        return 0;
    }
}
