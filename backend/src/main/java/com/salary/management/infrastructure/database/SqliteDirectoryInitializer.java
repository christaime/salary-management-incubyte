package com.salary.management.infrastructure.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.io.File;

/**
 * Ensures the parent directory of a SQLite database file exists before the
 * Spring context (and therefore Flyway) starts.
 */
public class SqliteDirectoryInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log =
            LoggerFactory.getLogger(SqliteDirectoryInitializer.class);

    private static final String SQLITE_PREFIX = "jdbc:sqlite:";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment env = context.getEnvironment();
        String jdbcUrl = env.getProperty("spring.datasource.url");
        ensureSqliteDirectoryExists(jdbcUrl);
    }

    private void ensureSqliteDirectoryExists(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith(SQLITE_PREFIX)) {
            return;
        }

        String path = jdbcUrl.substring(SQLITE_PREFIX.length());

        if (path.isBlank() || path.startsWith(":memory:")) {
            return;
        }

        File dbFile = new File(path);
        File parentDir = dbFile.getAbsoluteFile().getParentFile();

        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            if (created) {
                log.info("Created SQLite data directory: {}", parentDir);
            } else {
                log.warn("Could not create SQLite data directory: {}", parentDir);
            }
        }
    }
}