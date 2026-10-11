package arq.gui;

import arq.core.AdaptiveWindowController;
import arq.core.PerformanceAnalyzer;
import arq.core.Simulation;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Live performance metrics (the Performance Analyzer's output). */
public class MetricsPanel extends JPanel {

    private final Map<String, JLabel> values = new LinkedHashMap<String, JLabel>();
    private int row;

    public MetricsPanel() {
        setLayout(new GridBagLayout());
        setBackground(Theme.PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        section("Delivery");
        metric("Packets delivered");
        metric("Delivery rate");
        metric("Throughput");
        metric("Elapsed time");

        section("Reliability");
        metric("Transmissions");
        metric("Retransmissions");
        metric("Packets lost");
        metric("  random / congestion");
        metric("Packets corrupted");
        metric("Timeouts");
        metric("NAKs");
        metric("ACKs lost");
        metric("Duplicates at receiver");
        metric("Packet loss rate");
        metric("Network efficiency");

        section("Delay");
        metric("Avg. transmission delay");
        metric("Avg. round-trip time");
        metric("Retransmission timeout");

        section("Sliding window");
        metric("Mode");
        metric("Current window");
        metric("Average window");
        metric("Min / max window");
        metric("Increases / decreases");
        metric("Last round failures");
        metric("Normal failure rate");
        metric("Last round delay");

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = row;
        filler.weighty = 1;
        add(Box.createVerticalGlue(), filler);
        setPreferredSize(new Dimension(300, getPreferredSize().height));
    }

    private void section(String title) {
        JLabel label = new JLabel(title.toUpperCase());
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() - 1));
        label.setForeground(Theme.MUTED);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(row == 1 ? 0 : 12, 0, 4, 0);
        add(label, c);
    }

    private void metric(String name) {
        JLabel label = new JLabel(name);
        label.setForeground(Theme.TEXT);
        JLabel value = new JLabel("-", SwingConstants.RIGHT);
        value.setFont(value.getFont().deriveFont(Font.BOLD));
        value.setForeground(Theme.TEXT);
        values.put(name, value);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row++;
        c.insets = new Insets(1, 0, 1, 0);
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 1;
        add(label, c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 0;
        add(value, c);
    }

    public void update(Simulation sim) {
        PerformanceAnalyzer s = sim.getStats();
        AdaptiveWindowController adaptive = sim.getAdaptive();
        double now = sim.now();
        int n = s.getTotalPackets();

        set("Packets delivered", s.getDelivered() + " / " + n);
        set("Delivery rate", pct(s.getDeliveryRate()));
        set("Throughput", String.format("%.1f pkt/s", s.getThroughput(now)));
        set("Elapsed time", String.format("%.2f s", s.getElapsed(now) / 1000));

        set("Transmissions", String.valueOf(s.getTransmissions()));
        set("Retransmissions", String.valueOf(s.getRetransmissions()));
        set("Packets lost", String.valueOf(s.getLostTotal()));
        set("  random / congestion", s.getLostRandom() + " / " + s.getLostCongestion());
        set("Packets corrupted", String.valueOf(s.getCorrupted()));
        set("Timeouts", String.valueOf(s.getTimeouts()));
        set("NAKs", String.valueOf(s.getNaks()));
        set("ACKs lost", String.valueOf(s.getAcksLost()));
        set("Duplicates at receiver", String.valueOf(s.getDuplicatePackets()));
        set("Packet loss rate", pct(s.getPacketLossRate()));
        set("Network efficiency", pct(s.getEfficiency()));

        set("Avg. transmission delay", String.format("%.0f ms", s.getAverageDelay()));
        set("Avg. round-trip time", String.format("%.0f ms", s.getAverageRtt()));
        set("Retransmission timeout", String.format("%.0f ms", sim.getRetransmissions().getRto()));

        set("Mode", !adaptive.isEnabled() ? "Fixed" : adaptive.isInSlowStart() ? "Adaptive (slow start)" : "Adaptive (AIMD)");
        set("Current window", String.valueOf(sim.getWindow().getWindowSize()));
        set("Average window", String.format("%.1f", s.getAverageWindow(now)));
        set("Min / max window", s.getMinWindowSeen() + " / " + s.getMaxWindowSeen());
        set("Increases / decreases", s.getWindowIncreases() + " / " + s.getWindowDecreases());
        set("Last round failures", pct(adaptive.getLastFailureRate() * 100));
        set("Normal failure rate", pct(adaptive.getNormalFailureRate() * 100));
        set("Last round delay", String.format("x%.2f", adaptive.getLastDelayRatio()));
    }

    private void set(String name, String text) {
        values.get(name).setText(text);
    }

    private static String pct(double value) {
        return String.format("%.1f %%", value);
    }
}
