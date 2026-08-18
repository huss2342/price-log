package app.pricelog.api.extract;

import java.util.Locale;
import java.util.Optional;

/** Lenient enum parsing. Model output is constrained by schema, user input is not. */
public final class Enums {

    private Enums() {
    }

    public static <E extends Enum<E>> Optional<E> parse(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(key)) {
                return Optional.of(constant);
            }
        }
        return Optional.empty();
    }

    public static <E extends Enum<E>> E parseOr(Class<E> type, String raw, E fallback) {
        return parse(type, raw).orElse(fallback);
    }
}
