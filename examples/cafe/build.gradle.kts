plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":modules:moneta-api-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // Bills are rows in a database, the only record of which one is paid; H2 in a file keeps the example
    // self-contained.
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    runtimeOnly("com.h2database:h2")
    // The QR payment a bill is shown as: the SPAYD string and its PNG, checked by its author against the format's
    // own API. An example's dependency, not the library's.
    implementation("io.github.janhalasa:spayd-qr-java:1.0.2")
    // An API reference to try the endpoints by hand, at /scalar; the OpenAPI document is at /v3/api-docs.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-scalar:3.2.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jdbc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
