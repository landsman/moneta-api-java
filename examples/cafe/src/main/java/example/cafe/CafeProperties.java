package example.cafe;

import java.time.ZoneId;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code cafe.*} in application.yml.
 *
 * @param timeZone whose "today" a period means: the bank's, so a server on UTC does not put a payment made at
 *     00:30 on the day before
 * @param iban the account the bills' QR payments pay into; without it, the first account the token sees
 */
@ConfigurationProperties("cafe")
public record CafeProperties(
        @DefaultValue("Europe/Prague") ZoneId timeZone,
        @Nullable String iban) {}
