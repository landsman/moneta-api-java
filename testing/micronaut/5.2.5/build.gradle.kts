plugins {
    java
    id("io.spring.dependency-management")
}

// The BOM imported the way a Maven caller's build does, so it may pull the client's Jackson down.
dependencyManagement {
    imports { mavenBom("io.micronaut.platform:micronaut-platform:5.2.5") }
}

dependencies {
    testImplementation(project(":modules:moneta-api-micronaut"))
    testImplementation("io.micronaut:micronaut-context")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets.test {
    java.srcDir("../shared/src/test/java")
    resources.srcDir("../../resources") // one MONETA answer for every testing project
}

// Micronaut 5's jars are Java 25 bytecode, so this caller is compiled for 25 and runs on it whatever -PtestsJdk
// says. The module it tests stays release 21.
tasks.withType<JavaCompile>().configureEach { options.release = 25 }
tasks.test {
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}
