package org.bbnors.api.script;

import org.bbnors.core.config.Setting;

/** Script runtime settings (keys under {@code script.}). */
public final class ScriptSettings {

    private ScriptSettings() {
    }

    public static final Setting<Integer> MAX_CONSECUTIVE_ERRORS = Setting.ofInt("script.max-consecutive-errors", 5, 1, 1000,
            "Stop the script (state FAILED) after this many loop() exceptions in a row.");
    public static final Setting<Integer> ERROR_BACKOFF_MS = Setting.ofInt("script.error-backoff-ms", 1000, 0, 60000,
            "Delay after a loop() exception before retrying.");
    public static final Setting<Integer> MIN_LOOP_DELAY_MS = Setting.ofInt("script.min-loop-delay-ms", 20, 0, 10000,
            "Lower bound applied to loop() return values; prevents accidental busy loops.");
}
