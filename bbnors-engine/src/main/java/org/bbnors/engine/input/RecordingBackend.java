package org.bbnors.engine.input;

import org.bbnors.core.time.Clock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Records every input event with a timestamp from the engine clock, optionally forwarding to a
 * delegate (e.g. a simulated game). Thread-safe.
 */
public final class RecordingBackend implements InputBackend {

    public enum Type { MOVE, PRESS, RELEASE, WHEEL, KEY_PRESS, KEY_RELEASE }

    /** One recorded event. {@code a}/{@code b} are x/y for MOVE, button ordinal, notches or key code. */
    public record Event(long nanos, Type type, int a, int b, char ch) {
    }

    private final Clock clock;
    private final List<Event> events = new ArrayList<>();
    private final InputBackend delegate;
    private volatile Consumer<Event> listener;

    public RecordingBackend(Clock clock) {
        this(clock, null);
    }

    public RecordingBackend(Clock clock, InputBackend delegate) {
        this.clock = clock;
        this.delegate = delegate;
    }

    private void record(Type t, int a, int b, char ch) {
        Event e = new Event(clock.nanos(), t, a, b, ch);
        synchronized (events) {
            events.add(e);
        }
        Consumer<Event> l = listener;
        if (l != null) {
            l.accept(e);
        }
    }

    public void setListener(Consumer<Event> listener) {
        this.listener = listener;
    }

    @Override
    public void mouseMove(int x, int y) {
        record(Type.MOVE, x, y, '\0');
        if (delegate != null) delegate.mouseMove(x, y);
    }

    @Override
    public void mousePress(MouseButton button) {
        record(Type.PRESS, button.ordinal(), 0, '\0');
        if (delegate != null) delegate.mousePress(button);
    }

    @Override
    public void mouseRelease(MouseButton button) {
        record(Type.RELEASE, button.ordinal(), 0, '\0');
        if (delegate != null) delegate.mouseRelease(button);
    }

    @Override
    public void mouseWheel(int notches) {
        record(Type.WHEEL, notches, 0, '\0');
        if (delegate != null) delegate.mouseWheel(notches);
    }

    @Override
    public void keyPress(int keyCode, char keyChar) {
        record(Type.KEY_PRESS, keyCode, 0, keyChar);
        if (delegate != null) delegate.keyPress(keyCode, keyChar);
    }

    @Override
    public void keyRelease(int keyCode, char keyChar) {
        record(Type.KEY_RELEASE, keyCode, 0, keyChar);
        if (delegate != null) delegate.keyRelease(keyCode, keyChar);
    }

    public List<Event> events() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    public List<Event> events(Type type) {
        synchronized (events) {
            return events.stream().filter(e -> e.type() == type).toList();
        }
    }

    public void clear() {
        synchronized (events) {
            events.clear();
        }
    }
}
