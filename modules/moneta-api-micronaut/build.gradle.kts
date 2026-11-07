plugins {
    `java-library`
}

description = "Micronaut wiring for the MONETA API client"

// Compiled against the oldest Micronaut it supports, as the starter is against the oldest Spring Boot; the
// application's own Micronaut runs it, and testing/micronaut checks it on each major. compileOnly, so the BOM is
// not handed to the caller.
val micronaut = dependencies.platform("io.micronaut.platform:micronaut-platform:4.10.14")

dependencies {
    api(project(":modules:moneta-api"))
    api("org.jspecify:jspecify:1.0.1")
    compileOnly(micronaut)
    compileOnly("io.micronaut:micronaut-inject")
    compileOnly("io.micronaut:micronaut-core")
    compileOnly("jakarta.inject:jakarta.inject-api")
    // Optional: an application with micrometer-java11 and a MeterRegistry gets every call instrumented.
    compileOnly("io.micrometer:micrometer-java11")
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("io.micrometer:micrometer-observation")
    // Writes the bean definitions into the jar; Micronaut scans nothing at runtime.
    annotationProcessor(micronaut)
    annotationProcessor("io.micronaut:micronaut-inject-java")

    testImplementation(micronaut)
    testImplementation("io.micronaut:micronaut-inject")
    testImplementation("io.micronaut:micronaut-core")
    testImplementation("jakarta.inject:jakarta.inject-api")
    testImplementation("io.micrometer:micrometer-core")
    testRuntimeOnly("io.micrometer:micrometer-java11")
    // Registers the converter that reads "5s" as a Duration; every Micronaut application has it, micronaut-inject
    // alone does not.
    testRuntimeOnly("io.micronaut:micronaut-context")
    testAnnotationProcessor(micronaut)
    testAnnotationProcessor("io.micronaut:micronaut-inject-java")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencyAnalysis {
    // It writes the $Definition classes Micronaut loads instead of scanning; the analysis only sees that no
    // annotation of its own is used.
    issues { onUnusedAnnotationProcessors { exclude("io.micronaut:micronaut-inject-java") } }
}
