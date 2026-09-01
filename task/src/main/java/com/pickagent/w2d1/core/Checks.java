package com.pickagent.w2d1.core;

final class Checks {
    private Checks() {
    }

    static void nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }
}
