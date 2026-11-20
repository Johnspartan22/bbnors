package org.bbnors.core.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * A typed, documented, validated configuration key.
 *
 * <p>Settings are declared as constants next to the subsystem that consumes them (see
 * {@code MotionSettings}, {@code TimingSettings}, ...) and collected into a {@link ConfigSchema}.
 * Because each setting carries its own parser, validator, default and description, the schema can
 * validate any properties file and generate the configuration reference automatically.
 *
 * @param <T> value type
 */
public final class Setting<T> {

    /** Validates a parsed value; returns an error message or {@code null} if valid. */
    @FunctionalInterface
    public interface Validator<T> {
        String validate(T value);
    }

    private final String key;
    private final Class<T> type;
    private final T defaultValue;
    private final Function<String, T> parser;
    private final Validator<T> validator;
    private final String description;
    private final String constraint;

    private Setting(String key, Class<T> type, T defaultValue, Function<String, T> parser,
                    Validator<T> validator, String description, String constraint) {
        this.key = Objects.requireNonNull(key);
        this.type = Objects.requireNonNull(type);
        this.defaultValue = Objects.requireNonNull(defaultValue);
        this.parser = parser;
        this.validator = validator;
        this.description = description;
        this.constraint = constraint;
        String err = validator.validate(defaultValue);
        if (err != null) {
            throw new IllegalArgumentException("default for " + key + " is invalid: " + err);
        }
    }

    public static Setting<Double> ofDouble(String key, double def, double min, double max, String description) {
        return new Setting<>(key, Double.class, def, s -> Double.parseDouble(s.trim()),
                v -> (v.isNaN() || v < min || v > max) ? "must be in [" + min + ", " + max + "]" : null,
                description, "[" + fmt(min) + ", " + fmt(max) + "]");
    }

    public static Setting<Integer> ofInt(String key, int def, int min, int max, String description) {
        return new Setting<>(key, Integer.class, def, s -> Integer.parseInt(s.trim()),
                v -> (v < min || v > max) ? "must be in [" + min + ", " + max + "]" : null,
                description, "[" + min + ", " + max + "]");
    }

    public static Setting<Long> ofLong(String key, long def, long min, long max, String description) {
        return new Setting<>(key, Long.class, def, s -> Long.parseLong(s.trim()),
                v -> (v < min || v > max) ? "must be in [" + min + ", " + max + "]" : null,
                description, "[" + min + ", " + max + "]");
    }

    public static Setting<Boolean> ofBoolean(String key, boolean def, String description) {
        return new Setting<>(key, Boolean.class, def, Setting::parseBool, v -> null, description, "true | false");
    }

    public static Setting<String> ofString(String key, String def, String description) {
        return new Setting<>(key, String.class, def, String::trim, v -> null, description, "text");
    }

    public static <E extends Enum<E>> Setting<E> ofEnum(String key, Class<E> type, E def, String description) {
        String allowed = String.join(" | ", Arrays.stream(type.getEnumConstants()).map(e -> e.name().toLowerCase(Locale.ROOT)).toList());
        return new Setting<>(key, type, def, s -> Enum.valueOf(type, s.trim().toUpperCase(Locale.ROOT).replace('-', '_')),
                v -> null, description, allowed);
    }

    private static Boolean parseBool(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "true", "yes", "on", "1" -> Boolean.TRUE;
            case "false", "no", "off", "0" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException("not a boolean: " + s);
        };
    }

    private static String fmt(double d) {
        return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
    }

    /** Parses and validates; throws {@link IllegalArgumentException} with a readable message on failure. */
    public T parse(String raw) {
        T v;
        try {
            v = parser.apply(raw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(key + ": cannot parse '" + raw + "' (expected " + constraint + ")", e);
        }
        String err = validator.validate(v);
        if (err != null) {
            throw new IllegalArgumentException(key + ": '" + raw + "' " + err);
        }
        return v;
    }

    /** Returns an error for {@code value}, or {@code null} if it is acceptable. */
    public String check(T value) {
        return validator.validate(value);
    }

    public String key() {
        return key;
    }

    public Class<T> type() {
        return type;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public String description() {
        return description;
    }

    public String constraint() {
        return constraint;
    }

    @Override
    public String toString() {
        return key;
    }
}
