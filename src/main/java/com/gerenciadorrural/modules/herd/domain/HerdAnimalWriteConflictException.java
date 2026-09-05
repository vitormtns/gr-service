package com.gerenciadorrural.modules.herd.domain;

/** Conflito de invariantes de escrita que a aplicação pode classificar sem conhecer JDBC. */
public class HerdAnimalWriteConflictException extends RuntimeException {

    public enum Type {
        ID_CONFLICT,
        IDENTIFICATION_CONFLICT
    }

    private final Type type;

    public HerdAnimalWriteConflictException(Type type, Throwable cause) {
        super(cause);
        this.type = type;
    }

    public Type type() {
        return type;
    }
}
