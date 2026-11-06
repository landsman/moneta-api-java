package example.cafe;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code cafe.*} in application.yml.
 *
 * @param timeZone whose "today" a period means: the bank's, so a server on UTC does not put a payment made at
 *     00:30 on the day before
 */
@ConfigurationProperties("cafe")
record CafeProperties(@DefaultValue("Europe/Prague") ZoneId timeZone) {}
