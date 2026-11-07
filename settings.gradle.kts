rootProject.name = "moneta-api-java"

// What ships: the client, and the Spring Boot starter around it.
include(":modules:moneta-api", ":modules:moneta-api-spring-boot-starter")

// Callers the library promises to work for, each on its own classpath; see testing/README.md.
include(
    ":testing:spring-boot:3.5.12",
    ":testing:spring-boot:3.5.16",
    ":testing:spring-boot:4.0.0",
    ":testing:spring-boot:4.1.2",
    ":testing:kotlin",
)

// make dev
include(":examples:cafe")

// make peek: reads the live account, for finding out what MONETA does; never published
include(":tools:peek")
