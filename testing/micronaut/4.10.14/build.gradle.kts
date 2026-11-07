// The rest is shared, in ../build.gradle.kts.
// The BOM imported the way a Maven caller's build does, so it may pull the client's Jackson down.
dependencyManagement { imports { mavenBom("io.micronaut.platform:micronaut-platform:4.10.14") } }

dependencies {
    testRuntimeOnly("org.slf4j:slf4j-jdk-platform-logging:2.0.17") // the BOM's SLF4J; it does not manage this one
}
