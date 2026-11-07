plugins {
    application
}

description = "Reads the live MONETA account for finding out what the API does; internal, not published"

dependencies {
    implementation(project(":modules:moneta-api"))
    // Masks and rewrites a movement's raw JSON, which the client keeps as a string.
    implementation("tools.jackson.core:jackson-databind:3.2.4")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// make peek runs the installed start script under op run, never through Gradle, so the token does not stay in a
// Gradle daemon's environment after the build.
application {
    mainClass = "peek.Peek"
    applicationName = "peek"
}
