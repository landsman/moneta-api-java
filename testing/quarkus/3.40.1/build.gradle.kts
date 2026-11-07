plugins {
    java
    id("io.quarkus") version "3.40.1"
}

dependencies {
    // The platform BOM, as code.quarkus.io writes it for a caller.
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.40.1"))
    implementation(project(":modules:moneta-api-quarkus"))
    implementation("io.quarkus:quarkus-arc")
    // Micrometer without micrometer-java11, the setup most applications have: the extension must not register its
    // observation, which needs the binder. 3.33.4 has no Micrometer at all.
    implementation("io.quarkus:quarkus-micrometer")
    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("org.junit.jupiter:junit-jupiter")
}

sourceSets.main {
    java.srcDir("../shared/src/main/java")
}

sourceSets.test {
    java.srcDir("../shared/src/test/java")
    resources.srcDir("../../resources") // one MONETA answer for every testing project
}

// quarkusIntTest: the built application, JVM or native; FakeMoneta comes from the test sources.
sourceSets.named("integrationTest") {
    java.srcDir("../shared/src/integrationTest/java")
    java.srcDir("../shared/src/test/java")
    resources.srcDir("../../resources")
}

// The packaged application too, in make build; a few seconds on the JVM.
tasks.check { dependsOn(tasks.named("quarkusIntTest")) }

// The fix .docs/compatibility.md gives a Quarkus native caller: the platform BOM pins jackson-annotations 2.21,
// and the client's Jackson 3.2 links against 2.22, which a native image checks at build time. Only for the native
// build, so the JVM tests keep proving that a JVM caller needs nothing.
if (providers.systemProperty("quarkus.native.enabled").orNull == "true") {
    configurations.all { resolutionStrategy.force("com.fasterxml.jackson.core:jackson-annotations:2.22") }
}

tasks.named<Test>("quarkusIntTest") {
    // The whole failure, with the application's output, in the CI log; no report is kept there.
    testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
