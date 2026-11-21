package org.bbnors.api.script;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Script composed of prioritised {@link Task}s: each loop runs the highest-priority task whose
 * {@link Task#accept} returns true (ties: registration order). This keeps each behaviour small,
 * independently testable and reusable across scripts.
 */
public abstract class TaskScript implements Script {

    /** One behaviour. */
    public interface Task {
        String name();

        boolean accept(ScriptContext ctx);

        /** @return delay in ms before the next loop (negative stops the script) */
        int execute(ScriptContext ctx);

        default int priority() {
            return 0;
        }
    }

    private final List<Task> tasks = new ArrayList<>();
    private volatile String current = "idle";

    protected final void add(Task... ts) {
        for (Task t : ts) {
            tasks.add(t);
        }
        tasks.sort(Comparator.comparingInt(Task::priority).reversed()); // stable: keeps registration order
    }

    /** Delay used when no task accepts. */
    protected int idleDelay() {
        return 300;
    }

    @Override
    public final int loop(ScriptContext ctx) {
        for (Task t : tasks) {
            if (t.accept(ctx)) {
                current = t.name();
                return t.execute(ctx);
            }
        }
        current = "idle";
        return idleDelay();
    }

    @Override
    public String status() {
        return current;
    }

    public List<Task> tasks() {
        return List.copyOf(tasks);
    }
}
