rootProject.name = "moneta-api-java"

// What ships: the client, and the Spring Boot starter, Play and Micronaut modules around it.
include(
    ":modules:moneta-api",
    ":modules:moneta-api-spring-boot-starter",
    ":modules:moneta-api-play",
    ":modules:moneta-api-micronaut",
)

// Callers the library promises to work for, each on its own classpath; see testing/README.md.
include(
    ":testing:spring-boot:3.5.12",
    ":testing:spring-boot:3.5.16",
    ":testing:spring-boot:4.0.0",
    ":testing:spring-boot:4.1.2",
    ":testing:micronaut:4.10.14",
    ":testing:micronaut:5.2.5",
    ":testing:kotlin",
    ":testing:play:3.0-scala-2.13",
    ":testing:play:3.0-scala-3",
    ":testing:play:2.9",
)

// make dev
include(":examples:cafe")

// make peek: reads the live account, for finding out what MONETA does; never published
include(":tools:peek")
