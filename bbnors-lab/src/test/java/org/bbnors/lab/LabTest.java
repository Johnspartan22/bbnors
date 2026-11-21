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

    @Test
    public void evaluatorRecoversExactFittsConstantsWhenRandomnessIsOff() {
        MovementProfiles p = new MovementProfiles();
        org.bbnors.motion.MovementProfile det = p.get("standard").toBuilder().name("det")
                .durationJitter(0).overshootChance(0).endpointSpread(0).build();
        ProfileEvaluator.ProfileReport r = ProfileEvaluator.evaluate(det, ProfileEvaluator.scenarios(200, 3));
        assertEquals(det.fittsA(), r.fittedA(), 1e-9);
        assertEquals(det.fittsB(), r.fittedB(), 1e-9);
        assertEquals(1.0, r.fitR2(), 1e-9);
        assertEquals(1.0, r.inTargetRate(), 0);
        assertEquals(0.0, r.overshootRate(), 0);
        assertEquals(0.0, r.stats().get("aim_offset").p95(), 1e-9);
    }

    @Test
    public void evaluationIsPairedAndComparesPresets() {
        MovementProfiles p = new MovementProfiles();
        List<ProfileEvaluator.ProfileReport> rs = ProfileEvaluator.evaluateAll(
                List.of(p.get("precise"), p.get("fast")), 300, 9);
        for (int i = 0; i < 300; i++) {
            assertEquals(rs.get(0).trials().get(i).scenario(), rs.get(1).trials().get(i).scenario());
        }
        for (ProfileEvaluator.ProfileReport r : rs) {
            assertEquals(r.profile().name(), 1.0, r.inTargetRate(), 0);
            assertEquals(r.profile().name(), 1.0, r.continuityRate(), 0);
        }
        // documented behaviour of the presets: precise is slower and aims tighter than fast
        assertTrue(rs.get(0).stats().get("duration_ms").p50() > rs.get(1).stats().get("duration_ms").p50());
        assertTrue(rs.get(0).stats().get("aim_offset").p50() < rs.get(1).stats().get("aim_offset").p50());
        String md = ProfileEvaluator.markdown(rs, 300, 9);
        assertTrue(md.contains("| precise | 100.0% | 100.0%"));
        assertTrue(ProfileEvaluator.csv(rs).lines().count() == 601);
        assertTrue(new EvaluationRenderer(rs, "t").image(1000, 700).getWidth() == 1000);
    }
}
