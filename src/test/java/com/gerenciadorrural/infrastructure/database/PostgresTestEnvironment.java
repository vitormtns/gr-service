package com.gerenciadorrural.infrastructure.database;

import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

public final class PostgresTestEnvironment {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15.8-alpine");
    private static final String RUNTIME_USERNAME = "app_test_runtime";
    private static final String RUNTIME_PASSWORD = "test-" + UUID.randomUUID();
    private static boolean initialized;

    private PostgresTestEnvironment() {
    }

    public static synchronized void start() {
        if (initialized) {
            return;
        }
        POSTGRES.start();
        try {
            applyRealMigrations();
            createRuntimeLogin();
            initialized = true;
        } catch (IOException | SQLException exception) {
            POSTGRES.stop();
            throw new IllegalStateException("Não foi possível preparar o PostgreSQL de teste", exception);
        }
    }

    public static String jdbcUrl() {
        start();
        return POSTGRES.getJdbcUrl();
    }

    public static String runtimeUsername() {
        start();
        return RUNTIME_USERNAME;
    }

    public static String runtimePassword() {
        start();
        return RUNTIME_PASSWORD;
    }

    public static Connection adminConnection() throws SQLException {
        start();
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    public static Connection runtimeConnection() throws SQLException {
        start();
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), RUNTIME_USERNAME, RUNTIME_PASSWORD);
    }

    public static void clearUsers() throws SQLException {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    truncate table
                        app.membership_farm_scopes,
                        app.organization_memberships,
                        app.farms,
                        app.organizations,
                        app.users
                    """);
        }
    }

    private static void applyRealMigrations() throws IOException, SQLException {
        Path directory = Path.of("supabase", "migrations").toAbsolutePath().normalize();
        List<Path> migrations;
        try (var paths = Files.list(directory)) {
            migrations = paths.filter(path -> path.getFileName().toString().endsWith(".sql")).sorted().toList();
        }
        if (migrations.isEmpty()) {
            throw new IllegalStateException("Nenhuma migration SQL foi encontrada em " + directory);
        }
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (Path migration : migrations) {
                    statement.execute(Files.readString(migration, StandardCharsets.UTF_8));
                }
                connection.commit();
            } catch (IOException | SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void createRuntimeLogin() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("create role " + RUNTIME_USERNAME
                    + " login noinherit nosuperuser nocreatedb nocreaterole noreplication nobypassrls password '"
                    + RUNTIME_PASSWORD + "'");
            statement.execute("grant app_api to " + RUNTIME_USERNAME);
        }
    }
}
