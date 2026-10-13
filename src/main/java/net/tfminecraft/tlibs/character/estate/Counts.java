package net.tfminecraft.tlibs.character.estate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared checks for per-kind counts and short text lists. */
final class Counts {

    private Counts() {
    }

    static Map<String, Integer> copy(Map<String, Integer> counts) {
        Objects.requireNonNull(counts, "counts");
        counts.forEach((kind, count) -> {
            if (kind == null || kind.isBlank()) {
                throw new IllegalArgumentException("count kinds must not be blank");
            }
            if (count == null || count < 0) {
                throw new IllegalArgumentException("count for " + kind + " must not be negative");
            }
        });
        return Map.copyOf(counts);
    }

    static List<String> copy(List<String> values, String name) {
        return List.copyOf(Objects.requireNonNull(values, name));
    }

    static String text(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
