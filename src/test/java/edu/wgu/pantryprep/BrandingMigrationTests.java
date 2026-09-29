package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.HexFormat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class BrandingMigrationTests {
    @Test
    void upgradesExistingSchemaAndSupportsBothApplicationVersions() throws Exception {
        // Independent production checksum: a fresh database alone cannot detect
        // edits to a previously applied migration.
        byte[] v12 = Files.readAllBytes(Path.of("src/main/resources/db/migration/V12__audit_log.sql"));
        assertEquals("b7bd9e19bf62b40ae33be6a756dfbd7efe4461a9cd3f81859c58a820ecbc11a8",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v12)));

        try (var postgres = new PostgreSQLContainer("postgres:17")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("15").load().migrate();
            var flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load();
            // Later migrations run here too, so look for V16 by version
            // instead of counting what ran.
            var upgrade = flyway.migrate();
            assertTrue(upgrade.migrations.stream().anyMatch(migration -> "16".equals(migration.version)),
                    "upgrading from V15 applies the branding migration");
            flyway.validate();
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE audit_probe (id BIGINT, value TEXT)");
                sql.execute("CREATE TRIGGER probe AFTER INSERT ON audit_probe "
                        + "FOR EACH ROW EXECUTE FUNCTION audit_row_change()");
                for (String prefix : new String[]{"pantryplan", "pantryprep"}) {
                    connection.setAutoCommit(false);
                    sql.execute("SELECT set_config('" + prefix + ".actor', 'cook@example.com', true), "
                            + "set_config('" + prefix + ".impersonator', 'admin@example.com', true)");
                    sql.execute("INSERT INTO audit_probe VALUES (1, 'after upgrade')");
                    try (var result = sql.executeQuery("SELECT actor, impersonator FROM audit_log "
                            + "WHERE table_name = 'audit_probe' ORDER BY id DESC LIMIT 1")) {
                        assertTrue(result.next());
                        assertEquals("cook@example.com", result.getString(1));
                        assertEquals("admin@example.com", result.getString(2));
                    }
                    connection.commit();
                }
            }
        }
    }
}
