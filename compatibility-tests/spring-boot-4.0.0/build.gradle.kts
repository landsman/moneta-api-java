plugins {
    java
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports { mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.0") }
}

dependencies {
    testImplementation(project(":"))
    testImplementation("org.springframework.boot:spring-boot-starter-json")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets.test { java.srcDir("../spring-boot/src/test/java") }
