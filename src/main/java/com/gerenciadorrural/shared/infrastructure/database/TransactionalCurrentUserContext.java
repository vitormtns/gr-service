package com.gerenciadorrural.shared.infrastructure.database;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TransactionalCurrentUserContext {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionalDatabaseRole databaseRole;

    public TransactionalCurrentUserContext(
            NamedParameterJdbcTemplate jdbcTemplate,
            TransactionalDatabaseRole databaseRole
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseRole = databaseRole;
    }

    public void configure(UUID userId) {
        databaseRole.assumeApplicationRole();
        jdbcTemplate.queryForObject(
                "select set_config('app.current_user_id', :userId, true)",
                new MapSqlParameterSource("userId", userId.toString()),
                String.class
        );
    }
}
