plugins {
    java
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports { mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.12") }
    // The fix in .docs/compatibility.md for Spring Boot 3 before 3.5.13; without it this project fails with NoClassDefFoundError.
    dependencies { dependency("com.fasterxml.jackson.core:jackson-annotations:2.21") }
}

dependencies {
    testImplementation(project(":spring-boot-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-json")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets.test {
    java.srcDir("../shared/src/test/java")
    resources.srcDir("../shared/src/test/resources")
}
