package cz.insuit.moneta.quarkus.deployment;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.quarkus.MonetaProducer;
import cz.insuit.moneta.quarkus.MonetaRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import jakarta.inject.Singleton;

/** What Quarkus registers at build time: the feature, the client's producer and the token bean. */
public class MonetaProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("moneta");
    }

    @BuildStep
    AdditionalBeanBuildItem producer() {
        return AdditionalBeanBuildItem.unremovableOf(MonetaProducer.class);
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
