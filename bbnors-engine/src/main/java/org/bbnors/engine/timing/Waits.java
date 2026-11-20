package org.bbnors.engine.timing;

import org.bbnors.core.event.EventBus;
import org.bbnors.core.time.Clock;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Conditional waits, built on an injected {@link Clock} so they are deterministic under a
 * {@link org.bbnors.core.time.ManualClock}.
 *
 * <p>Compared with the common {@code sleepUntil(condition, timeout)} idiom this adds:
 * <ul>
 *   <li>{@link Builder#stableFor(int)}: the condition must hold on N consecutive polls, which
 *       filters one-frame flickers in game state (DreamBot exposes the same idea as
 *       "successfulPolls").</li>
 *   <li>{@link Builder#resetWhile(BooleanSupplier)}: the timeout restarts while progress is being
 *       made (e.g. "still walking"), so long but healthy actions do not time out.</li>
 *   <li>{@link #forEvent}: event-driven wait with no polling at all.</li>
 * </ul>
 * All waits return a {@link Result} instead of a bare boolean so callers can tell a timeout from
 * an interruption and see how long the wait took.
 */
public final class Waits {

    private Waits() {
    }

    /** Outcome of a wait. */
    public record Result(boolean satisfied, boolean interrupted, long elapsedNanos, int polls) {
        public boolean timedOut() {
            return !satisfied && !interrupted;
        }
    }

    public static Builder until(Clock clock, BooleanSupplier condition) {
        return new Builder(clock, condition);
    }

    public static Builder whileTrue(Clock clock, BooleanSupplier condition) {
        return new Builder(clock, () -> !condition.getAsBoolean());
    }

    /** Fluent wait configuration. Defaults: 5 s timeout, 50 ms poll, stable for 1 poll. */
    public static final class Builder {
        private final Clock clock;
        private final BooleanSupplier condition;
        private long timeoutNanos = Duration.ofSeconds(5).toNanos();
        private long pollNanos = Duration.ofMillis(50).toNanos();
        private int stablePolls = 1;
        private BooleanSupplier reset;

        private Builder(Clock clock, BooleanSupplier condition) {
            this.clock = clock;
            this.condition = condition;
        }

        public Builder timeout(Duration d) {
            timeoutNanos = d.toNanos();
            return this;
        }

        public Builder poll(Duration d) {
            if (d.isNegative() || d.isZero()) throw new IllegalArgumentException("poll must be > 0");
            pollNanos = d.toNanos();
            return this;
        }

        public Builder stableFor(int polls) {
            if (polls < 1) throw new IllegalArgumentException("polls must be >= 1");
            stablePolls = polls;
            return this;
        }

        /** While {@code progress} is true the timeout window restarts. */
        public Builder resetWhile(BooleanSupplier progress) {
            reset = progress;
            return this;
        }

        public Result await() {
            long start = clock.nanos();
            long windowStart = start;
            int consecutive = 0;
            int polls = 0;
            while (true) {
                polls++;
                if (condition.getAsBoolean()) {
                    if (++consecutive >= stablePolls) {
                        return new Result(true, false, clock.nanos() - start, polls);
                    }
                } else {
                    consecutive = 0;
                }
                long now = clock.nanos();
                if (reset != null && reset.getAsBoolean()) {
                    windowStart = now;
                }
                if (now - windowStart >= timeoutNanos) {
                    return new Result(false, false, now - start, polls);
                }
                try {
                    clock.sleepNanos(Math.min(pollNanos, timeoutNanos - (now - windowStart)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new Result(false, true, clock.nanos() - start, polls);
                }
            }
        }

        public boolean test() {
            return await().satisfied();
        }
    }

    /**
     * Blocks until an event of {@code type} matching {@code filter} is published on {@code bus}
     * or the (real-time) timeout elapses. No polling; wake-up latency is the publisher's.
     *
     * @return the matching event, or {@code null} on timeout/interrupt
     */
    public static <E> E forEvent(EventBus bus, Class<E> type, Predicate<? super E> filter, Duration timeout) {
        CountDownLatch latch = new CountDownLatch(1);
        Object[] box = new Object[1];
        EventBus.Subscription sub = bus.subscribe(type, e -> {
            if (latch.getCount() > 0 && filter.test(e)) {
                box[0] = e;
                latch.countDown();
            }
        });
        try {
            if (latch.await(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                return type.cast(box[0]);
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            sub.cancel();
        }
    }
}
