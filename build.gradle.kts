import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("com.diffplug.spotless") version "8.11.0"
    id("com.autonomousapps.dependency-analysis") version "3.20.1"
    id("net.ltgt.errorprone") version "5.2.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.springframework.boot") version "4.1.2" apply false
    kotlin("jvm") version "2.4.21" apply false
}

// Compiled once, for release 21, on whatever JDK runs Gradle; the tests run on the JDK -PtestsJdk names, so CI's
// matrix checks the jars on each JDK they support. Without it they run on Gradle's own.
val testsJdk = providers.gradleProperty("testsJdk")

// A tag vX.Y.Z releases X.Y.Z (-PreleaseVersion, set by `make publish`); anything else is the next snapshot.
val releaseVersion = providers.gradleProperty("releaseVersion").orElse("0.3.0-SNAPSHOT")

allprojects {
    group = "cz.insuit"
    version = releaseVersion.get()

    repositories {
        mavenCentral()
    }
    tasks.withType<JavaCompile>().configureEach {
        // Built on any current JDK, usable from Java 21.
        options.release = 21
    }
    plugins.withType<JavaPlugin> {
        val toolchains = extensions.getByType<JavaToolchainService>()
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            if (testsJdk.isPresent) {
                javaLauncher = toolchains.launcherFor { languageVersion = JavaLanguageVersion.of(testsJdk.get()) }
            }
        }
    }
}

// What ships is held to the strictest checks: every warning fails, and the JSpecify annotations are enforced by
// NullAway rather than left as documentation. testing/ and examples/ are callers and stay ordinary.
subprojects {
    if (path.startsWith(":modules:")) {
        apply(plugin = "net.ltgt.errorprone")
        apply(plugin = "com.autonomousapps.dependency-analysis")
        dependencies {
            "errorprone"("com.google.errorprone:error_prone_core:2.52.0")
            "errorprone"("com.uber.nullaway:nullaway:0.14.3")
        }
        tasks.withType<JavaCompile>().configureEach {
            // -processing: the starter's metadata processor reads only @ConfigurationProperties and says so about
            // every other annotation, which is not a fault.
            options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing", "-Werror"))
            options.errorprone {
                check("NullAway", CheckSeverity.ERROR)
                option("NullAway:JSpecifyMode", "true")
                option("NullAway:OnlyNullMarked", "true")
            }
        }

        tasks.withType<Javadoc>().configureEach {
            // An @param per record component or bean method would only repeat its name; broken links still fail.
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
        }

        // Published to this repository's GitHub Packages; .docs/development.md says how a release is cut.
        apply(plugin = "maven-publish")
        plugins.withType<JavaLibraryPlugin> {
            configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("maven") {
                        from(components["java"])
                        pom {
                            name = project.name
                            // Lazily: the module sets its description after this block has run.
                            description = provider { project.description }
                            url = "https://github.com/landsman/moneta-api-java"
                            licenses {
                                license {
                                    name = "Apache License 2.0"
                                    url = "https://www.apache.org/licenses/LICENSE-2.0"
                                }
                            }
                            developers {
                                developer {
                                    id = "landsman"
                                    name = "Michal Landsman"
                                }
                            }
                            scm {
                                url = "https://github.com/landsman/moneta-api-java"
                                connection = "scm:git:https://github.com/landsman/moneta-api-java.git"
                            }
                        }
                    }
                }
                repositories {
                    maven {
                        name = "GitHubPackages"
                        url = uri("https://maven.pkg.github.com/landsman/moneta-api-java")
                        // Set by GitHub Actions; a release from a laptop is not the way this one is cut.
                        credentials {
                            username = System.getenv("GITHUB_ACTOR")
                            password = System.getenv("GITHUB_TOKEN")
                        }
                    }
                }
            }
        }
    }
}

// make build fails on a dependency a module declares and does not use, or uses without declaring: the first ships
// in the POM to every caller for nothing, the second breaks the day the dependency that brought it drops it.
dependencyAnalysis {
    structure {
        // junit-jupiter is the aggregate that brings the engine; the advice to declare -api instead would leave the
        // tests compiling and none of them running.
        bundle("junit-jupiter") { includeGroup("org.junit.jupiter") }
    }
    issues { all { onAny { severity("fail") } } }
}

spotless {
    java {
        target("modules/*/src/**/*.java", "testing/**/src/**/*.java", "examples/*/src/**/*.java", "tools/*/src/**/*.java")
        palantirJavaFormat("2.103.0")
    }
    kotlin {
        target("testing/**/src/**/*.kt", "buildSrc/src/**/*.kt")
        ktlint()
    }
    kotlinGradle {
        target(
            "*.gradle.kts",
            "buildSrc/*.gradle.kts",
            "modules/*/*.gradle.kts",
            "testing/**/*.gradle.kts",
            "examples/*/*.gradle.kts",
            "tools/*/*.gradle.kts",
        )
        ktlint()
    }
}
