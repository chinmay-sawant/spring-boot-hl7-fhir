# Dependency versions

Every version here was read from Maven Central metadata or from the resolved
Maven build on 2026-10-08. No version is guessed. The root `pom.xml` holds
`hapi-hl7v2.version`, `hapi-fhir.version`, and `archunit.version`; Spring Boot
dependency management sets Flyway, Testcontainers, the PostgreSQL driver, and
JUnit.

## Toolchain

| Tool | Version | Source |
|---|---|---|
| JDK | Eclipse Temurin 17.0.20+8 | `java -version` and `./mvnw -v` in this environment |
| Maven | 3.9.6, pinned by the wrapper | [apache-maven-3.9.6-bin.zip](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip) |
| Java release target | 17 | `java.version` and `maven.compiler.release` in the root pom |

## Build and test dependencies

| Component | Coordinates | Version | Source |
|---|---|---|---|
| Spring Boot parent | `org.springframework.boot:spring-boot-starter-parent` | 3.5.16 | [Central metadata](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml): newest non-milestone 3.x release (4.x milestones and releases exist, the project baseline is 3.x). Runs on Java 17 to 25 per [system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html) |
| HAPI HL7v2 core | `ca.uhn.hapi:hapi-base` | 2.6.0 | [Central metadata](https://repo.maven.apache.org/maven2/ca/uhn/hapi/hapi-base/maven-metadata.xml): newest release. Java 11 bytecode (major version 55), checked with `javap`, runs on JDK 17 |
| HAPI HL7v2 v2.5.1 structures | `ca.uhn.hapi:hapi-structures-v251` | 2.6.0 | [Central metadata](https://repo.maven.apache.org/maven2/ca/uhn/hapi/hapi-structures-v251/maven-metadata.xml): same release line as `hapi-base` |
| HAPI FHIR client | `ca.uhn.hapi.fhir:hapi-fhir-client` | 8.12.1 | [Central metadata](https://repo.maven.apache.org/maven2/ca/uhn/hapi/fhir/hapi-fhir-client/maven-metadata.xml): newest release |
| HAPI FHIR R4 structures | `ca.uhn.hapi.fhir:hapi-fhir-structures-r4` | 8.12.1 | [Central metadata](https://repo.maven.apache.org/maven2/ca/uhn/hapi/fhir/hapi-fhir-structures-r4/maven-metadata.xml): same release line. Minimum JDK 17 per the [HAPI FHIR versions page](https://hapifhir.io/hapi-fhir/docs/getting_started/versions.html) |
| ArchUnit JUnit 5 | `com.tngtech.archunit:archunit-junit5` | 1.5.1 | [Central metadata](https://repo.maven.apache.org/maven2/com/tngtech/archunit/archunit-junit5/maven-metadata.xml): newest 1.x release |
| Maven Wrapper plugin | `org.apache.maven.plugins:maven-wrapper-plugin` | 3.3.4 | Resolved by Maven during `mvn -N wrapper:wrapper -Dmaven=3.9.6`; wrapper type `only-script` |

## Managed by Spring Boot 3.5.16

Spring Boot sets these versions through `spring-boot-dependencies`. The values
below are read from this build's `dependency:tree` output. Flyway 10 and later
ship per-database support as separate modules, so `healthcare-application`
declares `flyway-core` and `flyway-database-postgresql` at the managed version.

| Artifact | Version | Scope | Source |
|---|---|---|---|
| `org.flywaydb:flyway-core` | 11.7.2 | compile | [spring-boot-dependencies 3.5.16](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.16/spring-boot-dependencies-3.5.16.pom) |
| `org.flywaydb:flyway-database-postgresql` | 11.7.2 | compile | same POM |
| `org.postgresql:postgresql` | 42.7.11 | runtime | same POM |
| `org.testcontainers:junit-jupiter` | 1.21.4 | test | same POM (through the Testcontainers BOM) |
| `org.testcontainers:postgresql` | 1.21.4 | test | same POM |
| `org.junit.jupiter:junit-jupiter` | 5.12.2 | test | same POM (through `spring-boot-starter-test`) |

Build plugins inherited from the parent, observed in the build log:
maven-compiler-plugin 3.14.1, maven-jar-plugin 3.4.2, maven-surefire-plugin
3.5.6, maven-failsafe-plugin 3.5.6.

## Container images

The phase 2 build starts no containers. The local stack phase pins these tags
and records them in this table.

| Image | Tag | Status |
|---|---|---|
| `postgres` | pending | To be pinned by the local stack phase |
| `hapiproject/hapi` | pending | To be pinned by the local stack phase |
