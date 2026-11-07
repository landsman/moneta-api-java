package cz.insuit.moneta.play;

import com.typesafe.config.Config;
import cz.insuit.moneta.MonetaClient;
import java.net.URI;

/** {@code moneta.*}, read into the client's builder; the keys are the Spring starter's. */
final class MonetaConfig {

    private MonetaConfig() {}

    /** Only what the application set is passed on, so each default lives in one place: the client. */
    static MonetaClient.Builder builder(Config config) {
        var builder = MonetaClient.builder();
        if (config.hasPath("moneta.base-uri")) {
            builder.baseUri(URI.create(config.getString("moneta.base-uri")));
        }
        if (config.hasPath("moneta.request-timeout")) {
            builder.requestTimeout(config.getDuration("moneta.request-timeout"));
        }
        if (config.hasPath("moneta.partner")) {
            var partner = config.getConfig("moneta.partner");
            builder.partner(new MonetaClient.Partner(
                    partner.getString("identification-number"),
                    partner.getString("business-name"),
                    partner.getString("application-name")));
        }
        return builder;
    }
}
