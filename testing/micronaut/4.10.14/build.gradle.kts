plugins {
    java
    id("io.spring.dependency-management")
}

// The BOM imported the way a Maven caller's build does, so it may pull the client's Jackson down.
dependencyManagement {
    imports { mavenBom("io.micronaut.platform:micronaut-platform:4.10.14") }
}

dependencies {
    testImplementation(project(":modules:moneta-api-micronaut"))
    testImplementation("io.micronaut:micronaut-context")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets.test {
    java.srcDir("../shared/src/test/java")
    resources.srcDir("../shared/src/test/resources")
}
