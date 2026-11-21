package org.bbnors.lab;

import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.motion.MovementProfile;
import org.bbnors.motion.MovementProfiles;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive movement lab (Swing).
 *
 * <ul>
 *   <li>Pick up to three profiles to compare side by side; tune the first one's parameters live.</li>
 *   <li>Left-drag on the trajectory panel to move the start point, right-click to move the target.</li>
 *   <li>Hover the speed/acceleration charts to inspect values at a time; Replay animates the cursor
 *       along all runs in real time.</li>
 *   <li>New seed regenerates; Export writes PNG + CSV + JSON + the exact profiles used.</li>
 * </ul>
 */
public final class LabWindow {

    private final MovementProfiles profiles = new MovementProfiles();
    private final List<JComboBox<String>> pickers = new ArrayList<>();
    private final JSlider curvature = slider(0, 60, 12);
    private final JSlider peak = slider(10, 90, 42);
    private final JSlider sharpness = slider(4, 30, 7);
    private final JSlider fittsB = slider(20, 300, 110);
    private final JSlider spread = slider(0, 100, 35);
    private final JComboBox<MovementProfile.PathKind> pathKind = new JComboBox<>(MovementProfile.PathKind.values());
    private final JComboBox<MovementProfile.LawKind> lawKind = new JComboBox<>(MovementProfile.LawKind.values());
    private final JCheckBox overshoot = new JCheckBox("overshoot", true);
    private final JSpinner seed = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
    private final JLabel status = new JLabel(" ");
    private final Canvas canvas = new Canvas();
    private Vec2 from = new Vec2(90, 420);
    private Rect target = Rect.centered(new Vec2(610, 140), 34, 28);
    private boolean loading;

    public static void open() {
        SwingUtilities.invokeLater(() -> new LabWindow().show());
    }

    private void show() {
        JFrame f = new JFrame("$bbnors movement lab");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setLayout(new BorderLayout());
        f.add(controls(), BorderLayout.NORTH);
        f.add(canvas, BorderLayout.CENTER);
        f.add(status, BorderLayout.SOUTH);
        f.setSize(1500, 980);
        f.setLocationRelativeTo(null);
        loadFirstProfileIntoControls();
        regenerate();
        f.setVisible(true);
    }

