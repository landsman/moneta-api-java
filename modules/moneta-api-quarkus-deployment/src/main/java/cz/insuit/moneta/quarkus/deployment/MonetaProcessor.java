package cz.insuit.moneta.quarkus.deployment;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.quarkus.MonetaObservation;
import cz.insuit.moneta.quarkus.MonetaProducer;
import cz.insuit.moneta.quarkus.MonetaRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.runtime.metrics.MetricsFactory;
import jakarta.inject.Singleton;
import java.util.Optional;

/** What Quarkus registers at build time: the feature, the client's producer, its metrics and the token bean. */
public class MonetaProcessor {

    private static final String BINDER = "io.micrometer.java11.instrument.binder.jdk.MicrometerHttpClient";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("moneta");
    }

    @BuildStep
    AdditionalBeanBuildItem producer() {
        return AdditionalBeanBuildItem.unremovableOf(MonetaProducer.class);
    }

    /** Only with Micrometer's JDK client binder, which the application adds; Quarkus has no binder of its own. */
    @BuildStep
    void observation(
            MonetaBuildTimeConfig config,
            Optional<MetricsCapabilityBuildItem> metrics,
            BuildProducer<AdditionalBeanBuildItem> beans) {
        var micrometer =
                metrics.map(m -> m.metricsSupported(MetricsFactory.MICROMETER)).orElse(false);
        if (config.metrics().enabled() && micrometer && QuarkusClassLoader.isClassPresentAtRuntime(BINDER)) {
            beans.produce(AdditionalBeanBuildItem.unremovableOf(MonetaObservation.class));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    SyntheticBeanBuildItem accessToken(MonetaRecorder recorder) {
        return SyntheticBeanBuildItem.configure(AccessToken.class)
                .scope(Singleton.class)
                .defaultBean()
                .unremovable()
                .setRuntimeInit()
                .supplier(recorder.token())
                .checkActive(recorder.tokenActive())
                .done();
    }
}
