plugins {
    `java-library`
}

description = "Build-time half of the Quarkus extension for the MONETA API client"

// As in the runtime module: the oldest supported Quarkus LTS, compileOnly, since every application that builds
// with Quarkus has quarkus-arc-deployment.
val quarkus = dependencies.platform("io.quarkus:quarkus-bom:3.33.4")

dependencies {
    implementation(project(":modules:moneta-api"))
    implementation(project(":modules:moneta-api-quarkus"))
    // Every extension the runtime module depends on needs its deployment half here; the plugin checks it.
    implementation("io.quarkus:quarkus-tls-registry-deployment:3.33.4")
    compileOnly(quarkus)
    compileOnly("io.quarkus:quarkus-core")
    compileOnly("io.quarkus:quarkus-core-deployment")
    compileOnly("io.quarkus:quarkus-builder")
    compileOnly("io.quarkus:quarkus-arc-deployment")
    compileOnly("io.quarkus.arc:arc-processor")
    compileOnly("io.quarkus:quarkus-bootstrap-core")
    compileOnly("io.smallrye.config:smallrye-config-core")
    compileOnly("jakarta.inject:jakarta.inject-api")
    annotationProcessor(quarkus)
    annotationProcessor("io.quarkus:quarkus-extension-processor")

    testImplementation(quarkus)
    testImplementation("io.quarkus:quarkus-junit-internal")
    testImplementation("io.quarkus.arc:arc")
    testImplementation("jakarta.enterprise:jakarta.enterprise.cdi-api")
    testImplementation("jakarta.inject:jakarta.inject-api")
    testImplementation("org.jboss.shrinkwrap:shrinkwrap-api")
    // What QuarkusUnitTest builds the application from; found by Quarkus at build time, never by a class.
    testRuntimeOnly("io.quarkus:quarkus-arc-deployment")
    // Every test application has Micrometer, so the metrics are on unless a test turns them off; the testing/quarkus
    // projects are the applications without it.
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("io.micrometer:micrometer-java11")
    testRuntimeOnly("io.quarkus:quarkus-micrometer-deployment")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.compileJava {
    // As in the runtime module: no config docs outside a Maven build.
    options.compilerArgs.add("-AgenerateDoc=false")
}

tasks.test {
    // The Quarkus build plugins supply the platform's properties; QuarkusUnitTest under plain Gradle has none and
    // fails on the one quarkus.native.builder-image refers to. These tests build nothing native.
    systemProperty("platform.quarkus.native.builder-image", "mandrel")
}

dependencyAnalysis {
    issues {
        // Needed though no class names it: the extension plugin requires the deployment half of every extension the
        // runtime module depends on, and Quarkus loads its build steps by service file.
        onUnusedDependencies { exclude("io.quarkus:quarkus-tls-registry-deployment") }
        // Quarkus assembles the test application at runtime from whatever extensions are on the classpath, so the
        // analysis takes each jar it loads for one to declare.
        onUsedTransitiveDependencies {
            exclude(
                "io.netty:.*",
                "io.vertx:.*",
                "io.smallrye:.*",
                "io.smallrye.reactive:.*",
                "com.fasterxml.jackson.core:.*",
                "org.graalvm.sdk:.*",
                "org.jline:.*",
                "org.junit.platform:.*",
                "io.quarkus:quarkus-vertx",
                "io.quarkus:quarkus-vertx-latebound-mdc-provider",
                "io.quarkus:quarkus-tls-registry",
            )
        }
    }
}
