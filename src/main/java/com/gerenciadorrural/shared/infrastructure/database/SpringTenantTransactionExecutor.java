package com.gerenciadorrural.shared.infrastructure.database;

import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionContextConflictException;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import com.gerenciadorrural.shared.tenancy.TenantTransactionInfrastructureException;
import com.gerenciadorrural.shared.tenancy.TenantTransactionalAction;
import com.gerenciadorrural.shared.tenancy.TenantTransactionalOperation;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;

@Component
public class SpringTenantTransactionExecutor implements TenantTransactionExecutor {

    private final TransactionTemplate transactions;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionalDatabaseRole databaseRole;

    public SpringTenantTransactionExecutor(
            TransactionTemplate transactions,
            NamedParameterJdbcTemplate jdbc,
            TransactionalDatabaseRole databaseRole
    ) {
        this.transactions = transactions;
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.jdbc = jdbc;
        this.databaseRole = databaseRole;
    }

    @Override
    public <T> T execute(TenantContext context, TenantTransactionalOperation<T> operation) {
        Objects.requireNonNull(context, "O contexto de tenant é obrigatório");
        Objects.requireNonNull(operation, "A operação transacional é obrigatória");
        return transactions.execute(status -> prepare(context, operation));
    }

    @Override
    public void execute(TenantContext context, TenantTransactionalAction action) {
        Objects.requireNonNull(action, "A ação transacional é obrigatória");
        execute(context, () -> {
            action.execute();
            return null;
        });
    }

    private <T> T prepare(TenantContext context, TenantTransactionalOperation<T> operation) {
        try {
            Settings existing = settings();
            if (existing.isConfigured()) {
                if (!"app_api".equals(existing.currentRole()) || !existing.matches(context)) {
                    throw new TenantTransactionContextConflictException();
                }
            } else if (existing.isPartiallyConfigured()) {
                throw new TenantTransactionInfrastructureException();
            } else {
                databaseRole.assumeApplicationRole();
                configureUser(context.userId());
                configureTenant(context.tenantId().value());

                Settings configured = settings();
                if (!configured.matches(context) || !"app_api".equals(configured.currentRole())) {
                    throw new TenantTransactionInfrastructureException();
                }
            }
        } catch (TenantTransactionContextConflictException | TenantTransactionInfrastructureException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new TenantTransactionInfrastructureException(exception);
        }
        return operation.execute();
    }

    private void configureUser(UUID userId) {
        jdbc.queryForObject(
                "select set_config('app.current_user_id', :userId, true)",
                new MapSqlParameterSource("userId", userId.toString()),
                String.class
        );
    }

    private void configureTenant(UUID tenantId) {
        jdbc.queryForObject(
                "select set_config('app.current_tenant_id', :tenantId, true)",
                new MapSqlParameterSource("tenantId", tenantId.toString()),
                String.class
        );
    }

    private Settings settings() {
        return jdbc.queryForObject(
                "select current_user as current_role, session_user as session_role, "
                        + "nullif(current_setting('app.current_user_id', true), '')::uuid as user_id, "
                        + "nullif(current_setting('app.current_tenant_id', true), '')::uuid as tenant_id",
                new MapSqlParameterSource(),
                (resultSet, rowNumber) -> new Settings(
                        resultSet.getString("current_role"),
                        resultSet.getString("session_role"),
                        resultSet.getObject("user_id", UUID.class),
                        resultSet.getObject("tenant_id", UUID.class)
                )
        );
    }

    private record Settings(String currentRole, String sessionRole, UUID userId, UUID tenantId) {
        boolean isConfigured() {
            return userId != null && tenantId != null;
        }

        boolean isPartiallyConfigured() {
            boolean onlyOneSettingIsConfigured = (userId == null) != (tenantId == null);
            boolean roleWasAlreadyChanged = !currentRole.equals(sessionRole);
            return onlyOneSettingIsConfigured || roleWasAlreadyChanged;
        }

        boolean matches(TenantContext context) {
            return context.userId().equals(userId) && context.tenantId().value().equals(tenantId);
        }
    }
}
