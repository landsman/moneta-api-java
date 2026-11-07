// Everything a Micronaut version shares; each version's folder imports its BOM and the SLF4J provider matching it.
subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    dependencies {
        "testImplementation"(project(":modules:moneta-api-micronaut"))
        "testImplementation"("io.micronaut:micronaut-context")
        // What the module README tells a Micronaut application to add, so the client's System.Logger reaches Logback.
        "testImplementation"("ch.qos.logback:logback-classic")
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    configure<SourceSetContainer> {
        named("test") {
            java.srcDir("../shared/src/test/java")
            resources.srcDir("../../resources") // one MONETA answer for every testing project
            resources.srcDir("../shared/src/test/resources")
        }
    }
}
