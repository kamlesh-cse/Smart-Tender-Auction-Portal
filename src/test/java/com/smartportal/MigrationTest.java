package com.smartportal;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MigrationTest {
    @Test
    void baselineMigrationsBuildFreshH2Database() {
        Flyway flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:migration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("filesystem:src/main/resources/db/migration")
                // V3 uses PostgreSQL-only cast syntax; it is covered by PostgreSQL CI.
                .target("2")
                .load();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.validateWithResult().invalidMigrations.size());
    }
}