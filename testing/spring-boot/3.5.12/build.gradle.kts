dependencyManagement {
    imports { mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.12") }
    // The fix in .docs/compatibility.md for Spring Boot 3 before 3.5.13; without it this project fails with NoClassDefFoundError.
    dependencies { dependency("com.fasterxml.jackson.core:jackson-annotations:2.21") }
}
