plugins {
    java
}

dependencies {
    testImplementation(project(":modules:moneta-api-play"))
    testImplementation("org.playframework:play-guice_3:3.0.12")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets.test {
    java.srcDir("../shared/src/test/java")
    resources.srcDir("../shared/src/test/resources")
}
