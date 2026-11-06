package cz.landsman.moneta.spring;

import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code moneta.*}.
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

    /**
     * {@code moneta.partner.*}, sent as the {@code identification_number}, {@code business_name} and {@code
     * application_name} headers. All three or none; printable ASCII only.
     *
     * @param identificationNumber the registered organisation's IČO
     * @param businessName its name, without diacritics
     * @param applicationName the application's name
     */
    public record Partner(String identificationNumber, String businessName, String applicationName) {}
}
