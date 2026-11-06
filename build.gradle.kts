plugins {
    `java-library`
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
}

tasks.test {
    useJUnitPlatform()
}
