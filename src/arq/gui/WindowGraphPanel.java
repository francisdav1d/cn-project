package arq.gui;

import arq.core.PerformanceAnalyzer;
import arq.core.Simulation;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.List;

/** Line chart of the sliding-window size over time, with the network capacity and failure moments. */
public class WindowGraphPanel extends JPanel {

    private static final int LEFT = 48;
    private static final int RIGHT = 20;
    private static final int TOP = 34;
    private static final int BOTTOM = 30;

    private Simulation simulation;

    public WindowGraphPanel() {
        setBackground(Theme.PANEL);
        setPreferredSize(new Dimension(700, 220));
        setMinimumSize(new Dimension(300, 150));
    }

    public void setSimulation(Simulation simulation) {
        this.simulation = simulation;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Font base = getFont();
        g.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 1));
        g.setColor(Theme.TEXT);
        g.drawString("Sliding-window size (packets) over time (seconds)", LEFT, 20);
        drawLegend(g);

        int w = getWidth() - LEFT - RIGHT;
        int h = getHeight() - TOP - BOTTOM;
        if (simulation == null || w < 50 || h < 40) {
            g.dispose();
            return;
        }

        PerformanceAnalyzer stats = simulation.getStats();
        List<double[]> windows = stats.getWindowHistory();
        List<double[]> capacities = stats.getCapacityHistory();
        double end = Math.max(stats.getElapsed(simulation.now()), 1000);
        double tMax = niceTimeMax(end);

        int yMaxData = Math.max(simulation.getWindow().getWindowSize(), stats.getMaxWindowSeen());
        for (double[] c : capacities) {
            yMaxData = Math.max(yMaxData, (int) c[1]);
        }
        int yStep = yMaxData <= 8 ? 1 : yMaxData <= 20 ? 2 : yMaxData <= 40 ? 5 : 10;
        int yMax = ((yMaxData + 1) / yStep + 1) * yStep;

        // grid and axes
        g.setFont(base.deriveFont(base.getSize2D() - 1));
        FontMetrics fm = g.getFontMetrics();
        for (int v = 0; v <= yMax; v += yStep) {
            int y = TOP + h - (int) Math.round((double) v / yMax * h);
            g.setColor(Theme.GRID);
            g.drawLine(LEFT, y, LEFT + w, y);
            g.setColor(Theme.MUTED);
            String label = String.valueOf(v);
            g.drawString(label, LEFT - 8 - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
        }
        double tStep = niceTimeStep(tMax);
        for (double t = 0; t <= tMax + 1e-6; t += tStep) {
            int x = LEFT + (int) Math.round(t / tMax * w);
            g.setColor(Theme.GRID);
            g.drawLine(x, TOP + h, x, TOP + h + 4);
            g.setColor(Theme.MUTED);
            String label = formatSeconds(t);
            g.drawString(label, x - fm.stringWidth(label) / 2, TOP + h + 6 + fm.getAscent());
        }
        g.setColor(Theme.BORDER);
        g.drawLine(LEFT, TOP + h, LEFT + w, TOP + h);

        // failure moments (timeouts and NAKs) as small red ticks on the time axis
        g.setColor(Theme.LOST);
        for (double t : stats.getFailureTimes()) {
            int x = LEFT + (int) Math.round(Math.min(t, end) / tMax * w);
            g.drawLine(x, TOP + h - 1, x, TOP + h - 7);
        }

        // network capacity (dashed step line)
        g.setColor(Theme.CAPACITY_LINE);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 6f, 5f }, 0f));
        g.draw(stepPath(capacities, end, tMax, yMax, w, h, false));

        // window size (filled step line)
        if (!windows.isEmpty()) {
            g.setColor(Theme.WINDOW_AREA);
            g.fill(stepPath(windows, end, tMax, yMax, w, h, true));
            g.setColor(Theme.WINDOW_LINE);
            g.setStroke(new BasicStroke(2.2f));
            g.draw(stepPath(windows, end, tMax, yMax, w, h, false));
        }
        g.dispose();
    }

    private Path2D stepPath(List<double[]> points, double end, double tMax, int yMax, int w, int h, boolean closed) {
        Path2D path = new Path2D.Double();
        if (points.isEmpty()) {
            return path;
        }
        double baseline = TOP + h;
        double x0 = LEFT + points.get(0)[0] / tMax * w;
        if (closed) {
            path.moveTo(x0, baseline);
            path.lineTo(x0, y(points.get(0)[1], yMax, h));
        } else {
            path.moveTo(x0, y(points.get(0)[1], yMax, h));
        }
        for (int i = 1; i < points.size(); i++) {
            double x = LEFT + Math.min(points.get(i)[0], end) / tMax * w;
            path.lineTo(x, y(points.get(i - 1)[1], yMax, h));
            path.lineTo(x, y(points.get(i)[1], yMax, h));
        }
        double xEnd = LEFT + end / tMax * w;
        path.lineTo(xEnd, y(points.get(points.size() - 1)[1], yMax, h));
        if (closed) {
            path.lineTo(xEnd, baseline);
            path.closePath();
        }
        return path;
    }

    private static double y(double value, int yMax, int h) {
        return TOP + h - value / yMax * h;
    }

    private void drawLegend(Graphics2D g) {
        Font font = getFont().deriveFont(getFont().getSize2D() - 1);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        String[] labels = { "Window size", "Network capacity", "Timeout / NAK" };
        int x = getWidth() - RIGHT;
        for (int i = labels.length - 1; i >= 0; i--) {
            x -= fm.stringWidth(labels[i]);
            g.setColor(Theme.MUTED);
            g.drawString(labels[i], x, 20);
            x -= 24;
            int y = 20 - fm.getAscent() / 2 + 1;
            if (i == 0) {
                g.setColor(Theme.WINDOW_LINE);
                g.setStroke(new BasicStroke(2.2f));
                g.drawLine(x, y, x + 18, y);
            } else if (i == 1) {
                g.setColor(Theme.CAPACITY_LINE);
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 5f, 3f }, 0f));
                g.drawLine(x, y, x + 18, y);
            } else {
                g.setColor(Theme.LOST);
                g.setStroke(new BasicStroke(1f));
                g.drawLine(x + 9, y - 4, x + 9, y + 4);
            }
            g.setStroke(new BasicStroke(1f));
            x -= 18;
        }
    }

    private static double niceTimeMax(double endMs) {
        double step = niceTimeStep(endMs);
        return Math.ceil(endMs / step) * step;
    }

    private static double niceTimeStep(double rangeMs) {
        double[] steps = { 250, 500, 1000, 2000, 5000, 10000, 15000, 30000, 60000, 120000, 300000, 600000 };
        for (double step : steps) {
            if (rangeMs / step <= 10) {
                return step;
            }
        }
        return 1200000;
    }

    private static String formatSeconds(double ms) {
        double s = ms / 1000.0;
        return s == Math.rint(s) ? String.valueOf((long) s) : String.format("%.2f", s).replaceAll("0+$", "");
    }
}
