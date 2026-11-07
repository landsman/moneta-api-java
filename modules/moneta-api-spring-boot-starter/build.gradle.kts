plugins {
    `java-library`
}

description = "Spring Boot auto-configuration for the MONETA API client"

// Compiled against the oldest Spring Boot it supports; the application's own Spring Boot is what runs it, and
// testing/spring-boot checks it on each version. Dependabot leaves this BOM alone (.github/dependabot.yml).
val springBoot = dependencies.platform("org.springframework.boot:spring-boot-dependencies:3.5.16")

dependencies {
    api(project(":modules:moneta-api"))
    // MonetaProperties carries its nullability too; declared, not borrowed from the client.
    api("org.jspecify:jspecify:1.0.1")
    compileOnly(springBoot)
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    // ObjectProvider, to take every customizer and decorator bean in order.
    compileOnly("org.springframework:spring-beans")
    compileOnly("org.springframework:spring-core")
    // Optional: an application with micrometer-java11 and a MeterRegistry gets every call instrumented.
    compileOnly("io.micrometer:micrometer-java11")
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("io.micrometer:micrometer-observation")
    // Writes the metadata IntelliJ reads to complete moneta.* in application.yml.
    annotationProcessor(springBoot)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(springBoot)
    testImplementation("org.springframework.boot:spring-boot-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework:spring-beans")
    testImplementation("org.springframework:spring-core")
    // Only loaded: the auto-configuration wraps the client with it.
    testRuntimeOnly("io.micrometer:micrometer-java11")
    testImplementation("io.micrometer:micrometer-core")
    // ApplicationContextRunner hands the test an AssertJ type, so it has to be on the classpath.
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyAnalysis {
    // Never imported, but AssertableApplicationContext extends its AssertProvider, so javac needs it.
    issues {
        onUnusedDependencies { exclude("org.assertj:assertj-core") }
        // Asked for because a test declares a @Configuration; the tests have no properties to describe.
        onUsedTransitiveDependencies { exclude("org.springframework.boot:spring-boot-configuration-processor") }
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}
