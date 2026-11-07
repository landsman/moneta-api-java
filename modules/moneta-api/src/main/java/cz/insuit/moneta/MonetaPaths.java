package cz.insuit.moneta;

/** Every path the client calls, relative to the host. */
final class MonetaPaths {

    private static final String AISP = "api/v4/vip/aisp/my/";

    static final String ACCOUNTS = AISP + "accounts";

    static String transactions(String accountId) {
        // URI.resolve drops dot segments, so ".." would call a different endpoint.
        if (accountId.isBlank() || accountId.equals(".") || accountId.equals("..")) {
            throw new IllegalArgumentException("accountId is blank or a dot segment");
        }
        return ACCOUNTS + "/" + Query.encode(accountId) + "/transactions";
    }

    private MonetaPaths() {}
}
