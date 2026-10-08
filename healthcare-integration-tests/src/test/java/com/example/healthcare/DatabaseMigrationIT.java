package com.example.healthcare;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the application Flyway baseline (plan rows 5.2 and 5.3 of
 * {@code plans/spring-boot/01-foundation.md}) against a real PostgreSQL 17
 * container.
 *
 * <p>The migration scripts are not duplicated here: Flyway resolves them from
 * the {@code healthcare-application} artifact on the test classpath, so this
 * test proves the scripts that ship with the application.
 *
 * <p>Indexes are asserted through {@code pg_indexes} because PostgreSQL's
 * {@code information_schema} exposes tables, columns, and constraints but has
 * no index catalog.
 */
@Testcontainers
class DatabaseMigrationIT {

    /** Image tag matches the local stack in {@code docker-compose.yml}. */
    private static final String POSTGRES_IMAGE = "postgres:17.11";

    /**
     * Migration scripts live at {@code db/migration} in the application
     * module's resources. When the module is consumed as the Spring Boot
     * executable (fat) jar on the test classpath, that directory sits under
     * {@code BOOT-INF/classes}. Both layouts are scanned; exactly one is
     * present on a given classpath.
     */
    private static final String[] MIGRATION_LOCATIONS = {
        "classpath:db/migration", "classpath:BOOT-INF/classes/db/migration"
    };

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(POSTGRES_IMAGE)
                    .withDatabaseName("healthcare")
                    .withUsername("healthcare")
                    .withPassword("healthcare");

    private static Flyway flyway;
    private static MigrateResult firstRun;

    @BeforeAll
    static void migrateBaseline() {
        // The PostgreSQL JDBC driver sends the JVM default timezone in its
        // startup packet, and the server rejects legacy aliases such as
        // Asia/Calcutta. Pin UTC so the test is independent of the host zone.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(MIGRATION_LOCATIONS)
                .load();
        firstRun = flyway.migrate();
    }

    @Test
    void firstRunAppliesBothMigrations() {
        assertThat(firstRun.success).isTrue();
        assertThat(firstRun.migrationsExecuted).isEqualTo(2);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
    }

    @Test
    void secondRunAppliesNothing() {
        MigrateResult secondRun = flyway.migrate();

        assertThat(secondRun.success).isTrue();
        assertThat(secondRun.migrationsExecuted).isZero();
    }

    @Test
    void bothTablesExist() throws SQLException {
        List<String> tables = queryStrings(
                "SELECT table_name FROM information_schema.tables"
                        + " WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
                        + " AND table_name IN ('inbound_messages', 'resource_mappings')");

        assertThat(tables).containsExactlyInAnyOrder("inbound_messages", "resource_mappings");
    }

    @Test
    void inboundMessagesHasExpectedColumns() throws SQLException {
        assertThat(columnNames("inbound_messages"))
                .containsExactly(
                        "id",
                        "source_system",
                        "message_control_id",
                        "message_type",
                        "processing_status",
                        "received_at",
                        "processed_at",
                        "error_code",
                        "error_summary");
    }

    @Test
    void resourceMappingsHasExpectedColumns() throws SQLException {
        assertThat(columnNames("resource_mappings"))
                .containsExactly(
                        "id",
                        "source_system",
                        "source_identifier_system",
                        "source_identifier_value",
                        "fhir_resource_type",
                        "fhir_resource_id",
                        "created_at",
                        "updated_at");
    }

    @Test
    void inboundMessagesUniqueConstraintCoversSourceSystemAndMessageControlId() throws SQLException {
        assertThat(uniqueConstraintColumns("inbound_messages", "uq_source_message"))
                .containsExactly("source_system", "message_control_id");
    }

    @Test
    void resourceMappingsUniqueConstraintCoversTheSourceResource() throws SQLException {
        assertThat(uniqueConstraintColumns("resource_mappings", "uq_source_resource"))
                .containsExactly(
                        "source_system",
                        "source_identifier_system",
                        "source_identifier_value",
                        "fhir_resource_type");
    }

    @Test
    void bothIndexesExistWithExpectedColumns() throws SQLException {
        assertThat(indexDefinition("ix_inbound_messages_status_received"))
                .contains("(processing_status, received_at)");
        assertThat(indexDefinition("ix_resource_mappings_fhir"))
                .contains("(fhir_resource_type, fhir_resource_id)");
    }

    @Test
    void timestampColumnsUseTimestamptz() throws SQLException {
        assertThat(columnDataType("inbound_messages", "received_at")).isEqualTo("timestamp with time zone");
        assertThat(columnDataType("inbound_messages", "processed_at")).isEqualTo("timestamp with time zone");
        assertThat(columnDataType("resource_mappings", "created_at")).isEqualTo("timestamp with time zone");
        assertThat(columnDataType("resource_mappings", "updated_at")).isEqualTo("timestamp with time zone");
    }

    @Test
    void receivedAtIsRequiredAndProcessedAtIsOptional() throws SQLException {
        assertThat(columnNullable("inbound_messages", "received_at")).isEqualTo("NO");
        assertThat(columnNullable("inbound_messages", "processed_at")).isEqualTo("YES");
        assertThat(columnNullable("resource_mappings", "created_at")).isEqualTo("NO");
        assertThat(columnNullable("resource_mappings", "updated_at")).isEqualTo("NO");
    }

    private static List<String> columnNames(String table) throws SQLException {
        return queryStrings(
                "SELECT column_name FROM information_schema.columns"
                        + " WHERE table_schema = 'public' AND table_name = ?"
                        + " ORDER BY ordinal_position",
                table);
    }

    private static List<String> uniqueConstraintColumns(String table, String constraint) throws SQLException {
        return queryStrings(
                "SELECT kcu.column_name"
                        + " FROM information_schema.table_constraints tc"
                        + " JOIN information_schema.key_column_usage kcu"
                        + " ON kcu.constraint_catalog = tc.constraint_catalog"
                        + " AND kcu.constraint_schema = tc.constraint_schema"
                        + " AND kcu.constraint_name = tc.constraint_name"
                        + " WHERE tc.table_schema = 'public' AND tc.table_name = ?"
                        + " AND tc.constraint_name = ? AND tc.constraint_type = 'UNIQUE'"
                        + " ORDER BY kcu.ordinal_position",
                table,
                constraint);
    }

    private static String indexDefinition(String indexName) throws SQLException {
        return querySingle(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                indexName);
    }

    private static String columnDataType(String table, String column) throws SQLException {
        return querySingle(
                "SELECT data_type FROM information_schema.columns"
                        + " WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                table,
                column);
    }

    private static String columnNullable(String table, String column) throws SQLException {
        return querySingle(
                "SELECT is_nullable FROM information_schema.columns"
                        + " WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                table,
                column);
    }

    private static String querySingle(String sql, Object... parameters) throws SQLException {
        List<String> values = queryStrings(sql, parameters);
        assertThat(values).as("rows for: %s", sql).hasSize(1);
        return values.get(0);
    }

    private static List<String> queryStrings(String sql, Object... parameters) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                }
            }
        }
        return values;
    }
}
