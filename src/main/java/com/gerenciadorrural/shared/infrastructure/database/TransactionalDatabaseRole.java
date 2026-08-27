package com.gerenciadorrural.shared.infrastructure.database;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TransactionalDatabaseRole {

    private final JdbcTemplate jdbcTemplate;

    public TransactionalDatabaseRole(JdbcTemplate jdbcTemplate, DatabaseAccessProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        properties.runtimeRole();
    }

    public void assumeApplicationRole() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("O acesso ao banco exige uma transação ativa");
        }
        jdbcTemplate.execute("set local role app_api");
    }
}
