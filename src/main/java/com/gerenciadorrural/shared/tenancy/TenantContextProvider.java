package com.gerenciadorrural.shared.tenancy;

import java.util.Optional;

/** Porta para obter o contexto validado no escopo corrente, quando houver autenticação. */
@FunctionalInterface
public interface TenantContextProvider {

    Optional<TenantContext> currentContext();
}
