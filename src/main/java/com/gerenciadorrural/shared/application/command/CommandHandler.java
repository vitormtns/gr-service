package com.gerenciadorrural.shared.application.command;

/** Executa diretamente um comando de um caso de uso. */
@FunctionalInterface
public interface CommandHandler<C extends Command<R>, R> {

    R handle(C command);
}
