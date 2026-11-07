package cz.insuit.moneta.play;

import com.typesafe.config.Config;
import cz.insuit.moneta.MonetaClient;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Provider;
import play.api.inject.ApplicationLifecycle;

/**
 * Builds the client from {@code moneta.*} and closes it when the application stops. Play's Scala lifecycle, which
 * every application has; the Java one is bound only with play-java.
 */
final class MonetaClientProvider implements Provider<MonetaClient> {

    private final Config config;
    private final ApplicationLifecycle lifecycle;

    @Inject
    MonetaClientProvider(Config config, ApplicationLifecycle lifecycle) {
        this.config = config;
        this.lifecycle = lifecycle;
    }

    @Override
    public MonetaClient get() {
        var client = MonetaConfig.builder(config).build();
        lifecycle.addStopHook(() -> {
            client.close();
            return CompletableFuture.completedFuture(null);
        });
        return client;
    }
}
