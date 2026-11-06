package cz.landsman.moneta;

/** Every path the client calls, relative to the host. */
final class MonetaPaths {

    private static final String AISP = "api/v4/vip/aisp/my/";

    static final String ACCOUNTS = AISP + "accounts";

    static String transactions(String accountId) {
        return ACCOUNTS + "/" + Query.encode(accountId) + "/transactions";
    }

    private MonetaPaths() {}
}
