plugins {
    `java-library`
}

group = rootProject.group
version = rootProject.version

base {
    archivesName = "moneta-api-spring-boot-starter"
}

// Compiled against the oldest Spring Boot it supports; the application's own Spring Boot is what runs it, and
// testing/spring-boot checks it on each version. Dependabot leaves this BOM alone (.github/dependabot.yml).
val springBoot = dependencies.platform("org.springframework.boot:spring-boot-dependencies:3.5.16")

dependencies {
    api(project(":"))
    compileOnly(springBoot)
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    // Writes the metadata IntelliJ reads to complete moneta.* in application.yml.
    annotationProcessor(springBoot)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(springBoot)
    testImplementation("org.springframework.boot:spring-boot-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-test")
    // ApplicationContextRunner hands the test an AssertJ type, so it has to be on the classpath.
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
}
