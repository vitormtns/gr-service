package com.gerenciadorrural.shared.application.query;

/** Executa diretamente uma consulta de um caso de uso. */
@FunctionalInterface
public interface QueryHandler<Q extends Query<R>, R> {

    R handle(Q query);
}
