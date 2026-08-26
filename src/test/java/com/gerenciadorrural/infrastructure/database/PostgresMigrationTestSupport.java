package com.gerenciadorrural.infrastructure.database;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

@Testcontainers
abstract class PostgresMigrationTestSupport {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15.8-alpine");

    @BeforeAll
    static void applyRealMigrations() throws IOException, SQLException {
        Path migrationsDirectory = Path.of("supabase", "migrations").toAbsolutePath().normalize();
        List<Path> migrations;
        try (var paths = Files.list(migrationsDirectory)) {
            migrations = paths
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }

        if (migrations.isEmpty()) {
            throw new IllegalStateException("Nenhuma migration SQL foi encontrada em " + migrationsDirectory);
        }

        try (Connection connection = adminConnection()) {
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

    @BeforeEach
    void clearApplicationData() throws SQLException {
        executeAsAdmin("""
                truncate table
                    app.membership_farm_scopes,
                    app.organization_memberships,
                    app.farms,
                    app.organizations,
                    app.users
                """);
    }

    protected static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }

    protected static Connection apiConnection() throws SQLException {
        Connection connection = adminConnection();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("set role app_api");
        }
        return connection;
    }

    protected static void setTenant(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select set_config('app.current_tenant_id', ?, true)")) {
            statement.setString(1, tenantId.toString());
            statement.execute();
        }
    }

    protected static void executeAsAdmin(String sql, Object... parameters) throws SQLException {
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.executeUpdate();
        }
    }

    protected static void execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }
}
