package org.bbnors.core.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Registry of all known {@link Setting}s. Unknown keys in a properties file are reported as
 * problems (they are almost always typos), and the schema can render itself as Markdown.
 */
public final class ConfigSchema {

    private final Map<String, Setting<?>> settings = new LinkedHashMap<>();

    public ConfigSchema register(Setting<?> setting) {
        Setting<?> prev = settings.putIfAbsent(setting.key(), setting);
        if (prev != null && prev != setting) {
            throw new IllegalArgumentException("duplicate setting key: " + setting.key());
        }
        return this;
    }

    /** Registers every {@code public static final Setting} field declared on {@code holder}. */
    public ConfigSchema registerAll(Class<?> holder) {
        for (Field f : holder.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) && Modifier.isPublic(m) && Setting.class.isAssignableFrom(f.getType())) {
                try {
                    register((Setting<?>) f.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return this;
    }

    public Setting<?> find(String key) {
        return settings.get(key);
    }

    public Collection<Setting<?>> all() {
        return Collections.unmodifiableCollection(settings.values());
    }

    /** Markdown table of every setting: key, type, default, allowed values, description. */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("| Key | Type | Default | Allowed | Description |\n");
        sb.append("|---|---|---|---|---|\n");
        for (Setting<?> s : settings.values()) {
            Object def = s.defaultValue();
            String defStr = def instanceof Enum<?> e ? e.name().toLowerCase(Locale.ROOT) : String.valueOf(def);
            sb.append("| `").append(s.key()).append("` | ").append(s.type().getSimpleName())
                    .append(" | `").append(defStr).append("` | ").append(s.constraint())
                    .append(" | ").append(s.description()).append(" |\n");
        }
        return sb.toString();
    }
}
