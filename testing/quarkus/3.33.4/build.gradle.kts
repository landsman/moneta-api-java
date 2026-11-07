plugins {
    java
    id("io.quarkus") version "3.40.1.1"
}

dependencies {
    // The platform BOM, as code.quarkus.io writes it for a caller.
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.33.4"))
    implementation(project(":modules:moneta-api-quarkus"))
    implementation("io.quarkus:quarkus-arc")
    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("org.junit.jupiter:junit-jupiter")
}

sourceSets.main {
    java.srcDir("../shared/src/main/java")
}

sourceSets.test {
    java.srcDir("../shared/src/test/java")
    resources.srcDir("../../resources") // one MONETA answer for every testing project
}

// quarkusIntTest: the built application, JVM or native; FakeMoneta comes from the test sources.
sourceSets.named("integrationTest") {
    java.srcDir("../shared/src/integrationTest/java")
    java.srcDir("../shared/src/test/java")
    resources.srcDir("../../resources")
}

// The packaged application too, in make build; a few seconds on the JVM.
tasks.check { dependsOn(tasks.named("quarkusIntTest")) }
