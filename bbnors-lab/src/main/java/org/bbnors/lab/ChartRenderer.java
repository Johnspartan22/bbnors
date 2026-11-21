package org.bbnors.lab;

import org.bbnors.motion.MotionMetrics;
import org.bbnors.motion.TrajectorySamples;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Locale;

/**
 * Renders a comparison of up to three {@link TrajectoryRun}s with Java2D: trajectory in screen
 * space, speed over time, tangential acceleration over time, and a metrics table. Used by the
 * Swing lab and by headless PNG export, so what you see interactively is exactly what is exported.
 *
 * <p>Colour follows the run (slot order fixed: blue, orange, aqua; validated for all-pairs
 * CVD separation). Runs are always direct-labelled and the table repeats every value, so identity
 * never depends on colour alone.
 */
public final class ChartRenderer {

    public static final int MAX_RUNS = 3;

    static final Color SURFACE = new Color(0xfcfcfb);
    static final Color TEXT = new Color(0x0b0b0b);
    static final Color TEXT_2 = new Color(0x52514e);
    static final Color GRID = new Color(0xe7e6e1);
    static final Color AXIS = new Color(0xb9b8b1);
    static final Color TARGET = new Color(0x8a8983);
    static final Color[] SERIES = {new Color(0x2a78d6), new Color(0xeb6834), new Color(0x1baf7a)};

