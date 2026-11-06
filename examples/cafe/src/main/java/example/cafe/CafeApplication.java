package example.cafe;

import cz.landsman.moneta.AccessToken;
import cz.landsman.moneta.MonetaClient;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** A café checking which payments reached its MONETA account. */
@SpringBootApplication
public class CafeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CafeApplication.class, args);
    }

    /** One client for the whole app; it holds no token and is thread-safe. */
    @Bean
    MonetaClient moneta() {
        return new MonetaClient();
    }

    /** The token the café issued in Internet Banka, read-only, from the environment and nowhere else. */
    @Bean
    AccessToken monetaToken(@Value("${MONETA_TOKEN}") String token) {
        return new AccessToken(token);
    }

    /** The bank's day, not the server's: a server on UTC would put a payment at 00:30 on the wrong day. */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Europe/Prague"));
    }
}
