// The rest is shared, in ../build.gradle.kts.
// The BOM imported the way a Maven caller's build does, so it may pull the client's Jackson down.
dependencyManagement { imports { mavenBom("io.micronaut.platform:micronaut-platform:5.2.5") } }

dependencies {
    testRuntimeOnly("org.slf4j:slf4j-jdk-platform-logging:2.0.18") // the BOM's SLF4J; it does not manage this one
}

// Micronaut 5's jars are Java 25 bytecode, so this caller is compiled for 25 and runs on it whatever -PtestsJdk
// says. The module it tests stays release 21.
tasks.withType<JavaCompile>().configureEach { options.release = 25 }
tasks.test {
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}
