package org.bbnors.api.query;

import org.bbnors.api.model.Entity;
import org.bbnors.api.model.Tile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Fluent, lazy entity query: filters are combined into one predicate and evaluated in a single
 * pass when a terminal method ({@link #nearest()}, {@link #first()}, {@link #list()},
 * {@link #count()}, {@link #exists()}) runs. No intermediate lists are built, and
 * {@link #nearest()} is a linear scan, not a sort.
 *
 * <pre>{@code
 *   ctx.npcs().name("Goblin").action("Attack").within(10).where(n -> n.health() > 0).nearest()
 * }</pre>
 */
public final class EntityQuery<T extends Entity> {

    private final Supplier<List<T>> source;
    private final Supplier<Tile> origin;
    private Predicate<T> filter = t -> true;

    public EntityQuery(Supplier<List<T>> source, Supplier<Tile> origin) {
        this.source = source;
        this.origin = origin;
    }

    public EntityQuery<T> where(Predicate<? super T> p) {
        Predicate<T> prev = filter;
        filter = t -> prev.test(t) && p.test(t);
        return this;
    }

    public EntityQuery<T> name(String... names) {
        String[] n = names.clone();
        return where(e -> {
            String en = e.name();
            for (String x : n) {
                if (x.equalsIgnoreCase(en)) return true;
            }
            return false;
        });
    }

    public EntityQuery<T> nameContains(String part) {
        String p = part.toLowerCase(java.util.Locale.ROOT);
        return where(e -> e.name().toLowerCase(java.util.Locale.ROOT).contains(p));
    }

    public EntityQuery<T> id(int... ids) {
        return where(e -> {
            for (int i : ids) {
                if (e.id() == i) return true;
            }
            return false;
        });
    }

    public EntityQuery<T> action(String action) {
        return where(e -> e.hasAction(action));
    }

    /** Within {@code steps} tiles (Chebyshev) of the local player. */
    public EntityQuery<T> within(int steps) {
        Tile o = origin.get();
        return where(e -> o != null && e.tile().plane() == o.plane() && e.tile().steps(o) <= steps);
    }

    public EntityQuery<T> onScreen() {
        return where(Entity::onScreen);
    }

    public Optional<T> nearest() {
        Tile o = origin.get();
        T best = null;
        double bestD = Double.MAX_VALUE;
        for (T t : source.get()) {
            if (t.valid() && filter.test(t)) {
                double d = o == null ? 0 : t.tile().distance(o);
                if (d < bestD) {
                    bestD = d;
                    best = t;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public Optional<T> first() {
        for (T t : source.get()) {
            if (t.valid() && filter.test(t)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    public List<T> list() {
        List<T> out = new ArrayList<>();
        for (T t : source.get()) {
            if (t.valid() && filter.test(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Matches sorted by distance from the local player. */
    public List<T> sorted() {
        Tile o = origin.get();
        List<T> out = list();
        if (o != null) {
            out.sort(Comparator.comparingDouble(t -> t.tile().distance(o)));
        }
        return out;
    }

    public int count() {
        int n = 0;
        for (T t : source.get()) {
            if (t.valid() && filter.test(t)) n++;
        }
        return n;
    }

    public boolean exists() {
        return first().isPresent();
    }
}
