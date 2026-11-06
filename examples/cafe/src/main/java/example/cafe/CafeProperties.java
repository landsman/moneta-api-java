package example.cafe;

import static java.util.Objects.requireNonNull;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code cafe.*} in application.yml.
 *
 * @param timeZone whose "today" a period means: the bank's, so a server on UTC does not put a payment made at
 *     00:30 on the day before
 * @param iban the café's account, which the bills' QR payments pay into; required, so the app does not start
 *     without it
 */
@ConfigurationProperties("cafe")
public record CafeProperties(@DefaultValue("Europe/Prague") ZoneId timeZone, String iban) {

    public CafeProperties {
        requireNonNull(iban, "cafe.iban");
    }
}
