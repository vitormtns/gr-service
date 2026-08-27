package com.gerenciadorrural.modules.organizations.infrastructure;

import com.gerenciadorrural.modules.organizations.domain.AccessibleFarm;
import com.gerenciadorrural.modules.organizations.domain.AccessibleFarmRepository;
import com.gerenciadorrural.shared.infrastructure.database.TransactionalCurrentUserContext;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcAccessibleFarmRepository implements AccessibleFarmRepository {

    private static final RowMapper<AccessibleFarm> ROW_MAPPER = JdbcAccessibleFarmRepository::mapFarm;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionalCurrentUserContext currentUserContext;

    public JdbcAccessibleFarmRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            TransactionalCurrentUserContext currentUserContext
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserContext = currentUserContext;
    }

    @Override
    public List<AccessibleFarm> findForCurrentUserAndOrganization(UUID userId, UUID organizationId) {
        currentUserContext.configure(userId);
        return jdbcTemplate.query(
                "select farm_id, farm_name from app.list_current_user_accessible_farms(:organizationId)",
                new MapSqlParameterSource("organizationId", organizationId),
                ROW_MAPPER
        );
    }

    private static AccessibleFarm mapFarm(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AccessibleFarm(
                resultSet.getObject("farm_id", UUID.class),
                resultSet.getString("farm_name")
        );
    }
}
