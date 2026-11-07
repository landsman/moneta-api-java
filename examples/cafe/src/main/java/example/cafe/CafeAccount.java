package example.cafe;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.Account;
import cz.insuit.moneta.Balance;
import cz.insuit.moneta.MonetaClient;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** The café's own account at MONETA, the one whose IBAN is {@code cafe.iban}. */
@Service
public class CafeAccount {

    private final MonetaClient moneta;
    private final AccessToken token;
    private final CafeProperties cafe;

    CafeAccount(MonetaClient moneta, AccessToken token, CafeProperties cafe) {
        this.moneta = moneta;
        this.token = token;
        this.cafe = cafe;
    }

    public String iban() {
        return cafe.iban();
    }

    /** Empty when the token does not open an account with that IBAN. */
    public Optional<Account> find() {
        return moneta.accounts(token).stream()
                .filter(account -> cafe.iban().equals(account.iban()))
                .findFirst();
    }

    /** @throws NotOpened when the token does not open the account */
    public Balance balance() {
        var account = find().orElseThrow(() -> new NotOpened(cafe.iban()));
        return moneta.balance(token, account.id());
    }

    /** The café is set up with a token and an IBAN that do not match; whoever runs it has to fix that. */
    public static final class NotOpened extends RuntimeException {
        NotOpened(String iban) {
            super("the token does not open the café's account " + iban + " (cafe.iban)");
        }
    }
}
