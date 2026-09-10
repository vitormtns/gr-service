package com.gerenciadorrural.modules.herd.domain;

/** Campo de PATCH que conserva a diferença entre ausência e null explícito. */
public record HerdAnimalPatch<T>(boolean present, T value) {
    public static <T> HerdAnimalPatch<T> absent() { return new HerdAnimalPatch<>(false, null); }
    public static <T> HerdAnimalPatch<T> present(T value) { return new HerdAnimalPatch<>(true, value); }
}
