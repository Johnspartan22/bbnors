package org.bbnors.lab;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

/**
 * Chart for a {@link ProfileEvaluator} run: movement duration against Fitts index of difficulty
 * (one dot per trial, fitted line per profile), plus a summary table. Up to three profiles, using
 * the same validated palette as {@link ChartRenderer}.
 */
public final class EvaluationRenderer {

    private static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 15);
    private static final Font LABEL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 11);

    private final List<ProfileEvaluator.ProfileReport> reports;
    private final String title;

    public EvaluationRenderer(List<ProfileEvaluator.ProfileReport> reports, String title) {
        if (reports.isEmpty() || reports.size() > ChartRenderer.MAX_RUNS) {
            throw new IllegalArgumentException("1.." + ChartRenderer.MAX_RUNS + " profiles per chart");
        }
        this.reports = List.copyOf(reports);
        this.title = title;
    }

    public BufferedImage image(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            render(g, w, h);
        } finally {
            g.dispose();
        }
        return img;
    }

    public void render(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(ChartRenderer.SURFACE);
        g.fillRect(0, 0, w, h);
        g.setFont(TITLE);
        g.setColor(ChartRenderer.TEXT);
        g.drawString(title, 24, 30);
        legend(g, w);
        double tableH = 40 + 22 * reports.size();
        Rectangle2D panel = new Rectangle2D.Double(24, 56, w - 48, h - 56 - tableH - 40);
        scatter(g, panel);
        table(g, new Rectangle2D.Double(24, panel.getMaxY() + 30, w - 48, tableH));
    }

    private void legend(Graphics2D g, int w) {
        if (reports.size() < 2) return;
        g.setFont(LABEL);
        FontMetrics fm = g.getFontMetrics();
        double x = w - 24;
        for (int i = reports.size() - 1; i >= 0; i--) {
            String s = reports.get(i).profile().name();
            x -= fm.stringWidth(s);
            g.setColor(ChartRenderer.TEXT_2);
            g.drawString(s, (float) x, 30);
            x -= 18;
            g.setColor(ChartRenderer.SERIES[i]);
            g.fill(new RoundRectangle2D.Double(x, 21, 12, 12, 4, 4));
            x -= 20;
        }
    }

    private void scatter(Graphics2D g, Rectangle2D p) {
        g.setFont(LABEL.deriveFont(Font.BOLD));
        g.setColor(ChartRenderer.TEXT);
        g.drawString("Movement duration (ms) vs Fitts index of difficulty (bits), one dot per trial, line = least-squares fit", (float) p.getX(), (float) (p.getY() + 14));
        Rectangle2D a = new Rectangle2D.Double(p.getX() + 56, p.getY() + 28, p.getWidth() - 70, p.getHeight() - 52);
        double xMax = 0;
        double yMax = 0;
        for (ProfileEvaluator.ProfileReport r : reports) {
            for (ProfileEvaluator.Trial t : r.trials()) {
                xMax = Math.max(xMax, t.scenario().indexOfDifficulty());
                yMax = Math.max(yMax, t.metrics().duration() * 1000);
            }
        }
        xMax = ChartRenderer.niceCeil(Math.max(1, xMax));
        yMax = ChartRenderer.niceCeil(Math.max(1, yMax));
        g.setFont(SMALL);
        FontMetrics fm = g.getFontMetrics();
        double ys = ChartRenderer.niceStep(yMax / 5);
        for (double v = 0; v <= yMax + 1e-9; v += ys) {
            double y = a.getMaxY() - v / yMax * a.getHeight();
            g.setColor(v == 0 ? ChartRenderer.AXIS : ChartRenderer.GRID);
            g.draw(new Line2D.Double(a.getX(), y, a.getMaxX(), y));
            g.setColor(ChartRenderer.TEXT_2);
            String l = String.format(Locale.ROOT, "%.0f", v);
            g.drawString(l, (float) (a.getX() - 6 - fm.stringWidth(l)), (float) (y + 4));
        }
        double xs = ChartRenderer.niceStep(xMax / 8);
        for (double v = 0; v <= xMax + 1e-9; v += xs) {
            double x = a.getX() + v / xMax * a.getWidth();
            String l = v == Math.rint(v) ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v);
            g.setColor(ChartRenderer.TEXT_2);
            g.drawString(l, (float) (x - fm.stringWidth(l) / 2.0), (float) (a.getMaxY() + 15));
        }
        for (int i = 0; i < reports.size(); i++) {
            Color c = ChartRenderer.SERIES[i];
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 120));
            for (ProfileEvaluator.Trial t : reports.get(i).trials()) {
                double x = a.getX() + t.scenario().indexOfDifficulty() / xMax * a.getWidth();
                double y = a.getMaxY() - t.metrics().duration() * 1000 / yMax * a.getHeight();
                g.fill(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
            }
        }
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setFont(LABEL);
        for (int i = 0; i < reports.size(); i++) {
            ProfileEvaluator.ProfileReport r = reports.get(i);
            double y0 = a.getMaxY() - r.fittedA() * 1000 / yMax * a.getHeight();
            double y1 = a.getMaxY() - (r.fittedA() + r.fittedB() * xMax) * 1000 / yMax * a.getHeight();
            g.setColor(ChartRenderer.SURFACE);
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(a.getX(), y0, a.getMaxX(), y1));
            g.setColor(ChartRenderer.SERIES[i]);
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(a.getX(), y0, a.getMaxX(), y1));
            String lbl = String.format(Locale.ROOT, "%s: %.0f + %.0f·ID ms", r.profile().name(), r.fittedA() * 1000, r.fittedB() * 1000);
            double ly = Math.max(a.getY() + 12, y1 - 6 - 15 * i);
            g.setColor(ChartRenderer.SURFACE);
            g.fill(new Rectangle2D.Double(a.getMaxX() - 4 - g.getFontMetrics().stringWidth(lbl) - 2, ly - 11, g.getFontMetrics().stringWidth(lbl) + 4, 14));
            g.setColor(ChartRenderer.TEXT);
            g.drawString(lbl, (float) (a.getMaxX() - 4 - g.getFontMetrics().stringWidth(lbl)), (float) ly);
        }
    }

    private static final String[] COLS = {"profile", "trials", "in target", "continuity", "overshoot", "fitts a/b cfg", "fitts a/b fit", "R²",
            "dur p50 ms", "peak@ p50", "straight p50", "dev p95 px", "aim p50"};

    private void table(Graphics2D g, Rectangle2D r) {
        g.setFont(MONO);
        FontMetrics fm = g.getFontMetrics();
        String[][] rows = new String[reports.size()][];
        for (int i = 0; i < reports.size(); i++) {
            ProfileEvaluator.ProfileReport p = reports.get(i);
            rows[i] = new String[]{
                    p.profile().name(),
                    Integer.toString(p.trials().size()),
                    String.format(Locale.ROOT, "%.1f%%", p.inTargetRate() * 100),
                    String.format(Locale.ROOT, "%.1f%%", p.continuityRate() * 100),
                    String.format(Locale.ROOT, "%.1f%%", p.overshootRate() * 100),
                    String.format(Locale.ROOT, "%.0f/%.0f", p.profile().fittsA() * 1000, p.profile().fittsB() * 1000),
                    String.format(Locale.ROOT, "%.0f/%.0f", p.fittedA() * 1000, p.fittedB() * 1000),
                    String.format(Locale.ROOT, "%.2f", p.fitR2()),
                    ProfileEvaluator.fmt(p.stats().get("duration_ms").p50()),
                    ProfileEvaluator.fmt(p.stats().get("peak_speed_time").p50()),
                    ProfileEvaluator.fmt(p.stats().get("straightness").p50()),
                    ProfileEvaluator.fmt(p.stats().get("max_deviation_px").p95()),
                    ProfileEvaluator.fmt(p.stats().get("aim_offset").p50())};
        }
        double[] widths = new double[COLS.length];
        for (int c = 0; c < COLS.length; c++) {
            widths[c] = fm.stringWidth(COLS[c]);
            for (String[] row : rows) widths[c] = Math.max(widths[c], fm.stringWidth(row[c]));
        }
        double y = r.getY() + 14;
        double cx = r.getX() + 16;
        g.setColor(ChartRenderer.TEXT_2);
        for (int c = 0; c < COLS.length; c++) {
            g.drawString(COLS[c], (float) cx, (float) y);
            cx += widths[c] + 14;
        }
        g.setColor(ChartRenderer.GRID);
        g.draw(new Line2D.Double(r.getX(), y + 6, r.getMaxX(), y + 6));
        for (int i = 0; i < rows.length; i++) {
            double ry = y + 24 + 22 * i;
            g.setColor(ChartRenderer.SERIES[i]);
            g.fill(new RoundRectangle2D.Double(r.getX(), ry - 10, 10, 10, 4, 4));
            cx = r.getX() + 16;
            g.setColor(ChartRenderer.TEXT);
            for (int c = 0; c < COLS.length; c++) {
                g.drawString(rows[i][c], (float) cx, (float) ry);
                cx += widths[c] + 14;
            }
        }
        g.setFont(SMALL);
        g.setColor(ChartRenderer.TEXT_2);
        g.drawString("Fitts constants in ms. Fitted values include duration jitter and overshoot corrections. aim = end-point distance from target centre in half-widths.",
                (float) r.getX(), (float) (y + 24 + 22 * rows.length + 4));
    }
}
