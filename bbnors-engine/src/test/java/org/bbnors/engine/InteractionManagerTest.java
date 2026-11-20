package org.bbnors.engine;

import org.bbnors.core.event.EventBus;
import org.bbnors.core.random.Rng;
import org.bbnors.core.time.Clock;
import org.bbnors.engine.input.RecordingBackend;
import org.bbnors.engine.interact.Channel;
import org.bbnors.engine.interact.ChannelBusyException;
import org.bbnors.engine.interact.ConflictPolicy;
import org.bbnors.engine.interact.InteractionEvent;
import org.bbnors.engine.interact.InteractionManager;
import org.bbnors.engine.interact.OpHandle;
import org.bbnors.engine.interact.Operation;
import org.bbnors.engine.keyboard.KeyboardController;
import org.bbnors.engine.timing.TimingProfiles;
import org.junit.After;
import org.junit.Test;

import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Channel arbitration, preemption, cancellation and concurrency on real (virtual) threads. */
public class InteractionManagerTest {

    private final Clock clock = Clock.system();
    private final EventBus bus = new EventBus();
    private final InteractionManager m = new InteractionManager(clock, new Rng(1), bus);
    private final List<String> started = new CopyOnWriteArrayList<>();

    {
        bus.subscribe(InteractionEvent.Started.class, e -> started.add(e.handle().operation().name()));
    }

    @After
    public void close() {
        m.close();
    }

    private static Operation<String> sleeper(String name, EnumSet<Channel> ch, long ms, int prio, ConflictPolicy pol) {
        return Operation.of(name, ch, prio, pol, ctx -> {
            ctx.sleepNanos(TimeUnit.MILLISECONDS.toNanos(ms));
            return name;
        });
    }

    @Test
    public void sameChannelOperationsAreSerialisedInOrder() throws Exception {
        OpHandle<String> a = m.submit(sleeper("a", EnumSet.of(Channel.MOUSE), 60, 0, ConflictPolicy.QUEUE));
        OpHandle<String> b = m.submit(sleeper("b", EnumSet.of(Channel.MOUSE), 10, 0, ConflictPolicy.QUEUE));
        assertEquals(OpHandle.Status.QUEUED, b.status());
        assertEquals("b", b.await());
        assertEquals("a", a.await());
        assertEquals(List.of("a", "b"), started);
        assertTrue(m.awaitIdle(Duration.ofSeconds(1)));
    }

    @Test
    public void disjointChannelsRunConcurrently() throws Exception {
        CountDownLatch both = new CountDownLatch(2);
        Operation<Void> mouse = Operation.of("mouse", EnumSet.of(Channel.MOUSE), ctx -> {
            both.countDown();
            if (!both.await(2, TimeUnit.SECONDS)) throw new AssertionError("keyboard op never ran concurrently");
            return null;
        });
        Operation<Void> keys = Operation.of("keys", EnumSet.of(Channel.KEYBOARD), ctx -> {
            both.countDown();
            if (!both.await(2, TimeUnit.SECONDS)) throw new AssertionError("mouse op never ran concurrently");
            return null;
        });
        OpHandle<Void> a = m.submit(mouse);
        OpHandle<Void> b = m.submit(keys);
        a.await();
        b.await();
    }

    @Test
    public void preemptCancelsLowerPriorityHolder() {
        OpHandle<String> slow = m.submit(sleeper("slow", EnumSet.of(Channel.MOUSE), 5_000, 0, ConflictPolicy.QUEUE));
        waitFor(() -> slow.status() == OpHandle.Status.RUNNING);
        long t0 = System.nanoTime();
        OpHandle<String> urgent = m.submit(sleeper("urgent", EnumSet.of(Channel.MOUSE), 5, 10, ConflictPolicy.PREEMPT));
        assertEquals("urgent", urgent.await());
        assertTrue("preemption was prompt", System.nanoTime() - t0 < TimeUnit.SECONDS.toNanos(1));
        try {
            slow.await();
            fail("expected cancellation");
        } catch (CancellationException expected) {
            assertEquals(OpHandle.Status.CANCELLED, slow.status());
        }
    }

