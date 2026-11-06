import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    id("com.diffplug.spotless") version "8.11.0"
    id("net.ltgt.errorprone") version "5.2.0"
    // For the compat projects only.
    id("io.spring.dependency-management") version "1.1.7" apply false
    kotlin("jvm") version "2.4.21" apply false
}

group = "cz.landsman"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Nullability annotations are part of the API, so Kotlin callers see String and String? correctly.
    api("org.jspecify:jspecify:1.0.1")
    // No Jackson type leaves the library, and databind and core of the two majors live in different packages,
    // so a caller on Jackson 2 keeps it. jackson-annotations is the exception: Jackson 3.2 shares it with 2 and
    // needs 2.21 or newer, which a Spring Boot 3 BOM before 3.5.13 pins lower (see the README).
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
    withJavadocJar()
}

tasks.jar {
    // Pinned, so a JPMS caller's `requires` does not change with the jar's file name.
    manifest { attributes("Automatic-Module-Name" to "cz.landsman.moneta") }
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
        target("src/**/*.java", "compat/**/*.java")
        palantirJavaFormat("2.103.0")
    }
    kotlin {
        target("compat/**/*.kt")
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts", "compat/**/*.gradle.kts")
        ktlint()
    }
}

// Compiled once, for release 21, on whatever JDK runs Gradle; the tests run on the JDK -PtestsJdk names, so CI's
// matrix checks the jar on each JDK it supports. Without it they run on Gradle's own.
val testsJdk = providers.gradleProperty("testsJdk")

allprojects {
    repositories {
        mavenCentral()
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release = 21
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        if (testsJdk.isPresent) {
            javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(testsJdk.get()) }
        }
    }
}

// The oldest Jackson a caller is likely to resolve this to: Spring Boot 4.0.0's. Running the same tests on it
// catches a call to an API newer than that, which would compile here and fail there with NoSuchMethodError.
val jacksonFloor =
    configurations.create("jacksonFloor") {
        isCanBeConsumed = false
        extendsFrom(configurations.testImplementation.get(), configurations.testRuntimeOnly.get())
        // Pinned on purpose, and not written as "group:name:version": Dependabot would take that for a dependency
        // and bump it, raising the floor this test exists to hold.
        val floor =
            mapOf(
                "tools.jackson.core:jackson-databind" to "3.0.4",
                "tools.jackson.core:jackson-core" to "3.0.4",
                "com.fasterxml.jackson.core:jackson-annotations" to "2.20",
            )
        resolutionStrategy.force(floor.map { (module, version) -> "$module:$version" })
    }

val jacksonFloorTest =
    tasks.register<Test>("jacksonFloorTest") {
        description = "Runs the tests on the oldest Jackson 3 the library supports."
        group = "verification"
        testClassesDirs =
            sourceSets.test
                .get()
                .output.classesDirs
        classpath = sourceSets.test.get().output + sourceSets.main.get().output + jacksonFloor
    }

tasks.check {
    dependsOn(jacksonFloorTest)
}

// Trivy reads Gradle only through gradle.lockfile. Only what ships is locked: tooling and tests stay out of
// the scan, where a finding could not be acted on anyway.
configurations.named("runtimeClasspath") {
    resolutionStrategy.activateDependencyLocking()
}

// Nothing else in the build resolves runtimeClasspath, so a stale lockfile would pass and Trivy would scan
// versions that do not ship. Resolving it here fails the build on any drift; `make lock` rewrites the file.
val verifyLocks =
    tasks.register("verifyLocks") {
        description = "Fails when gradle.lockfile no longer matches the runtime classpath."
        group = "verification"
        inputs.files(configurations.runtimeClasspath)
        doLast {}
    }

tasks.check {
    dependsOn(verifyLocks)
}

tasks.javadoc {
    // A record's components are named well enough; an @param for each would only repeat the name.
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}
