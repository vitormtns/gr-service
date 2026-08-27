package com.gerenciadorrural.modules.organizations.infrastructure;

import com.gerenciadorrural.modules.organizations.domain.AccessibleOrganization;
import com.gerenciadorrural.modules.organizations.domain.AccessibleOrganizationRepository;
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
public class JdbcAccessibleOrganizationRepository implements AccessibleOrganizationRepository {

    private static final RowMapper<AccessibleOrganization> ROW_MAPPER =
            JdbcAccessibleOrganizationRepository::mapAccessibleOrganization;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionalCurrentUserContext currentUserContext;

    public JdbcAccessibleOrganizationRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            TransactionalCurrentUserContext currentUserContext
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserContext = currentUserContext;
    }

    @Override
    public List<AccessibleOrganization> findActiveForCurrentUser(UUID userId) {
        currentUserContext.configure(userId);
        return jdbcTemplate.query(
                "select organization_id, organization_name, membership_id, role_key, farm_scope_mode "
                        + "from app.list_current_user_organizations()",
                ROW_MAPPER
        );
    }

    private static AccessibleOrganization mapAccessibleOrganization(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new AccessibleOrganization(
                resultSet.getObject("organization_id", UUID.class),
                resultSet.getString("organization_name"),
                resultSet.getObject("membership_id", UUID.class),
                resultSet.getString("role_key"),
                resultSet.getString("farm_scope_mode")
        );
    }
}