    private static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 15);
    private static final Font LABEL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 11);

    /** Panel geometry for a given canvas size (also used by the Swing view for hit-testing). */
    public record Layout(Rectangle2D path, Rectangle2D speed, Rectangle2D accel, Rectangle2D table) {
        public static Layout of(int w, int h) {
            double m = 24;
            double top = 56;
            double tableH = 30 + 22 * MAX_RUNS;
            double bodyH = h - top - tableH - m * 2;
            double leftW = (w - 3 * m) * 0.55;
            double rightW = w - 3 * m - leftW;
            Rectangle2D path = new Rectangle2D.Double(m, top, leftW, bodyH);
            double ch = (bodyH - m) / 2;
            Rectangle2D speed = new Rectangle2D.Double(m * 2 + leftW, top, rightW, ch);
            Rectangle2D accel = new Rectangle2D.Double(m * 2 + leftW, top + ch + m, rightW, ch);
            Rectangle2D table = new Rectangle2D.Double(m, top + bodyH + m, w - 2 * m, tableH);
            return new Layout(path, speed, accel, table);
        }
    }

    /** Optional overlays: replay time (seconds, &lt;0 = none) and an inspected time on the charts. */
    public record Overlay(double replayTime, double inspectTime) {
        public static final Overlay NONE = new Overlay(-1, -1);
    }

    private final List<TrajectoryRun> runs;
    private final String title;
    // world bounds of the trajectory panel (screen pixels)
    private double wx0;
    private double wy0;
    private double wx1;
    private double wy1;
    private double tMax;
    private double vMax;
    private double aAbs;

    public ChartRenderer(List<TrajectoryRun> runs, String title) {
        if (runs.isEmpty() || runs.size() > MAX_RUNS) {
            throw new IllegalArgumentException("1.." + MAX_RUNS + " runs (more would exceed the validated palette)");
        }
        this.runs = List.copyOf(runs);
        this.title = title;
        computeScales();
    }

    public List<TrajectoryRun> runs() {
        return runs;
    }

    public double maxTime() {
        return tMax;
    }

    private void computeScales() {
        wx0 = Double.MAX_VALUE;
        wy0 = Double.MAX_VALUE;
        wx1 = -Double.MAX_VALUE;
        wy1 = -Double.MAX_VALUE;
        for (TrajectoryRun r : runs) {
            TrajectorySamples s = r.samples();
            for (int i = 0; i < s.size(); i++) {
                wx0 = Math.min(wx0, s.x[i]);
                wy0 = Math.min(wy0, s.y[i]);
                wx1 = Math.max(wx1, s.x[i]);
                wy1 = Math.max(wy1, s.y[i]);
                vMax = Math.max(vMax, s.speed[i]);
                aAbs = Math.max(aAbs, Math.abs(s.accTangential[i]));
            }
            wx0 = Math.min(wx0, r.target().x());
            wy0 = Math.min(wy0, r.target().y());
            wx1 = Math.max(wx1, r.target().x() + r.target().width());
            wy1 = Math.max(wy1, r.target().y() + r.target().height());
            tMax = Math.max(tMax, r.motion().duration());
        }
        double pad = Math.max(20, Math.max(wx1 - wx0, wy1 - wy0) * 0.06);
        wx0 -= pad;
        wy0 -= pad;
        wx1 += pad;
        wy1 += pad;
        tMax = Math.max(tMax, 1e-3);
        vMax = niceCeil(Math.max(vMax, 1));
        aAbs = niceCeil(Math.max(aAbs, 1));
    }

    public void render(Graphics2D g, int w, int h, Overlay overlay) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(SURFACE);
        g.fillRect(0, 0, w, h);
        Layout L = Layout.of(w, h);
        g.setFont(TITLE);
        g.setColor(TEXT);
        g.drawString(title, 24, 30);
        if (runs.size() > 1) {
            drawLegend(g, w);
        }
        drawPathPanel(g, L.path(), overlay);
        drawTimeChart(g, L.speed(), "Speed (px/s) over time (ms)", false, overlay);
        drawTimeChart(g, L.accel(), "Tangential acceleration (px/s²) over time (ms)", true, overlay);
        drawTable(g, L.table());
    }

    private void drawLegend(Graphics2D g, int w) {
        g.setFont(LABEL);
        FontMetrics fm = g.getFontMetrics();
        double x = w - 24;
        for (int i = runs.size() - 1; i >= 0; i--) {
            String s = runs.get(i).label();
            x -= fm.stringWidth(s);
            g.setColor(TEXT_2);
            g.drawString(s, (float) x, 30);
            x -= 18;
            g.setColor(SERIES[i]);
            g.fill(new RoundRectangle2D.Double(x, 21, 12, 12, 4, 4));
            x -= 20;
        }
    }

    // ---------------------------------------------------------------- trajectory panel

    /** Maps screen-space world coordinates into the panel, preserving aspect ratio. */
    private double[] pathTransform(Rectangle2D p) {
        double sx = (p.getWidth() - 20) / (wx1 - wx0);
        double sy = (p.getHeight() - 36) / (wy1 - wy0);
        double s = Math.min(sx, sy);
        double ox = p.getX() + 10 + ((p.getWidth() - 20) - (wx1 - wx0) * s) / 2 - wx0 * s;
        double oy = p.getY() + 26 + ((p.getHeight() - 36) - (wy1 - wy0) * s) / 2 - wy0 * s;
        return new double[]{s, ox, oy};
    }

    /** Inverse mapping for hit-testing in the Swing view. */
    public double[] panelToWorld(Rectangle2D panel, double px, double py) {
        double[] t = pathTransform(panel);
        return new double[]{(px - t[1]) / t[0], (py - t[2]) / t[0]};
    }

    private void drawPathPanel(Graphics2D g, Rectangle2D p, Overlay overlay) {
        panelTitle(g, p, "Trajectory (screen px, y down) · dots every 25 ms");
        double[] t = pathTransform(p);
        double s = t[0];
        double ox = t[1];
        double oy = t[2];
        // recessive 50 px grid
        g.setStroke(new BasicStroke(1f));
        g.setColor(GRID);
        double step = niceStep((wx1 - wx0) / 8);
        for (double x = Math.ceil(wx0 / step) * step; x <= wx1; x += step) {
            g.draw(new Line2D.Double(ox + x * s, oy + wy0 * s, ox + x * s, oy + wy1 * s));
        }
        for (double y = Math.ceil(wy0 / step) * step; y <= wy1; y += step) {
            g.draw(new Line2D.Double(ox + wx0 * s, oy + y * s, ox + wx1 * s, oy + y * s));
        }
        g.setFont(SMALL);
        g.setColor(TEXT_2);
        g.drawString(String.format(Locale.ROOT, "grid %.0f px", step), (float) (p.getX() + p.getWidth() - 70), (float) (p.getY() + 14));
        for (int i = 0; i < runs.size(); i++) {
            TrajectoryRun r = runs.get(i);
            Color c = SERIES[i];
            // target
            g.setColor(TARGET);
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
            g.draw(new Rectangle2D.Double(ox + r.target().x() * s, oy + r.target().y() * s, r.target().width() * s, r.target().height() * s));
            // path
            TrajectorySamples sm = r.samples();
            Path2D path = new Path2D.Double();
            for (int k = 0; k < sm.size(); k++) {
                double x = ox + sm.x[k] * s;
                double y = oy + sm.y[k] * s;
                if (k == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            g.setColor(c);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);
            // time ticks every 25 ms: their spacing shows speed (close together = slow)
            double nextTick = 0;
            for (int k = 0; k < sm.size(); k++) {
                if (sm.t[k] + 1e-9 >= nextTick) {
                    double x = ox + sm.x[k] * s;
                    double y = oy + sm.y[k] * s;
                    g.setColor(SURFACE);
                    g.fill(new Ellipse2D.Double(x - 3.5, y - 3.5, 7, 7));
                    g.setColor(c);
                    g.fill(new Ellipse2D.Double(x - 2.2, y - 2.2, 4.4, 4.4));
                    nextTick += 0.025;
                }
            }
            // segment boundaries (overshoot corrections)
            for (int k = 1; k < sm.size(); k++) {
                if (sm.segment[k] != sm.segment[k - 1]) {
                    ring(g, ox + sm.x[k] * s, oy + sm.y[k] * s, 5, c);
                }
            }
            // start / end markers with surface ring
            ring(g, ox + sm.x[0] * s, oy + sm.y[0] * s, 5, c);
            int last = sm.size() - 1;
            double ex = ox + sm.x[last] * s;
            double ey = oy + sm.y[last] * s;
            g.setColor(SURFACE);
            g.fill(new Ellipse2D.Double(ex - 6, ey - 6, 12, 12));
            g.setColor(c);
            g.fill(new Ellipse2D.Double(ex - 4.5, ey - 4.5, 9, 9));
            // direct label at the path's midpoint, pushed away from the start-end chord
            int mid = sm.size() / 2;
            double mx = ox + sm.x[mid] * s;
            double my = oy + sm.y[mid] * s;
            double cxm = ox + (sm.x[0] + sm.x[last]) / 2 * s;
            double cym = oy + (sm.y[0] + sm.y[last]) / 2 * s;
            double nx = mx - cxm;
            double ny = my - cym;
            double nl = Math.hypot(nx, ny);
            if (nl < 1) {
                nx = sm.y[last] - sm.y[0];
                ny = -(sm.x[last] - sm.x[0]);
                nl = Math.max(1, Math.hypot(nx, ny));
            }
            g.setFont(LABEL);
            FontMetrics lfm = g.getFontMetrics();
            double lx = mx + nx / nl * 14 - (nx < 0 ? lfm.stringWidth(r.label()) : 0);
            double ly = my + ny / nl * 14 + (ny > 0 ? 10 : 0);
            g.setColor(SURFACE);
            g.fill(new Rectangle2D.Double(lx - 2, ly - 11, lfm.stringWidth(r.label()) + 4, 14));
            g.setColor(TEXT);
            g.drawString(r.label(), (float) lx, (float) ly);
            if (overlay.replayTime() >= 0) {
                org.bbnors.motion.MotionState st = new org.bbnors.motion.MotionState();
                r.motion().state(overlay.replayTime(), st);
                double cx = ox + st.x * s;
                double cy = oy + st.y * s;
                g.setColor(SURFACE);
                g.fill(new Ellipse2D.Double(cx - 8, cy - 8, 16, 16));
                g.setColor(c);
                g.setStroke(new BasicStroke(2f));
                g.draw(new Ellipse2D.Double(cx - 6, cy - 6, 12, 12));
                g.draw(new Line2D.Double(cx - 10, cy, cx + 10, cy));
                g.draw(new Line2D.Double(cx, cy - 10, cx, cy + 10));
            }
        }
    }

    private static void ring(Graphics2D g, double x, double y, double r, Color c) {
        g.setColor(SURFACE);
        g.fill(new Ellipse2D.Double(x - r - 2, y - r - 2, 2 * r + 4, 2 * r + 4));
        g.setColor(c);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
    }

    // ---------------------------------------------------------------- time charts

    /** Plot area inside a chart panel (excluding title and axis labels). */
    public static Rectangle2D plotArea(Rectangle2D p) {
        return new Rectangle2D.Double(p.getX() + 56, p.getY() + 26, p.getWidth() - 66, p.getHeight() - 50);
    }

    /** Time (s) at a horizontal pixel position in a chart panel. */
    public double timeAt(Rectangle2D panel, double px) {
        Rectangle2D a = plotArea(panel);
        return Math.max(0, Math.min(tMax, (px - a.getX()) / a.getWidth() * tMax));
    }

    private void drawTimeChart(Graphics2D g, Rectangle2D p, String name, boolean signed, Overlay overlay) {
        panelTitle(g, p, name);
        Rectangle2D a = plotArea(p);
        double yMin = signed ? -aAbs : 0;
        double yMax = signed ? aAbs : vMax;
        // grid + y ticks
        g.setFont(SMALL);
        FontMetrics fm = g.getFontMetrics();
        double ystep = niceStep((yMax - yMin) / 4);
        for (double v = Math.ceil(yMin / ystep) * ystep; v <= yMax + 1e-9; v += ystep) {
            double y = a.getMaxY() - (v - yMin) / (yMax - yMin) * a.getHeight();
            g.setColor(Math.abs(v) < 1e-9 && signed ? AXIS : GRID);
            g.setStroke(new BasicStroke(1f));
            g.draw(new Line2D.Double(a.getX(), y, a.getMaxX(), y));
            g.setColor(TEXT_2);
            String lbl = fmtTick(v);
            g.drawString(lbl, (float) (a.getX() - 6 - fm.stringWidth(lbl)), (float) (y + 4));
        }
        // x ticks (ms)
        double tstepMs = niceStep(tMax * 1000 / 5);
        for (double ms = 0; ms <= tMax * 1000 + 1e-9; ms += tstepMs) {
            double x = a.getX() + ms / 1000 / tMax * a.getWidth();
            String lbl = fmtTick(ms);
            if (x + fm.stringWidth(lbl) / 2.0 > a.getMaxX() + 4) continue;
            g.setColor(TEXT_2);
            g.drawString(lbl, (float) (x - fm.stringWidth(lbl) / 2.0), (float) (a.getMaxY() + 15));
        }
        g.setColor(AXIS);
        g.draw(new Line2D.Double(a.getX(), a.getMaxY(), a.getMaxX(), a.getMaxY()));
        for (int i = 0; i < runs.size(); i++) {
            TrajectorySamples s = runs.get(i).samples();
            double[] ys = signed ? s.accTangential : s.speed;
            Path2D path = new Path2D.Double();
            for (int k = 0; k < s.size(); k++) {
                double x = a.getX() + s.t[k] / tMax * a.getWidth();
                double y = a.getMaxY() - (ys[k] - yMin) / (yMax - yMin) * a.getHeight();
                if (k == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            g.setColor(SERIES[i]);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);
        }
        double it = overlay.inspectTime() >= 0 ? overlay.inspectTime() : overlay.replayTime();
        if (it >= 0) {
            double x = a.getX() + it / tMax * a.getWidth();
            g.setColor(TEXT_2);
            g.setStroke(new BasicStroke(1f));
            g.draw(new Line2D.Double(x, a.getY(), x, a.getMaxY()));
            org.bbnors.motion.MotionState st = new org.bbnors.motion.MotionState();
            double ty = a.getY() + 12;
            g.setFont(MONO);
            for (int i = 0; i < runs.size(); i++) {
                runs.get(i).motion().state(it, st);
                double v = signed ? st.accTangential : st.speed;
                double y = a.getMaxY() - (v - yMin) / (yMax - yMin) * a.getHeight();
                ring(g, x, y, 4, SERIES[i]);
                String txt = String.format(Locale.ROOT, "%s %.0f", runs.get(i).label(), v);
                g.setColor(TEXT);
                g.drawString(txt, (float) (x + 8), (float) (ty + 13 * i));
            }
            g.setColor(TEXT_2);
            g.drawString(String.format(Locale.ROOT, "t=%.0f ms", it * 1000), (float) (x + 8), (float) (ty + 13 * runs.size()));
        }
    }

    private static void panelTitle(Graphics2D g, Rectangle2D p, String t) {
        g.setFont(LABEL.deriveFont(Font.BOLD));
        g.setColor(TEXT);
        g.drawString(t, (float) p.getX(), (float) (p.getY() + 14));
    }

    // ---------------------------------------------------------------- table

    private static final String[] COLS = {"run", "path", "law", "dur ms", "len px", "straight", "dev px", "v peak", "peak@", "a peak", "rms jerk", "max step", "end err", "in target", "segs", "plan µs (warm)"};

    private void drawTable(Graphics2D g, Rectangle2D r) {
        g.setFont(MONO);
        FontMetrics fm = g.getFontMetrics();
        double[] widths = new double[COLS.length];
        String[][] rows = new String[runs.size()][];
        for (int i = 0; i < runs.size(); i++) {
            rows[i] = row(runs.get(i));
        }
        for (int c = 0; c < COLS.length; c++) {
            widths[c] = fm.stringWidth(COLS[c]);
            for (String[] row : rows) widths[c] = Math.max(widths[c], fm.stringWidth(row[c]));
        }
        double gap = 14;
        double x = r.getX();
        double y = r.getY() + 14;
        g.setColor(TEXT_2);
        double cx = x + 16;
        for (int c = 0; c < COLS.length; c++) {
            g.drawString(COLS[c], (float) cx, (float) y);
            cx += widths[c] + gap;
        }
        g.setColor(GRID);
        g.draw(new Line2D.Double(x, y + 6, r.getMaxX(), y + 6));
        for (int i = 0; i < rows.length; i++) {
            double ry = y + 24 + 22 * i;
            g.setColor(SERIES[i]);
            g.fill(new RoundRectangle2D.Double(x, ry - 10, 10, 10, 4, 4));
            cx = x + 16;
            g.setColor(TEXT);
            for (int c = 0; c < COLS.length; c++) {
                g.drawString(rows[i][c], (float) cx, (float) ry);
                cx += widths[c] + gap;
            }
        }
    }

    static String[] row(TrajectoryRun r) {
        MotionMetrics m = r.metrics();
        return new String[]{
                r.label(),
                r.profile().path().name().toLowerCase(Locale.ROOT),
                r.profile().timingLaw().name(),
                String.format(Locale.ROOT, "%.0f", m.duration() * 1000),
                String.format(Locale.ROOT, "%.1f", m.pathLength()),
                String.format(Locale.ROOT, "%.3f", m.straightness()),
                String.format(Locale.ROOT, "%.1f", m.maxDeviation()),
                String.format(Locale.ROOT, "%.0f", m.peakSpeed()),
                String.format(Locale.ROOT, "%.2f", m.peakSpeedTime()),
                String.format(Locale.ROOT, "%.0f", m.peakAcceleration()),
                String.format(Locale.ROOT, "%.3g", m.rmsJerk()),
                String.format(Locale.ROOT, "%.2f", m.maxStep()),
                String.format(Locale.ROOT, "%.2g", m.endpointError()),
                m.endInTarget() ? "yes" : "NO",
                Integer.toString(m.segments()),
                String.format(Locale.ROOT, "%.1f", r.planNanos() / 1000.0)};
    }

    // ---------------------------------------------------------------- helpers

    static double niceStep(double raw) {
        if (raw <= 0) return 1;
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double n = raw / mag;
        double nice = n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10;
        return nice * mag;
    }

    static double niceCeil(double v) {
        double step = niceStep(v / 4);
        return Math.ceil(v / step) * step;
    }

    private static String fmtTick(double v) {
        double a = Math.abs(v);
        if (a >= 10_000) return String.format(Locale.ROOT, "%.0fk", v / 1000);
        if (a >= 1000 && a % 1000 == 0) return String.format(Locale.ROOT, "%.0fk", v / 1000);
        if (a >= 1000) return String.format(Locale.ROOT, "%.1fk", v / 1000);
        return v == Math.rint(v) ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
