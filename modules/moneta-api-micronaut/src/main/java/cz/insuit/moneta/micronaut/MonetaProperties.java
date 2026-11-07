package cz.insuit.moneta.micronaut;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.MonetaClient;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * {@code moneta.*}, the same names the Spring Boot starter reads, without {@code moneta.ssl}: mTLS is an {@link
 * cz.insuit.moneta.HttpClientCustomizer} bean setting the {@code SSLContext}. A token or partner value the client
 * would refuse fails here, when the configuration is bound: at startup with {@code eagerInitConfiguration(true)},
 * without building the client.
 *
 * @param token one account's token, for an application that reads only that one; it then gets an {@code
 *     AccessToken} bean. An application serving many merchants leaves it unset and passes each one's token with
 *     the call. Set it from the environment, never in a committed file.
 * @param baseUri MONETA's host; the client's own default, production, unless set
 * @param requestTimeout how long one call to MONETA may take; the client's own default unless set
 * @param partner the headers that name a registered application, sent only when set
 */
@ConfigurationProperties("moneta")
public record MonetaProperties(
        @Nullable String token,
        @Nullable URI baseUri,
        @Nullable Duration requestTimeout,
        @Nullable Partner partner) {

    public MonetaProperties {
        if (token != null) {
            new AccessToken(token); // throws for a value it refuses; the bean itself is made when injected
        }
    }

    /**
     * {@code moneta.partner.*}, sent as the {@code identification_number}, {@code business_name} and {@code
     * application_name} headers. All three or none; printable ASCII only.
     *
     * @param identificationNumber the registered organisation's IČO
     * @param businessName its name, without diacritics
     * @param applicationName the application's name
     */
    // Without it Micronaut builds the record whether moneta.partner.* is set or not, and fails on the missing ones.
    @Requires(property = "moneta.partner")
    @ConfigurationProperties("partner")
    public record Partner(String identificationNumber, String businessName, String applicationName) {

        public Partner {
            new MonetaClient.Partner(identificationNumber, businessName, applicationName); // throws for what it refuses
        }
    }
}
