package cz.insuit.moneta.quarkus;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * {@code quarkus.moneta.*}: the names the Spring Boot starter reads under {@code moneta.*}, with TLS from
 * Quarkus's TLS registry instead of a Spring SSL bundle. Read when the application starts, so a token can come
 * from the environment.
 */
@ConfigMapping(prefix = "quarkus.moneta")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface MonetaConfig {

    /**
     * One account's token, for an application that reads only that one; it then gets an {@code AccessToken} bean.
     * An application serving many merchants leaves it unset and passes each one's token with the call. Set it
     * from the environment, never in a committed file.
     */
    Optional<String> token();

    /** MONETA's host; the client's own default, production, unless set. */
    Optional<URI> baseUri();

    /** How long one call to MONETA may take; the client's own default unless set. */
    Optional<Duration> requestTimeout();

    /** The headers that name a registered application, sent only when set. */
    Optional<Partner> partner();

    /**
     * The name of a {@code quarkus.tls.<name>.*} configuration: a client certificate for mTLS, a trust store, the
     * protocols and ciphers. The JDK's own TLS unless set. Read once, when the client is built.
     */
    Optional<String> tlsConfigurationName();

    /**
     * {@code quarkus.moneta.partner.*}, sent as the {@code identification_number}, {@code business_name} and
     * {@code application_name} headers. All three or none; printable ASCII only.
     */
    interface Partner {

        /** The registered organisation's IČO. */
        String identificationNumber();

        /** Its name, without diacritics. */
        String businessName();

        /** The application's name. */
        String applicationName();
    }
}
