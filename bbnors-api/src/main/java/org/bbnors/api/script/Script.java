package org.bbnors.api.script;

/**
 * A script. The runner calls {@link #onStart} once, then {@link #loop} repeatedly, sleeping for
 * the returned number of milliseconds between calls, until {@code loop} returns a negative value
 * or the script is stopped; {@link #onStop} always runs last (also after errors).
 *
 * <p>Scripts never sleep with {@code Thread.sleep}; they use {@link ScriptContext#sleep} and
 * {@link ScriptContext#waitUntil}, which respect pause/stop and the injected clock.
 */
public interface Script {

    default void onStart(ScriptContext ctx) {
    }

    /** @return delay before the next loop in ms; negative to stop */
    int loop(ScriptContext ctx);

    default void onStop(ScriptContext ctx) {
    }

    /** Short human-readable status for overlays/logs. */
    default String status() {
        return "";
    }

    default String name() {
        return getClass().getSimpleName();
    }
}
