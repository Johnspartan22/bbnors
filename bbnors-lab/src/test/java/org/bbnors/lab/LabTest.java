package org.bbnors.lab;

import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.lab.bench.BenchmarkSuite;
import org.bbnors.lab.bench.Harness;
import org.bbnors.motion.MovementProfiles;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static ChartRenderer renderer() {
        MovementProfiles p = new MovementProfiles();
        Vec2 from = new Vec2(50, 400);
        Rect target = Rect.centered(new Vec2(600, 120), 30, 30);
        return new ChartRenderer(List.of(
                TrajectoryRun.of(p.get("standard"), from, target, 1),
                TrajectoryRun.of(p.get("fast"), from, target, 1)), "test");
    }

    @Test
    public void rendersHeadlessWithSeriesColoursPresent() {
        BufferedImage img = Exporter.image(renderer(), 1200, 800, new ChartRenderer.Overlay(0.1, 0.2));
        boolean blue = false;
        boolean orange = false;
        for (int x = 0; x < img.getWidth(); x += 2) {
            for (int y = 0; y < img.getHeight(); y += 2) {
                int rgb = img.getRGB(x, y) & 0xffffff;
                blue |= rgb == 0x2a78d6;
                orange |= rgb == 0xeb6834;
            }
        }
        assertTrue("first series drawn", blue);
        assertTrue("second series drawn", orange);
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesMoreRunsThanTheValidatedPalette() {
        ChartRenderer r = renderer();
        new ChartRenderer(List.of(r.runs().get(0), r.runs().get(0), r.runs().get(0), r.runs().get(0)), "x");
    }

    @Test
    public void exportWritesAllArtifacts() throws Exception {
        Path dir = tmp.newFolder().toPath();
        List<Path> files = Exporter.all(renderer(), dir, "t");
        assertEquals("png + 2 csv + json + profiles", 5, files.size());
        for (Path f : files) assertTrue(Files.size(f) > 0);
        String csv = Files.readString(dir.resolve("t-standard.csv"));
        assertTrue(csv.startsWith("t_s,x_px,y_px"));
        assertTrue(Files.readString(dir.resolve("t.json")).contains("\"endpoint_error_px\""));
    }

    @Test
    public void jsonEscapesAndNests() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("a", "q\"\n");
        m.put("b", List.of(1, 2.5));
        assertEquals("{\n  \"a\": \"q\\\"\\n\",\n  \"b\": [1, 2.50000]\n}", Json.write(m));
    }

    @Test
    public void harnessMeasuresAndSuiteRunsQuickly() throws Exception {
        Harness h = new Harness(1, 3, 2);
        Harness.Result r = h.run("t", "noop", "", i -> null);
        assertTrue(r.nsMedian() >= 0 && r.opsPerBatch() > 0);
        BenchmarkSuite s = new BenchmarkSuite(h);
        s.runAll();
        assertTrue(s.results().size() >= 20);
        Path dir = tmp.newFolder().toPath();
        s.write(dir);
        assertTrue(Files.readString(dir.resolve("results.md")).contains("astar/optimized-grid"));
        assertTrue(s.macro().containsKey("clock_spin_study_200hz"));
    }
}
