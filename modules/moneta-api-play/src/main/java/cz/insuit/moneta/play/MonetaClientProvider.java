package cz.insuit.moneta.play;

import com.typesafe.config.Config;
import cz.insuit.moneta.HttpClientCustomizer;
import cz.insuit.moneta.HttpClientDecorator;
import cz.insuit.moneta.MonetaClient;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Provider;
import play.api.Environment;
import play.api.inject.ApplicationLifecycle;
import play.api.inject.Injector;

/**
 * Builds the client from {@code moneta.*}, with the hooks {@code moneta.http.customizers} and {@code
 * moneta.http.decorators} name, and closes it when the application stops. Play's Scala types throughout, which every
 * application has; the Java lifecycle is bound only with play-java.
 */
final class MonetaClientProvider implements Provider<MonetaClient> {

    private final Config config;
    private final ApplicationLifecycle lifecycle;
    private final Injector injector;
    private final Environment environment;

    @Inject
    MonetaClientProvider(Config config, ApplicationLifecycle lifecycle, Injector injector, Environment environment) {
        this.config = config;
        this.lifecycle = lifecycle;
        this.injector = injector;
        this.environment = environment;
    }

    @Override
    public MonetaClient get() {
        var builder = MonetaConfig.builder(config);
        // In the listed order, as play.filters.enabled is; each made by the injector, so it can take a MeterRegistry.
        for (var hook : hooks("moneta.http.customizers", HttpClientCustomizer.class)) {
            builder.configureHttpClient(hook::customize);
        }
        for (var hook : hooks("moneta.http.decorators", HttpClientDecorator.class)) {
            builder.decorateHttpClient(hook::decorate);
        }
        var client = builder.build();
        lifecycle.addStopHook(() -> {
            client.close();
            return CompletableFuture.completedFuture(null);
        });
        return client;
    }

    private <T> List<T> hooks(String key, Class<T> type) {
        if (!config.hasPath(key)) {
            return List.of();
        }
        return config.getStringList(key).stream()
                .<T>map(name -> injector.instanceOf(load(key, name, type)))
                .toList();
    }

    /** The application's class loader, which dev mode replaces on each reload; a wrong name stops the start. */
    private <T> Class<? extends T> load(String key, String name, Class<T> type) {
        try {
            return Class.forName(name, true, environment.classLoader()).asSubclass(type);
        } catch (ClassNotFoundException | ClassCastException e) {
            throw new IllegalArgumentException(
                    key + ": " + name + " is not a " + type.getName() + " on the classpath", e);
        }
    }
}
