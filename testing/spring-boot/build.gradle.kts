// Everything a Spring Boot version shares; each version's folder only imports its BOM.
subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    dependencies {
        "testImplementation"(project(":modules:moneta-api-spring-boot-starter"))
        "testImplementation"("org.springframework.boot:spring-boot-starter-json")
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    configure<SourceSetContainer> {
        named("test") {
            java.srcDir("../shared/src/test/java")
            resources.srcDir("../../resources") // one MONETA answer for every testing project
        }
    }
}
