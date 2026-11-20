package org.bbnors.core.log;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Small dependency-free logging facade.
 *
 * <p>Loggers are cheap named handles; output goes to the registered {@link Sink}s (console by
 * default). {@link RingBufferSink} keeps the last N records in memory for on-screen diagnostics.
 * Use the {@link Supplier} overloads in hot paths so message formatting is skipped when the
 * level is disabled.
 */
public final class Log {

    public enum Level { TRACE, DEBUG, INFO, WARN, ERROR }

    /** One log record. */
    public record Record(Instant time, Level level, String logger, String message, Throwable error) {
    }

    /** Destination for log records. Implementations must be thread-safe. */
    @FunctionalInterface
    public interface Sink {
        void accept(Record record);
    }

    private static final List<Sink> SINKS = new CopyOnWriteArrayList<>(List.of(Log::console));
    private static volatile Level threshold = Level.INFO;

    private final String name;

    private Log(String name) {
        this.name = name;
    }

    public static Log get(Class<?> type) {
        return new Log(type.getSimpleName());
    }

    public static Log get(String name) {
        return new Log(Objects.requireNonNull(name));
    }

    public static void setLevel(Level level) {
        threshold = Objects.requireNonNull(level);
    }

    public static Level level() {
        return threshold;
    }

    public static void addSink(Sink sink) {
        SINKS.add(sink);
    }

    public static void removeSink(Sink sink) {
        SINKS.remove(sink);
    }

    /** Replaces all sinks (e.g. to silence console output in benchmarks). */
    public static void setSinks(List<Sink> sinks) {
        SINKS.clear();
        SINKS.addAll(sinks);
    }

    public boolean enabled(Level level) {
        return level.ordinal() >= threshold.ordinal();
    }

    public void trace(Supplier<String> msg) {
        if (enabled(Level.TRACE)) emit(Level.TRACE, msg.get(), null);
    }

    public void debug(Supplier<String> msg) {
        if (enabled(Level.DEBUG)) emit(Level.DEBUG, msg.get(), null);
    }

    public void debug(String msg) {
        if (enabled(Level.DEBUG)) emit(Level.DEBUG, msg, null);
    }

    public void info(String msg) {
        if (enabled(Level.INFO)) emit(Level.INFO, msg, null);
    }

    public void warn(String msg) {
        if (enabled(Level.WARN)) emit(Level.WARN, msg, null);
    }

    public void warn(String msg, Throwable t) {
        if (enabled(Level.WARN)) emit(Level.WARN, msg, t);
    }

    public void error(String msg, Throwable t) {
        if (enabled(Level.ERROR)) emit(Level.ERROR, msg, t);
    }

    private void emit(Level level, String msg, Throwable t) {
        Record r = new Record(Instant.now(), level, name, msg, t);
        for (Sink s : SINKS) {
            s.accept(r);
        }
    }

    private static void console(Record r) {
        StringBuilder sb = new StringBuilder(64)
                .append(r.time()).append(' ').append(r.level()).append(" [").append(r.logger()).append("] ")
                .append(r.message());
        (r.level().ordinal() >= Level.WARN.ordinal() ? System.err : System.out).println(sb);
        if (r.error() != null) {
            r.error().printStackTrace(System.err);
        }
    }

    /** Thread-safe fixed-capacity in-memory sink for diagnostics overlays and tests. */
    public static final class RingBufferSink implements Sink {
        private final Record[] buffer;
        private int next;
        private int size;

        public RingBufferSink(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
            buffer = new Record[capacity];
        }

        @Override
        public synchronized void accept(Record record) {
            buffer[next] = record;
            next = (next + 1) % buffer.length;
            size = Math.min(size + 1, buffer.length);
        }

        /** Oldest-first snapshot. */
        public synchronized List<Record> snapshot() {
            List<Record> out = new ArrayList<>(size);
            int start = (next - size + buffer.length) % buffer.length;
            for (int i = 0; i < size; i++) {
                out.add(buffer[(start + i) % buffer.length]);
            }
            return out;
        }
    }
}
