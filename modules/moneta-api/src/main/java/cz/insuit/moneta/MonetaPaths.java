package cz.insuit.moneta;

import java.util.List;
import java.util.regex.Pattern;

/** Every path the client calls, relative to the host. */
final class MonetaPaths {

    private static final String AISP = "api/v4/vip/aisp/my/";

    static final String ACCOUNTS = AISP + "accounts";

    /** What {@link #template} answers for a path the client does not call. */
    static final String UNKNOWN = "UNKNOWN";

    /**
     * Each path above as a template, with the pattern its built form matches. An encoded account id holds no
     * {@code /}, so one segment is one id. Anchored at the end only: a base URI may carry a prefix of its own.
     */
    private static final List<Template> TEMPLATES = List.of(
            new Template("/" + ACCOUNTS, Pattern.compile(".*/" + ACCOUNTS)),
            new Template(
                    "/" + ACCOUNTS + "/{accountId}/transactions",
                    Pattern.compile(".*/" + ACCOUNTS + "/[^/]+/transactions")),
            new Template(
                    "/" + ACCOUNTS + "/{accountId}/balance", Pattern.compile(".*/" + ACCOUNTS + "/[^/]+/balance")));

    static String transactions(String accountId) {
        return account(accountId) + "/transactions";
    }

    static String balance(String accountId) {
        return account(accountId) + "/balance";
    }

    /** The template a raw request path was built from, without an account id; {@link #UNKNOWN} for any other. */
    static String template(String rawPath) {
        return TEMPLATES.stream()
                .filter(t -> t.pattern().matcher(rawPath).matches())
                .map(Template::template)
                .findFirst()
                .orElse(UNKNOWN);
    }

    private static String account(String accountId) {
        // URI.resolve drops dot segments, so ".." would call a different endpoint.
        if (accountId.isBlank() || accountId.equals(".") || accountId.equals("..")) {
            throw new IllegalArgumentException("accountId is blank or a dot segment");
        }
        return ACCOUNTS + "/" + Query.encode(accountId);
    }

    private record Template(String template, Pattern pattern) {}

    private MonetaPaths() {}
}
