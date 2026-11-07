plugins {
    `java-library`
}

description = "A Play Framework module for the MONETA API client"

dependencies {
    api(project(":modules:moneta-api"))
    compileOnly("org.jspecify:jspecify:1.0.1")
    // The application's own Play is what runs it; testing/play checks each version. Dependabot leaves it alone
    // (.github/dependabot.yml). The Java API in play.inject is the same class in play_2.13 and play_3.
    compileOnly("org.playframework:play_2.13:3.0.12")
    compileOnly("com.typesafe:config:1.4.9")
    compileOnly("javax.inject:javax.inject:1")

    testImplementation("com.typesafe:config:1.4.9")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
    withJavadocJar()
}
