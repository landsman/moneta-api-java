rootProject.name = "moneta-api-java"

// Callers the library promises to work for, each on its own classpath; see testing/README.md.
include(
    ":testing:spring-boot:3.5.12",
    ":testing:spring-boot:3.5.16",
    ":testing:spring-boot:4.0.0",
    ":testing:spring-boot:4.1.2",
    ":testing:kotlin",
    ":examples:cafe",
)
