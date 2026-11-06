package example.cafe;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code moneta.*} in application.yml.
 *
 * @param token the token the café issued in Internet Banka; set through {@code MONETA_TOKEN}, never in a file
 * @param baseUri MONETA's host, production unless set; the sandbox's once the portal gives it
 * @param requestTimeout how long one call to MONETA may take
 */
@ConfigurationProperties("moneta")
record MonetaProperties(
        String token,
        @DefaultValue("https://api.moneta.cz/") URI baseUri,
        @DefaultValue("30s") Duration requestTimeout) {}
