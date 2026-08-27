package com.gerenciadorrural.modules.identity.infrastructure;

import com.gerenciadorrural.modules.identity.domain.InternalUser;
import com.gerenciadorrural.modules.identity.domain.InternalUserRepository;
import com.gerenciadorrural.modules.identity.domain.InternalUserStatus;
import com.gerenciadorrural.shared.infrastructure.database.TransactionalDatabaseRole;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcInternalUserRepository implements InternalUserRepository {

    private static final String COLUMNS = "id, email, display_name, status, created_at, updated_at, version";
    private static final RowMapper<InternalUser> ROW_MAPPER = JdbcInternalUserRepository::mapUser;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionalDatabaseRole databaseRole;

    public JdbcInternalUserRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            TransactionalDatabaseRole databaseRole
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseRole = databaseRole;
    }

    @Override
    public Optional<InternalUser> findById(UUID id) {
        databaseRole.assumeApplicationRole();
        List<InternalUser> users = jdbcTemplate.query(
                "select " + COLUMNS + " from app.users where id = :id",
                new MapSqlParameterSource("id", id),
                ROW_MAPPER
        );
        return users.stream().findFirst();
    }

    @Override
    public boolean insert(InternalUser user) {
        databaseRole.assumeApplicationRole();
        int rows = jdbcTemplate.update("""
                insert into app.users
                    (id, email, display_name, status, created_at, updated_at, version)
                values
                    (:id, :email, :displayName, :status, :createdAt, :updatedAt, :version)
                on conflict (id) do nothing
                """, parameters(user));
        return rows == 1;
    }

    @Override
    public Optional<InternalUser> updateIdentity(
            UUID id,
            Optional<String> email,
            Optional<String> displayName,
            Instant updatedAt,
            long expectedVersion
    ) {
        databaseRole.assumeApplicationRole();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("email", email.orElse(null))
                .addValue("displayName", displayName.orElse(null))
                .addValue("updatedAt", timestamp(updatedAt))
                .addValue("expectedVersion", expectedVersion);
        List<InternalUser> users = jdbcTemplate.query("""
                update app.users
                set email = :email,
                    display_name = :displayName,
                    updated_at = :updatedAt,
                    version = version + 1
                where id = :id
                  and version = :expectedVersion
                returning id, email, display_name, status, created_at, updated_at, version
                """, parameters, ROW_MAPPER);
        return users.stream().findFirst();
    }

    private static MapSqlParameterSource parameters(InternalUser user) {
        return new MapSqlParameterSource()
                .addValue("id", user.id())
                .addValue("email", user.email().orElse(null))
                .addValue("displayName", user.displayName().orElse(null))
                .addValue("status", user.status().name())
                .addValue("createdAt", timestamp(user.createdAt()))
                .addValue("updatedAt", timestamp(user.updatedAt()))
                .addValue("version", user.version());
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static InternalUser mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
        return new InternalUser(
                resultSet.getObject("id", UUID.class),
                Optional.ofNullable(resultSet.getString("email")),
                Optional.ofNullable(resultSet.getString("display_name")),
                InternalUserStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant(),
                resultSet.getLong("version")
        );
    }
}
