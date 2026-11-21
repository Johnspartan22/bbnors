package org.bbnors.api.script;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Catalogue of available scripts: register explicitly or discover them on the classpath.
 *
 * <p><b>Discovery</b> uses the standard {@link ServiceLoader} mechanism. A script jar lists its
 * script classes (each with a public no-argument constructor) in
 * {@code META-INF/services/org.bbnors.api.script.Script}. Discovery only reads class metadata;
 * instances are created by {@link #create(String)}, so every run gets a fresh script with no
 * shared state.
 *
 * <p><b>Explicit registration</b> takes a factory, for scripts that need constructor arguments
 * or are defined in tests.
 *
 * <p>Thread-safe.
 */
public final class ScriptRegistry {

    /** Catalogue entry. */
    public record Entry(String id, String name, String description, String version, String author,
                        String category, Class<? extends Script> type, Supplier<? extends Script> factory) {
        @Override
        public String toString() {
            return id + " (" + name + " " + version + ")" + (description.isEmpty() ? "" : ": " + description);
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** Registers every script listed via {@link ServiceLoader} in {@code loader}; returns how many were added. */
    public synchronized int discover(ClassLoader loader) {
        int n = 0;
        for (ServiceLoader.Provider<Script> p : ServiceLoader.load(Script.class, loader).stream().toList()) {
            add(entryFor(p.type(), p::get));
            n++;
        }
        return n;
    }

    public int discover() {
        return discover(Thread.currentThread().getContextClassLoader());
    }

    /** Registers a script class with a factory; metadata comes from its {@link ScriptManifest}. */
    public synchronized <S extends Script> Entry register(Class<S> type, Supplier<? extends S> factory) {
        Entry e = entryFor(type, factory);
        add(e);
        return e;
    }

    public Optional<Entry> find(String id) {
        synchronized (this) {
            return Optional.ofNullable(entries.get(id.toLowerCase(Locale.ROOT)));
        }
    }

    /** Creates a new instance of the script with this id. */
    public Script create(String id) {
        Entry e = find(id).orElseThrow(() -> new IllegalArgumentException("unknown script '" + id + "'; available: " + ids()));
        return Objects.requireNonNull(e.factory().get(), "factory for " + id + " returned null");
    }

    public synchronized List<Entry> list() {
        List<Entry> out = new ArrayList<>(entries.values());
        out.sort(Comparator.comparing(Entry::id));
        return out;
    }

    public synchronized List<String> ids() {
        return list().stream().map(Entry::id).toList();
    }

    private void add(Entry e) {
        Entry prev = entries.putIfAbsent(e.id(), e);
        if (prev != null && prev.type() != e.type()) {
            throw new IllegalStateException("duplicate script id '" + e.id() + "': " + prev.type().getName() + " and " + e.type().getName());
        }
    }

    private static Entry entryFor(Class<? extends Script> type, Supplier<? extends Script> factory) {
        ScriptManifest m = type.getAnnotation(ScriptManifest.class);
        String id = m != null ? m.id() : type.getSimpleName().toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException("script id must match [a-z0-9][a-z0-9-]*: '" + id + "' (" + type.getName() + ")");
        }
        String name = m != null && !m.name().isEmpty() ? m.name() : type.getSimpleName();
        return new Entry(id, name, m != null ? m.description() : "", m != null ? m.version() : "",
                m != null ? m.author() : "", m != null ? m.category() : "", type, factory);
    }
}
