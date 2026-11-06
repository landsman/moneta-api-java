import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    id("com.diffplug.spotless") version "8.11.0"
    id("net.ltgt.errorprone") version "5.2.0"
}

group = "cz.landsman"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Nullability annotations are part of the API, so Kotlin callers see String and String? correctly.
    api("org.jspecify:jspecify:1.0.1")
    // Only the tree model is used and no Jackson type leaves the library, so a caller on Jackson 2
    // keeps it: the two majors live in different packages.
    implementation("tools.jackson.core:jackson-databind:3.2.4")

    errorprone("com.google.errorprone:error_prone_core:2.52.0")
    // Makes the JSpecify annotations a compile error when broken, not just documentation.
    errorprone("com.uber.nullaway:nullaway:0.14.3")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    // Built on any current JDK, usable from Java 21.
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-Werror"))
    options.errorprone {
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:JSpecifyMode", "true")
        option("NullAway:OnlyNullMarked", "true")
    }
}

spotless {
    java {
        palantirJavaFormat("2.103.0")
    }
    kotlinGradle {
        ktlint()
    }
}

tasks.test {
    useJUnitPlatform()
}

// Trivy reads Gradle only through gradle.lockfile. Only what ships is locked: tooling and tests stay out of
// the scan, where a finding could not be acted on anyway.
configurations.named("runtimeClasspath") {
    resolutionStrategy.activateDependencyLocking()
}
