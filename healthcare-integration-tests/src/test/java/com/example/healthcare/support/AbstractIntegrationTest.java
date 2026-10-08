package com.example.healthcare.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.TimeZone;

import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Shared environment for the end-to-end tests (plan row 6.4 of
 * {@code plans/spring-boot/01-foundation.md}).
 *
 * <p>Nothing external runs: the application database is H2 in memory, and the
 * FHIR server is the in-process in-memory {@link RestfulServer} from
 * {@link InMemoryFhirServerConfiguration}, hosted on the same embedded Tomcat
 * at {@code /fhir/*}. No Docker, no PostgreSQL, no external FHIR server.
 *
 * <p>The server port is chosen up front so both {@code server.port} and
 * {@code healthcare.fhir.base-url} can point at it before the context starts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Import(InMemoryFhirServerConfiguration.class)
public abstract class AbstractIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    /** Single port for the application REST API and the in-memory FHIR endpoint. */
    private static final int TEST_PORT = findAvailablePort();

    static {
        // The PostgreSQL JDBC driver sends the JVM default time zone in its
        // startup packet, and the postgres:17.11 image rejects legacy aliases
        // such as Asia/Calcutta (this host runs in that zone). Pinning UTC
        // keeps the migration test independent of host zone configuration.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @DynamicPropertySource
    static void registerApplicationProperties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> TEST_PORT);
        registry.add("healthcare.fhir.base-url", () -> fhirBaseUrl());
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:mem:healthcare_it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @BeforeAll
    static void reportEnvironment() {
        LOGGER.info("Running against the in-memory FHIR server at {} (no Docker required)", fhirBaseUrl());
    }

    /** Base URL of the in-process FHIR server, as seen by the test JVM. */
    protected static String fhirBaseUrl() {
        return "http://localhost:" + TEST_PORT + "/fhir";
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("No free port available for the test server", e);
        }
    }
}
