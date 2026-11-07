package cz.insuit.moneta.spring;

import static java.util.Objects.requireNonNull;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.MonetaClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * One {@link MonetaClient} for the application, built from {@code moneta.*}, unless the application defines its
 * own. Spring closes it on shutdown, which releases the HTTP client it made. Each {@link MonetaHttpClientCustomizer}
 * bean changes that HTTP client before it is built, and each {@link MonetaHttpClientDecorator} wraps it, in order.
 */
@AutoConfiguration
@EnableConfigurationProperties(MonetaProperties.class)
public class MonetaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MonetaClient monetaClient(
            MonetaProperties properties,
            ObjectProvider<MonetaHttpClientCustomizer> customizers,
            ObjectProvider<MonetaHttpClientDecorator> decorators) {
        // Only what the application set is passed on, so each default lives in one place: the client.
        var builder = MonetaClient.builder();
        var baseUri = properties.baseUri();
        if (baseUri != null) {
            builder.baseUri(baseUri);
        }
        var requestTimeout = properties.requestTimeout();
        if (requestTimeout != null) {
            builder.requestTimeout(requestTimeout);
        }
        var partner = properties.partner();
        if (partner != null) {
            builder.partner(new MonetaClient.Partner(
                    partner.identificationNumber(), partner.businessName(), partner.applicationName()));
        }
        customizers.orderedStream().forEach(customizer -> builder.configureHttpClient(customizer::customize));
        decorators.orderedStream().forEach(decorator -> builder.decorateHttpClient(decorator::decorate));
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
