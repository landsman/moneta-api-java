// The rest is shared, in ../build.gradle.kts.
dependencies {
    testImplementation("org.playframework:play-guice_2.13:3.0.12")
    // What every Play application has: the sbt plugin adds it, and it configures Logback from logback.xml. On the
    // compile classpath too, for the test to read what reached Logback.
    testImplementation("org.playframework:play-logback_2.13:3.0.12")
}
