package org.bbnors.core.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Immutable set of configuration values validated against a {@link ConfigSchema}.
 *
 * <p>Loading never throws for bad <em>values</em>: an invalid or unparseable entry falls back to
 * the setting's default and is recorded in {@link #problems()}, so a typo in a properties file
 * degrades gracefully instead of stopping a script. Callers that want strictness can check
 * {@code problems().isEmpty()}.
 */
public final class Config {

    private final ConfigSchema schema;
    private final Map<String, Object> values;
    private final List<String> problems;

    private Config(ConfigSchema schema, Map<String, Object> values, List<String> problems) {
        this.schema = schema;
        this.values = values;
        this.problems = problems;
    }

    /** All defaults. */
    public static Config defaults(ConfigSchema schema) {
        return new Config(schema, Map.of(), List.of());
    }

    public static Config load(ConfigSchema schema, Properties props) {
        Map<String, Object> values = new HashMap<>();
        List<String> problems = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            Setting<?> s = schema.find(key);
            if (s == null) {
                problems.add("unknown key '" + key + "' (ignored)");
                continue;
            }
            try {
                values.put(key, s.parse(props.getProperty(key)));
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage() + " -> using default " + s.defaultValue());
            }
        }
        return new Config(schema, Map.copyOf(values), List.copyOf(problems));
    }

    public static Config load(ConfigSchema schema, Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return load(schema, p);
    }

    public <T> T get(Setting<T> setting) {
        Object v = values.get(setting.key());
        return v == null ? setting.defaultValue() : setting.type().cast(v);
    }

    /** Returns a copy with one value replaced (validated; throws on invalid input). */
    public <T> Config with(Setting<T> setting, T value) {
        String err = setting.check(value);
        if (err != null) {
            throw new IllegalArgumentException(setting.key() + ": " + err);
        }
        Map<String, Object> copy = new HashMap<>(values);
        copy.put(setting.key(), value);
        return new Config(schema, Map.copyOf(copy), problems);
    }

    public List<String> problems() {
        return Collections.unmodifiableList(problems);
    }

    public ConfigSchema schema() {
        return schema;
    }
}
