package cz.landsman.moneta.spring;

import static java.util.Objects.requireNonNull;

import cz.landsman.moneta.AccessToken;
import cz.landsman.moneta.MonetaClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * One {@link MonetaClient} for the application, built from {@code moneta.*}, unless the application defines its
 * own. Spring closes it on shutdown, which releases the HTTP client it made.
 */
@AutoConfiguration
@EnableConfigurationProperties(MonetaProperties.class)
public class MonetaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MonetaClient monetaClient(MonetaProperties properties) {
        var builder = MonetaClient.builder().baseUri(properties.baseUri()).requestTimeout(properties.requestTimeout());
        var partner = properties.partner();
        if (partner != null) {
            builder.partner(new MonetaClient.Partner(
                    partner.identificationNumber(), partner.businessName(), partner.applicationName()));
        }
        return builder.build();
    }

    /** Only with {@code moneta.token} set; a blank one or one ending in a newline stops the application. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty("moneta.token")
    public AccessToken monetaAccessToken(MonetaProperties properties) {
        return new AccessToken(requireNonNull(properties.token(), "moneta.token"));
    }
}