    @Test
    public void preemptDoesNotCancelHigherPriorityHolder() {
        OpHandle<String> important = m.submit(sleeper("important", EnumSet.of(Channel.MOUSE), 80, 10, ConflictPolicy.QUEUE));
        waitFor(() -> important.status() == OpHandle.Status.RUNNING);
        OpHandle<String> other = m.submit(sleeper("other", EnumSet.of(Channel.MOUSE), 5, 1, ConflictPolicy.PREEMPT));
        assertEquals("important", important.await());
        assertEquals("other", other.await());
    }

    @Test
    public void rejectFailsFastWhenBusy() {
        OpHandle<String> a = m.submit(sleeper("a", EnumSet.of(Channel.CAMERA), 100, 0, ConflictPolicy.QUEUE));
        OpHandle<String> r = m.submit(sleeper("r", EnumSet.of(Channel.CAMERA, Channel.KEYBOARD), 1, 0, ConflictPolicy.REJECT));
        assertEquals(OpHandle.Status.FAILED, r.status());
        try {
            r.await();
            fail();
        } catch (OpHandle.OperationFailedException e) {
            assertTrue(e.getCause() instanceof ChannelBusyException);
        }
        a.await();
    }

    @Test
    public void queuedMultiChannelOperationIsNotOvertaken() {
        OpHandle<String> a = m.submit(sleeper("a", EnumSet.of(Channel.MOUSE), 80, 0, ConflictPolicy.QUEUE));
        waitFor(() -> a.status() == OpHandle.Status.RUNNING);
        OpHandle<String> b = m.submit(sleeper("b", EnumSet.of(Channel.MOUSE, Channel.KEYBOARD), 10, 0, ConflictPolicy.QUEUE));
        OpHandle<String> c = m.submit(sleeper("c", EnumSet.of(Channel.KEYBOARD), 10, 0, ConflictPolicy.QUEUE));
        c.await();
        b.await();
        assertEquals("b reserved KEYBOARD, so c waits for it", List.of("a", "b", "c"), started);
    }

    @Test
    public void cancellingQueuedOperationRemovesIt() {
        OpHandle<String> a = m.submit(sleeper("a", EnumSet.of(Channel.MOUSE), 50, 0, ConflictPolicy.QUEUE));
        OpHandle<String> b = m.submit(sleeper("b", EnumSet.of(Channel.MOUSE), 50, 0, ConflictPolicy.QUEUE));
        b.cancel();
        assertEquals(OpHandle.Status.CANCELLED, b.status());
        a.await();
        assertEquals(List.of("a"), started);
    }

    @Test
    public void cancelledKeyHoldReleasesItsKeys() {
        RecordingBackend rec = new RecordingBackend(clock);
        KeyboardController kb = new KeyboardController(m, rec, new TimingProfiles());
        OpHandle<Void> h = kb.hold(List.of(KeyEvent.VK_SHIFT, KeyEvent.VK_LEFT), 10);
        waitFor(() -> kb.pressedKeys().size() == 2);
        h.cancel();
        try {
            h.await();
            fail();
        } catch (CancellationException expected) {
            // ok
        }
        assertTrue(kb.pressedKeys().isEmpty());
        assertEquals(2, rec.events(RecordingBackend.Type.KEY_RELEASE).size());
    }

    @Test
    public void failingOperationReportsCause() {
        OpHandle<Object> h = m.submit(Operation.of("boom", EnumSet.of(Channel.MOUSE), ctx -> {
            throw new IllegalStateException("kaboom");
        }));
        try {
            h.await();
            fail();
        } catch (OpHandle.OperationFailedException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
        // channel was released
        assertEquals("ok", m.submit(sleeper("ok", EnumSet.of(Channel.MOUSE), 1, 0, ConflictPolicy.QUEUE)).await());
    }

    private static void waitFor(java.util.function.BooleanSupplier c) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!c.getAsBoolean()) {
            if (System.nanoTime() > end) fail("condition not reached");
            Thread.onSpinWait();
        }
    }
}
