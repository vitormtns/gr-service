package com.gerenciadorrural.modules.herd.application;

final class PosixEdgeWhitespace {

    private static final String EDGE = "[ \\t\\n\\x0B\\f\\r]";

    private PosixEdgeWhitespace() {
    }

    static String trim(String value) {
        return value.replaceAll("^" + EDGE + "+|" + EDGE + "+$", "");
    }
}
