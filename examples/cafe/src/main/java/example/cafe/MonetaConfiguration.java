package example.cafe;

import cz.landsman.moneta.AccessToken;
import cz.landsman.moneta.MonetaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MonetaConfiguration {

    /** One client for the whole app: it holds no token and is thread-safe. Spring closes it on shutdown. */
    @Bean
    MonetaClient monetaClient(MonetaProperties properties) {
        return MonetaClient.builder()
                .baseUri(properties.baseUri())
                .requestTimeout(properties.requestTimeout())
                .build();
    }

    /** Refuses a blank token or one with a stray newline at startup, without repeating it. */
    @Bean
    AccessToken monetaToken(MonetaProperties properties) {
        return new AccessToken(properties.token());
    }
}
