// Everything a Play version shares; each version's folder only names its play-guice.
subprojects {
    apply(plugin = "java")

    dependencies {
        "testImplementation"(project(":modules:moneta-api-play"))
        "testImplementation"(platform("org.junit:junit-bom:6.1.3"))
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
