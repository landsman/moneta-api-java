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
    // What the module README tells a Micronaut application to add, so the client's System.Logger reaches Logback.
    testImplementation("ch.qos.logback:logback-classic")
    testRuntimeOnly("org.slf4j:slf4j-jdk-platform-logging:2.0.17") // the BOM's SLF4J; it does not manage this one
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets.test {
    java.srcDir("../shared/src/test/java")
    resources.srcDir("../../resources") // one MONETA answer for every testing project
    resources.srcDir("../shared/src/test/resources")
}