    private JPanel controls() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        String[] names = profiles.all().stream().map(MovementProfile::name).toArray(String[]::new);
        String[] defaults = {"standard", "smooth-spline", "linear-baseline"};
        for (int i = 0; i < ChartRenderer.MAX_RUNS; i++) {
            JComboBox<String> c = new JComboBox<>(i == 0 ? names : prepend("(none)", names));
            c.setSelectedItem(defaults[i]);
            c.addActionListener(e -> {
                if (pickers.indexOf(c) == 0) loadFirstProfileIntoControls();
                regenerate();
            });
            pickers.add(c);
            row1.add(new JLabel(i == 0 ? "Run A (tunable)" : i == 1 ? "B" : "C"));
            row1.add(c);
        }
        row1.add(Box.createHorizontalStrut(16));
        row1.add(new JLabel("seed"));
        row1.add(seed);
        seed.addChangeListener(e -> regenerate());
        JButton next = new JButton("New seed");
        next.addActionListener(e -> seed.setValue(((Integer) seed.getValue()) + 1));
        JButton replay = new JButton("Replay");
        replay.addActionListener(e -> canvas.replay());
        JButton export = new JButton("Export…");
        export.addActionListener(e -> export());
        row1.add(next);
        row1.add(replay);
        row1.add(export);
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.setBorder(BorderFactory.createTitledBorder("Run A parameters"));
        row2.add(new JLabel("path"));
        row2.add(pathKind);
        row2.add(new JLabel("law"));
        row2.add(lawKind);
        row2.add(labeled("curvature", curvature));
        row2.add(labeled("peak", peak));
        row2.add(labeled("sharpness", sharpness));
        row2.add(labeled("fitts b (ms/bit)", fittsB));
        row2.add(labeled("endpoint spread", spread));
        row2.add(overshoot);
        for (JSlider s : List.of(curvature, peak, sharpness, fittsB, spread)) {
            s.addChangeListener(e -> regenerate());
        }
        pathKind.addActionListener(e -> regenerate());
        lawKind.addActionListener(e -> regenerate());
        overshoot.addActionListener(e -> regenerate());
        top.add(row1);
        top.add(row2);
        return top;
    }

    private static String[] prepend(String first, String[] rest) {
        String[] out = new String[rest.length + 1];
        out[0] = first;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }

    private static JSlider slider(int min, int max, int v) {
        JSlider s = new JSlider(min, max, v);
        s.setPreferredSize(new Dimension(110, 24));
        return s;
    }

    private static JPanel labeled(String name, JSlider s) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JLabel(name), BorderLayout.NORTH);
        p.add(s, BorderLayout.CENTER);
        return p;
    }

    private void loadFirstProfileIntoControls() {
        MovementProfile p = profiles.get((String) pickers.get(0).getSelectedItem());
        loading = true;
        pathKind.setSelectedItem(p.path());
        lawKind.setSelectedItem(p.law());
        curvature.setValue((int) Math.round(p.curvature() * 100));
        peak.setValue((int) Math.round(p.peak() * 100));
        sharpness.setValue((int) Math.round(p.sharpness()));
        fittsB.setValue((int) Math.round(p.fittsB() * 1000));
        spread.setValue((int) Math.round(p.endpointSpread() * 100));
        overshoot.setSelected(p.overshootChance() > 0);
        loading = false;
    }

    private MovementProfile tunedFirst() {
        MovementProfile base = profiles.get((String) pickers.get(0).getSelectedItem());
        return base.toBuilder().name(base.name() + "*")
                .path((MovementProfile.PathKind) pathKind.getSelectedItem())
                .law((MovementProfile.LawKind) lawKind.getSelectedItem())
                .curvature(curvature.getValue() / 100.0)
                .peak(peak.getValue() / 100.0)
                .sharpness(sharpness.getValue())
                .fittsB(fittsB.getValue() / 1000.0)
                .endpointSpread(spread.getValue() / 100.0)
                .overshootChance(overshoot.isSelected() ? Math.max(base.overshootChance(), 0.15) : 0)
                .build();
    }

    private void regenerate() {
        if (loading || pickers.size() < ChartRenderer.MAX_RUNS) return;
        long s = ((Integer) seed.getValue()).longValue();
        List<TrajectoryRun> runs = new ArrayList<>();
        try {
            runs.add(TrajectoryRun.of(tunedFirst(), from, target, s));
        } catch (IllegalArgumentException ex) {
            status.setText("Invalid parameters: " + ex.getMessage());
            return;
        }
        for (int i = 1; i < pickers.size(); i++) {
            String n = (String) pickers.get(i).getSelectedItem();
            if (n != null && !n.equals("(none)")) {
                runs.add(TrajectoryRun.of(profiles.get(n), from, target, s));
            }
        }
        canvas.setRenderer(new ChartRenderer(runs, String.format("Seed %d · %.0f px to a %.0f×%.0f target", s, from.distance(target.center()), target.width(), target.height())));
        status.setText("Drag on the trajectory to move the start; right-click to move the target. " + runs.get(0).motion().describe());
    }

    private void export() {
        JFileChooser ch = new JFileChooser();
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (ch.showSaveDialog(canvas) != JFileChooser.APPROVE_OPTION || canvas.renderer == null) return;
        try {
            List<Path> files = Exporter.all(canvas.renderer, ch.getSelectedFile().toPath(), "lab-seed-" + seed.getValue());
            status.setText("Exported " + files.size() + " files to " + ch.getSelectedFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(canvas, ex.toString(), "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Drawing surface with hover inspection and replay. */
    private final class Canvas extends JPanel {
        ChartRenderer renderer;
        double inspect = -1;
        double replayT = -1;
        Timer timer;

        Canvas() {
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    if (renderer == null) return;
                    ChartRenderer.Layout l = ChartRenderer.Layout.of(getWidth(), getHeight());
                    if (ChartRenderer.plotArea(l.speed()).contains(e.getPoint()) || ChartRenderer.plotArea(l.accel()).contains(e.getPoint())) {
                        inspect = renderer.timeAt(l.speed(), e.getX());
                    } else {
                        inspect = -1;
                    }
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    moveEndpoint(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    moveEndpoint(e);
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private void moveEndpoint(MouseEvent e) {
            if (renderer == null) return;
            ChartRenderer.Layout l = ChartRenderer.Layout.of(getWidth(), getHeight());
            if (!l.path().contains(e.getPoint())) return;
            double[] w = renderer.panelToWorld(l.path(), e.getX(), e.getY());
            if (SwingUtilities.isRightMouseButton(e)) {
                target = Rect.centered(new Vec2(w[0], w[1]), target.width(), target.height());
            } else {
                from = new Vec2(w[0], w[1]);
            }
            regenerate();
        }

        void setRenderer(ChartRenderer r) {
            renderer = r;
            repaint();
        }

        void replay() {
            if (renderer == null) return;
            if (timer != null) timer.stop();
            long start = System.nanoTime();
            double total = renderer.maxTime() + 0.25;
            timer = new Timer(10, e -> {
                double t = (System.nanoTime() - start) / 1e9;
                replayT = t <= total ? Math.min(t, renderer.maxTime()) : -1;
                if (t > total) ((Timer) e.getSource()).stop();
                repaint();
            });
            timer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (renderer != null) {
                renderer.render((Graphics2D) g, getWidth(), getHeight(), new ChartRenderer.Overlay(replayT, inspect));
            }
        }
    }
}
