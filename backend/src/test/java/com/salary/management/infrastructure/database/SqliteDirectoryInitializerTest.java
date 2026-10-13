package com.salary.management.infrastructure.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqliteDirectoryInitializerTest {

    private final SqliteDirectoryInitializer initializer =
            new SqliteDirectoryInitializer();

    @Test
    void initialize_createsParentDirectory_whenMissing(@TempDir Path tempDir) {
        Path dbFile = tempDir.resolve("nested/data/app.db");
        String jdbcUrl = "jdbc:sqlite:" + dbFile;

        assertFalse(dbFile.getParent().toFile().exists());

        initializer.initialize(contextWithUrl(jdbcUrl));

        assertTrue(dbFile.getParent().toFile().exists(),
                "Expected the parent directory to be created");
    }

    @Test
    void initialize_doesNothing_whenDirectoryAlreadyExists(@TempDir Path tempDir) {
        Path dbFile = tempDir.resolve("data/app.db");
        File parent = dbFile.getParent().toFile();
        parent.mkdirs();

        initializer.initialize(contextWithUrl("jdbc:sqlite:" + dbFile));

        assertTrue(parent.exists());
    }

    @Test
    void initialize_doesNothing_forInMemoryDatabase() {
        // Should not throw, should not create files
        initializer.initialize(contextWithUrl("jdbc:sqlite::memory:"));
    }

    @Test
    void initialize_doesNothing_forNonSqliteUrl() {
        // Should not throw, should not create files
        initializer.initialize(contextWithUrl("jdbc:postgresql://localhost/db"));
    }

    // --- Helper ---

    private ConfigurableApplicationContext contextWithUrl(String jdbcUrl) {
        ConfigurableEnvironment env = mock(ConfigurableEnvironment.class);
        when(env.getProperty("spring.datasource.url")).thenReturn(jdbcUrl);

        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(context.getEnvironment()).thenReturn(env);

        return context;
    }
}