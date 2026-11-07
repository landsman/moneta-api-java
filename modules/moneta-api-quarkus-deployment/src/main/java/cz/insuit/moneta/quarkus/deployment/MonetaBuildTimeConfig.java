package cz.insuit.moneta.quarkus.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** {@code quarkus.moneta.*} fixed when the application is built. */
@ConfigMapping(prefix = "quarkus.moneta")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface MonetaBuildTimeConfig {

    /** Metrics of every call to MONETA. */
    Metrics metrics();

    /** {@code quarkus.moneta.metrics.*}. */
    interface Metrics {

        /**
         * Whether every call is timed, when the application has {@code quarkus-micrometer} and {@code
         * micrometer-java11}. Off, the application may wrap the client with an {@code HttpClientDecorator} of its
         * own instead.
         */
        @WithDefault("true")
        boolean enabled();
    }
}
