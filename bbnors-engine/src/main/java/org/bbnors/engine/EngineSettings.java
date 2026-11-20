package org.bbnors.engine;

import org.bbnors.core.config.Setting;
import org.bbnors.engine.camera.CameraController;

/** Global interaction-engine settings (keys under {@code engine.}). */
public final class EngineSettings {

    private EngineSettings() {
    }

    public static final Setting<String> DEFAULT_PROFILE = Setting.ofString("engine.mouse.default-profile", "standard",
            "Movement profile used when a script does not name one.");
    public static final Setting<Integer> MAX_CORRECTIONS = Setting.ofInt("engine.mouse.max-corrections", 2, 0, 10,
            "Corrective re-aims after a movement if the (moving) target no longer contains the cursor.");
    public static final Setting<Long> SEED = Setting.ofLong("engine.seed", 0, Long.MIN_VALUE, Long.MAX_VALUE,
            "Random seed; 0 = derive from time (non-reproducible).");
    public static final Setting<CameraController.Mode> CAMERA_MODE = Setting.ofEnum("engine.camera.mode", CameraController.Mode.class,
            CameraController.Mode.KEYS, "Default camera rotation mode.");
    public static final Setting<Integer> CAMERA_YAW_TOLERANCE = Setting.ofInt("engine.camera.yaw-tolerance", 24, 1, 512,
            "Acceptable final yaw error, client units.");
    public static final Setting<Integer> CAMERA_PITCH_TOLERANCE = Setting.ofInt("engine.camera.pitch-tolerance", 12, 1, 256,
            "Acceptable final pitch error, client units.");
    public static final Setting<Integer> CAMERA_MAX_PASSES = Setting.ofInt("engine.camera.max-passes", 4, 1, 20,
            "Maximum rotate/re-measure passes.");
    public static final Setting<Double> CAMERA_HOLD_FACTOR = Setting.ofDouble("engine.camera.hold-factor", 0.92, 0.31, 1.2,
            "Fraction of the computed key-hold / drag distance used per pass (<1 approaches from below).");
    public static final Setting<String> PROFILES_FILE = Setting.ofString("engine.profiles-file", "",
            "Optional properties file with profile.* and timing.* overrides.");
}
