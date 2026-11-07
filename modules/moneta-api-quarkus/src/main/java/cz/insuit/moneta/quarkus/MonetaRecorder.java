package cz.insuit.moneta.quarkus;

import cz.insuit.moneta.AccessToken;
import io.quarkus.arc.ActiveResult;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.SmallRyeConfig;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * The {@link AccessToken} bean, active only with {@code quarkus.moneta.token} set. The token is known only at
 * runtime, so whether the bean exists cannot be decided at build time; injecting it without a token fails with
 * this message rather than with a null.
 */
@Recorder
public class MonetaRecorder {

    public Supplier<ActiveResult> tokenActive() {
        return () -> config().token().isPresent()
                ? ActiveResult.active()
                : ActiveResult.inactive("quarkus.moneta.token is not set; an application serving many merchants"
                        + " passes each one's token with the call instead of injecting AccessToken");
    }

    public Supplier<AccessToken> token() {
        return () -> new AccessToken(config().token().orElseThrow());
    }

    private static MonetaConfig config() {
        return ConfigProvider.getConfig().unwrap(SmallRyeConfig.class).getConfigMapping(MonetaConfig.class);
    }
}
