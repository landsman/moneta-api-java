package cz.landsman.moneta;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Every path the client calls, relative to the host. A path segment from a caller is encoded here, nowhere else. */
final class MonetaPaths {

    private static final String AISP = "api/v4/vip/aisp/my/";

    static final String ACCOUNTS = AISP + "accounts";

    static String transactions(String accountId) {
        return ACCOUNTS + "/" + segment(accountId) + "/transactions";
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private MonetaPaths() {}
}
