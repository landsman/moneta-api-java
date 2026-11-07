plugins {
    java
}

dependencies {
    implementation(project(":modules:moneta-api"))
}

// What bin/native-test.sh hands to native-image and to the JVM: the caller's jar and everything it needs.
tasks.register<Sync>("nativeClasspath") {
    description = "Collects the caller's runtime classpath into build/native/lib."
    from(tasks.jar, configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("native/lib"))
}
