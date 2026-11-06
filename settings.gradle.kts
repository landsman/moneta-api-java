rootProject.name = "moneta-api-java"

// Callers the library promises to work for, each its own build classpath; see compat/README.md.
include(":compat:spring-boot-3.5.12", ":compat:spring-boot-3.5.16", ":compat:spring-boot-4.0.0", ":compat:kotlin")
