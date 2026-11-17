plugins {
    `java-library`
    // Writes META-INF/quarkus-extension.properties, which names the deployment jar Quarkus pulls in at build time.
    // Versioned here, not in the root: there it would share a class loader with testing/quarkus's io.quarkus
    // plugins, which come in other versions and clash with it.
    id("io.quarkus.extension") version "3.40.1.2"
}

description = "Quarkus extension for the MONETA API client"

// Compiled against the oldest supported Quarkus LTS, as the starter is against the oldest Spring Boot; the
// application's own Quarkus runs it. compileOnly, so the BOM is not handed to the caller, and so is Quarkus
// itself: every Quarkus application has quarkus-arc.
val quarkus = dependencies.platform("io.quarkus:quarkus-bom:3.33.4")

dependencies {
    api(project(":modules:moneta-api"))
    // Only @NullMarked on the package; nothing in the API is nullable.
    compileOnly("org.jspecify:jspecify:1.0.1")
    compileOnly(quarkus)
    compileOnly("io.quarkus:quarkus-core")
    compileOnly("io.quarkus:quarkus-arc")
    compileOnly("io.quarkus.arc:arc")
    compileOnly("io.smallrye.config:smallrye-config-core")
    compileOnly("org.eclipse.microprofile.config:microprofile-config-api")
    compileOnly("jakarta.enterprise:jakarta.enterprise.cdi-api")
    compileOnly("jakarta.inject:jakarta.inject-api")
    // quarkus.moneta.tls-configuration-name. A real dependency, not compileOnly: a native image links every class
    // at build time, so the registry's types must exist even in an application that names no TLS configuration.
    // The application's Quarkus BOM picks the version; this one is only the floor.
    runtimeOnly("io.quarkus:quarkus-tls-registry:3.33.4")
    compileOnly("io.quarkus:quarkus-tls-registry-spi")
    compileOnly("io.vertx:vertx-core") // the protocols and ciphers come as Vert.x SSLOptions
    // Optional: an application with quarkus-micrometer and micrometer-java11 gets every call timed.
    compileOnly("io.micrometer:micrometer-java11")
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("jakarta.annotation:jakarta.annotation-api")
    annotationProcessor(quarkus)
    annotationProcessor("io.quarkus:quarkus-extension-processor")
}

java {
    withSourcesJar()
    withJavadocJar()
}

quarkusExtension {
    deploymentModule = ":modules:moneta-api-quarkus-deployment"
}

tasks.compileJava {
    options.compilerArgs.addAll(
        listOf(
            // The config docs are generated only in a Maven build, which this is not; README.md lists the keys.
            "-AgenerateDoc=false",
            // Vert.x's SSLOptions carries an annotation from vertx-codegen, which the compiler only needs to read.
            "-Xlint:-classfile",
        ),
    )
}
