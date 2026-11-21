package org.bbnors.lab;

import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.lab.bench.BenchmarkSuite;
import org.bbnors.lab.bench.Harness;
import org.bbnors.motion.MovementProfile;
import org.bbnors.motion.MovementProfiles;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lab entry point.
 * <pre>
 *   lab                         interactive Swing lab (needs a display)
 *   lab render  [options]       headless comparison: PNG + CSV + JSON + profiles
 *   lab compare [options]       print the metrics table for the chosen profiles
 *   lab bench   [--out DIR] [--quick]
 *
 * options: --profiles a,b,c  --seed N  --from X,Y  --target X,Y,W,H  --out DIR  --name BASE
 * </pre>
 */
public final class LabMain {

    private LabMain() {
    }

    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 && !args[0].startsWith("--") ? args[0] : "gui";
        Map<String, String> opt = options(args);
        switch (cmd) {
            case "gui" -> {
                if (GraphicsEnvironment.isHeadless()) {
                    System.err.println("No display available; use 'render' or 'bench'.");
                    System.exit(2);
                }
                LabWindow.open();
            }
            case "render" -> {
                ChartRenderer r = renderer(opt);
                Path out = Path.of(opt.getOrDefault("out", "lab-output"));
                for (Path p : Exporter.all(r, out, opt.getOrDefault("name", "comparison"))) {
                    System.out.println("wrote " + p);
                }
            }
            case "compare" -> {
                ChartRenderer r = renderer(opt);
                for (TrajectoryRun run : r.runs()) {
                    System.out.println(run.label() + ": " + run.metrics());
                }
            }
            case "bench" -> {
                boolean quick = opt.containsKey("quick");
                BenchmarkSuite s = new BenchmarkSuite(quick ? new Harness(2, 5, 20) : new Harness(5, 15, 60));
                s.runAll();
                Path out = Path.of(opt.getOrDefault("out", "bench-results"));
                s.write(out);
                System.out.print(Harness.markdown(s.results()));
                System.out.println("\nend-to-end: " + s.macro());
                System.out.println("wrote " + out.resolve("results.md") + " and results.json");
            }
            default -> {
                System.err.println("unknown command '" + cmd + "' (gui | render | compare | bench)");
                System.exit(2);
            }
        }
    }

    static ChartRenderer renderer(Map<String, String> opt) {
        MovementProfiles profiles = new MovementProfiles();
        String[] names = opt.getOrDefault("profiles", "standard,smooth-spline,linear-baseline").split(",");
        long seed = Long.parseLong(opt.getOrDefault("seed", "1"));
        double[] f = nums(opt.getOrDefault("from", "90,420"), 2);
        double[] t = nums(opt.getOrDefault("target", "593,126,34,28"), 4);
        Vec2 from = new Vec2(f[0], f[1]);
        Rect target = new Rect(t[0], t[1], t[2], t[3]);
        List<TrajectoryRun> runs = new ArrayList<>();
        for (String n : names) {
            MovementProfile p = profiles.get(n.trim());
            runs.add(TrajectoryRun.of(p, from, target, seed));
        }
        return new ChartRenderer(runs, String.format("Seed %d · %.0f px to a %.0f×%.0f target", seed, from.distance(target.center()), target.width(), target.height()));
    }

    private static double[] nums(String s, int n) {
        String[] p = s.split(",");
        if (p.length != n) throw new IllegalArgumentException("expected " + n + " comma-separated numbers: " + s);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = Double.parseDouble(p[i].trim());
        return out;
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String k = args[i].substring(2);
                String v = i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "true";
                m.put(k, v);
            }
        }
        return m;
    }
}
