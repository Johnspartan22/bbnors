package org.bbnors.motion;

import org.bbnors.core.geom.Vec2;
import org.bbnors.core.random.Rng;
import org.bbnors.motion.path.ArcLengthTable;
import org.bbnors.motion.path.BezierPath;
import org.bbnors.motion.path.CatmullRomPath;
import org.bbnors.motion.path.LinearPath;
import org.bbnors.motion.path.Path;
import org.bbnors.motion.timing.BetaLaw;
import org.bbnors.motion.timing.TimingLaw;
import org.bbnors.motion.timing.TimingLaws;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Interpolation, derivative and arc-length correctness. */
public class PathAndTimingTest {

    private static List<Vec2> randomPoints(Rng r, int n) {
        List<Vec2> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            pts.add(new Vec2(r.uniform(0, 800), r.uniform(0, 600)));
        }
        return pts;
    }

    /** Reference de Casteljau evaluation. */
    private static Vec2 deCasteljau(List<Vec2> ctrl, double u) {
        Vec2[] p = ctrl.toArray(new Vec2[0]);
        for (int k = p.length - 1; k > 0; k--) {
            for (int i = 0; i < k; i++) {
                p[i] = p[i].lerp(p[i + 1], u);
            }
        }
        return p[0];
    }

    @Test
    public void bezierMatchesDeCasteljauForAllDegrees() {
        Rng r = new Rng(11);
        for (int deg = 1; deg <= 10; deg++) {
            List<Vec2> ctrl = randomPoints(r, deg + 1);
            BezierPath b = new BezierPath(ctrl);
            for (int i = 0; i <= 100; i++) {
                double u = i / 100.0;
                Vec2 ref = deCasteljau(ctrl, u);
                assertEquals("deg " + deg, 0, b.point(u).distance(ref), 1e-7);
            }
            assertEquals(0, b.point(0).distance(ctrl.get(0)), 1e-9);
            assertEquals(0, b.point(1).distance(ctrl.get(deg)), 1e-7);
        }
    }

    @Test
    public void analyticDerivativesMatchFiniteDifferences() {
        Rng r = new Rng(5);
        List<Path> paths = List.of(
                new LinearPath(Vec2.of(3, 4), Vec2.of(500, 90)),
                new BezierPath(randomPoints(r, 4)),
                new BezierPath(randomPoints(r, 7)),
                new CatmullRomPath(randomPoints(r, 5)),
                new CatmullRomPath(randomPoints(r, 3), 0.0));
        double h = 1e-6;
        double[] e = new double[6];
        double[] a = new double[6];
        double[] b = new double[6];
        for (Path p : paths) {
            for (int i = 1; i < 100; i++) {
                double u = i / 100.0 + 0.0031; // stay away from segment joins
                p.eval(u, e);
                p.eval(u - h, a);
                p.eval(u + h, b);
                double scale = 1 + Math.hypot(e[2], e[3]);
                assertEquals(p.getClass().getSimpleName(), (b[0] - a[0]) / (2 * h), e[2], 1e-4 * scale);
                assertEquals((b[1] - a[1]) / (2 * h), e[3], 1e-4 * scale);
                double scale2 = 1 + Math.hypot(e[4], e[5]);
                assertEquals((b[2] - a[2]) / (2 * h), e[4], 1e-3 * scale2);
                assertEquals((b[3] - a[3]) / (2 * h), e[5], 1e-3 * scale2);
            }
        }
    }

    @Test
    public void catmullRomInterpolatesWaypointsAndIsC1() {
        Rng r = new Rng(3);
        List<Vec2> pts = randomPoints(r, 6);
        CatmullRomPath p = new CatmullRomPath(pts);
        int seg = pts.size() - 1;
        double[] l = new double[6];
        double[] rr = new double[6];
        for (int k = 0; k <= seg; k++) {
            assertEquals(0, p.point(p.paramAtWaypoint(k)).distance(pts.get(k)), 1e-9);
            if (k > 0 && k < seg) {
                double u = p.paramAtWaypoint(k);
                p.eval(u - 1e-12, l);
                p.eval(u + 1e-12, rr);
                assertEquals("C1 at join " + k, l[2], rr[2], 1e-3 * (1 + Math.abs(l[2])));
                assertEquals(l[3], rr[3], 1e-3 * (1 + Math.abs(l[3])));
            }
        }
    }

    @Test
    public void arcLengthIsAccurateAndInverts() {
        assertEquals(500, new ArcLengthTable(new LinearPath(Vec2.of(0, 0), Vec2.of(300, 400))).length(), 1e-9);
        Rng r = new Rng(9);
        for (int trial = 0; trial < 20; trial++) {
            Path p = trial % 2 == 0 ? new BezierPath(randomPoints(r, 4 + trial % 4)) : new CatmullRomPath(randomPoints(r, 4));
            ArcLengthTable t = new ArcLengthTable(p);
            // reference length: dense polyline
            int n = 200_000;
            double ref = 0;
            Vec2 prev = p.point(0);
            double[] cumRef = new double[n + 1];
            for (int i = 1; i <= n; i++) {
                Vec2 q = p.point((double) i / n);
                ref += q.distance(prev);
                cumRef[i] = ref;
                prev = q;
            }
            assertEquals(ref, t.length(), Math.max(1e-3, ref * 1e-5));
            double lastU = -1;
            for (int k = 0; k <= 50; k++) {
                double s = ref * k / 50;
                double u = t.paramAt(s);
                assertTrue("monotone", u >= lastU - 1e-12);
                lastU = u;
                double x = u * n;
                int i0 = (int) Math.min(n - 1, Math.floor(x));
                double sAtU = cumRef[i0] + (cumRef[i0 + 1] - cumRef[i0]) * (x - i0);
                // These random control polygons are far curvier than real cursor paths; the
                // default table still inverts them to within 0.01 px (measured worst: 0.003 px).
                assertEquals("inverse at s=" + s, s, sAtU, 0.01);
            }
        }
    }

    private static final List<TimingLaw> LAWS = List.of(
            TimingLaws.LINEAR, TimingLaws.SMOOTHSTEP, TimingLaws.MINIMUM_JERK,
            TimingLaws.beta(0.4, 7), TimingLaws.beta(0.3, 12), TimingLaws.beta(0.5, 6),
            TimingLaws.trapezoidal(0.3, 0.3), TimingLaws.trapezoidal(0.1, 0.5));

    @Test
    public void timingLawsAreNormalisedMonotoneAndDifferentiable() {
        double h = 1e-6;
        for (TimingLaw law : LAWS) {
            assertEquals(law.name(), 0, law.s(0), 1e-12);
            assertEquals(law.name(), 1, law.s(1), 1e-12);
            double prev = -1;
            for (int i = 0; i <= 1000; i++) {
                double t = i / 1000.0;
                double s = law.s(t);
                assertTrue(law.name() + " monotone", s >= prev - 1e-12);
                prev = s;
                assertTrue(law.ds(t) >= -1e-12);
            }
            for (int i = 1; i < 200; i++) {
                double t = i / 200.0 + 0.0013;
                assertEquals(law.name() + " ds", (law.s(t + h) - law.s(t - h)) / (2 * h), law.ds(t), 1e-5);
                assertEquals(law.name() + " dds", (law.ds(t + h) - law.ds(t - h)) / (2 * h), law.dds(t), 1e-3);
            }
        }
    }

    @Test
    public void boundaryConditionsMatchTheirDocumentation() {
        for (TimingLaw law : List.of(TimingLaws.SMOOTHSTEP, TimingLaws.MINIMUM_JERK, TimingLaws.beta(0.4, 7))) {
            assertEquals(law.name(), 0, law.ds(0), 1e-12);
            assertEquals(law.name(), 0, law.ds(1), 1e-12);
        }
        assertEquals(0, TimingLaws.MINIMUM_JERK.dds(0), 1e-12);
        assertEquals(0, TimingLaws.MINIMUM_JERK.dds(1), 1e-12);
        assertEquals(1.875, TimingLaws.MINIMUM_JERK.ds(0.5), 1e-12);
    }

    @Test
    public void betaThreeThreeIsMinimumJerk() {
        BetaLaw b = new BetaLaw(3, 3);
        for (int i = 0; i <= 100; i++) {
            double t = i / 100.0;
            assertEquals(TimingLaws.MINIMUM_JERK.s(t), b.s(t), 1e-10);
            assertEquals(TimingLaws.MINIMUM_JERK.ds(t), b.ds(t), 1e-9);
            assertEquals(TimingLaws.MINIMUM_JERK.dds(t), b.dds(t), 1e-8);
        }
    }

    @Test
    public void betaPeakIsWhereRequested() {
        for (double peak : new double[]{0.3, 0.4, 0.5, 0.6}) {
            TimingLaw law = TimingLaws.beta(peak, 9);
            assertEquals(peak, law.peakTau(), 1e-12);
            double best = 0;
            double bestV = 0;
            for (int i = 0; i <= 10_000; i++) {
                double t = i / 10_000.0;
                if (law.ds(t) > bestV) {
                    bestV = law.ds(t);
                    best = t;
                }
            }
            assertEquals(peak, best, 2e-4);
        }
    }

    @Test
    public void fittsLawValues() {
        assertEquals(1.0, Fitts.indexOfDifficulty(10, 10), 1e-12);
        assertEquals(3.0, Fitts.indexOfDifficulty(70, 10), 1e-12);
        assertEquals(0.1 + 0.2 * 3, Fitts.movementTime(0.1, 0.2, 70, 10), 1e-12);
        assertEquals(0, Fitts.indexOfDifficulty(0, 10), 1e-12);
    }
}
