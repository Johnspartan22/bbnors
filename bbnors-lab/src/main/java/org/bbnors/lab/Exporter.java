package org.bbnors.lab;

import org.bbnors.motion.ProfileCodec;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes lab results: PNG chart, per-run sample CSV, metrics JSON, and the profiles used. */
public final class Exporter {

    private Exporter() {
    }

    public static BufferedImage image(ChartRenderer r, int w, int h, ChartRenderer.Overlay o) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            r.render(g, w, h, o);
        } finally {
            g.dispose();
        }
        return img;
    }

    public static void png(ChartRenderer r, int w, int h, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        ImageIO.write(image(r, w, h, ChartRenderer.Overlay.NONE), "png", file.toFile());
    }

    /**
     * Writes {@code <base>.png}, {@code <base>-<run>.csv} per run, {@code <base>.json} and
     * {@code <base>.profiles.properties}. Returns the files written.
     */
    public static List<Path> all(ChartRenderer r, Path dir, String base) throws IOException {
        Files.createDirectories(dir);
        List<Path> out = new ArrayList<>();
        Path png = dir.resolve(base + ".png");
        png(r, 1500, 900, png);
        out.add(png);
        List<Object> runs = new ArrayList<>();
        for (TrajectoryRun run : r.runs()) {
            Path csv = dir.resolve(base + "-" + run.label() + ".csv");
            Files.writeString(csv, run.samples().toCsv(), StandardCharsets.UTF_8);
            out.add(csv);
            runs.add(describe(run));
        }
        Path json = dir.resolve(base + ".json");
        Files.writeString(json, Json.write(Map.of("runs", runs)) + "\n", StandardCharsets.UTF_8);
        out.add(json);
        Path props = dir.resolve(base + ".profiles.properties");
        Files.writeString(props, ProfileCodec.format(r.runs().stream().map(TrajectoryRun::profile).distinct().toList()), StandardCharsets.UTF_8);
        out.add(props);
        return out;
    }

    static Map<String, Object> describe(TrajectoryRun run) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profile", run.profile().name());
        m.put("seed", run.seed());
        m.put("from", List.of(run.from().x(), run.from().y()));
        m.put("target", List.of(run.target().x(), run.target().y(), run.target().width(), run.target().height()));
        m.put("motion", run.motion().describe());
        m.put("plan_us", run.planNanos() / 1000.0);
        m.put("metrics", run.metrics().asMap());
        return m;
    }
}
