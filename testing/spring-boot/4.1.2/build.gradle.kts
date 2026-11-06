plugins {
    java
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports { mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.2") }
}

dependencies {
    testImplementation(project(":"))
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets.test {
    java.srcDir("../shared/src/test/java")
    resources.srcDir("../shared/src/test/resources")
}

// `make dev`: the library in a running Spring Boot app, against the account MONETA_TOKEN opens.
tasks.register<JavaExec>("dev") {
    description = "Runs a Spring Boot app that reads MONETA through the library, on http://localhost:8080."
    group = "application"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "example.dev.DevApplication"
}
