package cz.landsman.moneta;

import java.time.LocalDate;

/** The example in .docs/usage.md, word for word. Compiled, never run: a renamed accessor breaks the build, not the doc. */
@SuppressWarnings("unused")
final class UsageExample {

    static void example(AccessToken token) {
        // --- .docs/usage.md start
        var moneta = new MonetaClient(); // or MonetaClient.builder().baseUri(sandbox).partner(partner).build()

        for (var account : moneta.accounts(token)) {
            for (var t :
                    moneta.transactions(token, account.id(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))) {
                if (t.booked()) {
                    System.out.println(t.amount() + " VS " + t.symbols().variable());
                }
            }
        }
        // --- .docs/usage.md end
    }

    private UsageExample() {}
}
