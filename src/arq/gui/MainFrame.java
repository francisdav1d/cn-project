package arq.gui;

import arq.core.NetworkConfig;
import arq.core.PerformanceAnalyzer;
import arq.core.SimEvent;
import arq.core.Simulation;
import arq.core.SimulationListener;
import arq.core.SlidingWindowController;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Main window: controls on the left, packet view, window graph and event log in the middle,
 * performance metrics on the right.
 *
 * A Swing timer fires every TICK_MS of real time and advances the simulated clock by
 * TICK_MS * speed, so all simulation work happens on the Swing event thread.
 */
public class MainFrame extends JFrame implements SimulationListener {

    private static final int TICK_MS = 40;

    private final PacketPanel packetPanel = new PacketPanel();
    private final WindowGraphPanel graphPanel = new WindowGraphPanel();
    private final MetricsPanel metricsPanel = new MetricsPanel();
    private final EventLogPanel logPanel = new EventLogPanel();
    private final JLabel statusLabel = new JLabel(" ");
    private final ControlPanel controls;
    private final Timer timer;
    private final JSplitPane centerSplit;

    private Simulation simulation;
    private double speed;
    private boolean summaryShown;

    public MainFrame() {
        super("Adaptive Selective Repeat ARQ - Dynamic Sliding Window & Network Condition Simulation");
        controls = new ControlPanel(this);
        speed = controls.getSpeed();
        timer = new Timer(TICK_MS, e -> tick());

        JScrollPane packetScroll = new JScrollPane(packetPanel);
        packetScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        packetScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        graphPanel.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        JSplitPane lower = split(graphPanel, logPanel, 0.5);
        centerSplit = split(packetScroll, lower, 0.5);

        JScrollPane controlScroll = new JScrollPane(controls);
        controlScroll.setBorder(null);
        controlScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        controlScroll.getViewport().setBackground(Theme.BACKGROUND);
        controlScroll.setPreferredSize(new Dimension(controls.getPreferredSize().width + 18, 400));

        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 4));
        statusLabel.setForeground(Theme.MUTED);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));
        root.setBackground(Theme.BACKGROUND);
        root.add(controlScroll, BorderLayout.WEST);
        root.add(centerSplit, BorderLayout.CENTER);
        root.add(metricsPanel, BorderLayout.EAST);
        root.add(statusLabel, BorderLayout.SOUTH);
        setContentPane(root);

        resetSimulation();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1440, 900);
        setMinimumSize(new Dimension(1150, 680));
        setLocationRelativeTo(null);
        setExtendedState(MAXIMIZED_BOTH);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                fitPacketView();
            }
        });
    }

    /** Gives the packet view the height it needs (at most 55%), the graph and log share the rest. */
    void fitPacketView() {
        validate();
        int available = centerSplit.getHeight();
        if (available > 0) {
            int wanted = packetPanel.getPreferredSize().height + 4;
            centerSplit.setDividerLocation(Math.min(wanted, (int) (available * 0.55)));
        }
    }

    private static JSplitPane split(java.awt.Component top, java.awt.Component bottom, double weight) {
        JSplitPane pane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        pane.setResizeWeight(weight);
        pane.setBorder(null);
        pane.setDividerSize(8);
        pane.setContinuousLayout(true);
        return pane;
    }

    // ------------------------------------------------------------------ actions from the control panel

    void startOrResume() {
        if (simulation.isComplete()) {
            resetSimulation();
        }
        simulation.start();
        timer.start();
        refresh();
    }

    void pause() {
        timer.stop();
        refresh();
    }

    /** Processes exactly one event so the protocol can be followed step by step. */
    void step() {
        timer.stop();
        if (!simulation.isStarted()) {
            simulation.start();
        } else {
            simulation.step();
        }
        refresh();
        checkFinished();
    }

    void resetSimulation() {
        timer.stop();
        simulation = new Simulation(controls.buildConfig(), this);
        summaryShown = false;
        logPanel.clear();
        packetPanel.setSimulation(simulation);
        graphPanel.setSimulation(simulation);
        refresh();
        SwingUtilities.invokeLater(this::fitPacketView);
    }

    /** Packet count or window limits changed: they only apply to a new run. */
    void setupChanged() {
        if (!simulation.isStarted() || simulation.isComplete()) {
            resetSimulation();
        }
    }

    void networkChanged(NetworkConfig config) {
        simulation.setNetworkConfig(config);
        refresh();
    }

    void adaptiveChanged(boolean adaptive) {
        if (!simulation.isStarted() || simulation.isComplete()) {
            resetSimulation();
        } else {
            simulation.setAdaptive(adaptive);
            refresh();
        }
    }

    void setSpeed(double speed) {
        this.speed = speed;
    }

    // ------------------------------------------------------------------ simulation loop

    private void tick() {
        simulation.advanceTo(simulation.now() + TICK_MS * speed);
        refresh();
        checkFinished();
    }

    private void checkFinished() {
        if (simulation.isComplete() && !summaryShown) {
            summaryShown = true;
            timer.stop();
            refresh();
            SwingUtilities.invokeLater(this::showSummary);
        }
    }

    @Override
    public void onEvent(SimEvent event) {
        logPanel.add(event);
        packetPanel.onEvent(event);
    }

    void refresh() {
        boolean running = timer.isRunning();
        controls.updateState(simulation.isStarted(), running, simulation.isComplete());
        metricsPanel.update(simulation);
        packetPanel.revalidate();
        packetPanel.repaint();
        graphPanel.repaint();
        logPanel.flush();

        SlidingWindowController w = simulation.getWindow();
        String state = simulation.isComplete() ? "Finished" : running ? "Running" : simulation.isStarted() ? "Paused" : "Ready";
        statusLabel.setText(String.format(
                "%s   |   t = %.2f s   |   base %d   |   next %d   |   window %d   |   outstanding %d   |   frames in network %d   |   RTO %.0f ms   |   network: %s",
                state, simulation.now() / 1000, w.getBase(), w.getNextSeq(), w.getWindowSize(), w.getOutstanding(),
                simulation.getNetwork().getFramesInFlight(), simulation.getRetransmissions().getRto(),
                simulation.getNetwork().getConfig().getName()));
    }

    private void showSummary() {
        PerformanceAnalyzer s = simulation.getStats();
        double now = simulation.now();
        boolean intact = simulation.verifyIntegrity();
        String html = "<html><body style='width:380px'>"
                + "<h2 style='margin:0 0 6px 0'>Transfer complete</h2>"
                + "<table cellpadding='2'>"
                + row("Packets delivered", s.getDelivered() + " / " + s.getTotalPackets()
                        + String.format(" (%.1f%%)", s.getDeliveryRate()))
                + row("Time", String.format("%.2f s", s.getElapsed(now) / 1000))
                + row("Throughput", String.format("%.1f packets/s", s.getThroughput(now)))
                + row("Transmissions / retransmissions", s.getTransmissions() + " / " + s.getRetransmissions())
                + row("Packet loss rate", String.format("%.1f%%", s.getPacketLossRate()))
                + row("Corrupted packets (NAKs)", s.getCorrupted() + " (" + s.getNaks() + ")")
                + row("Timeouts", String.valueOf(s.getTimeouts()))
                + row("Avg. transmission delay", String.format("%.0f ms", s.getAverageDelay()))
                + row("Network efficiency", String.format("%.1f%%", s.getEfficiency()))
                + row("Average window", String.format("%.1f (min %d, max %d)", s.getAverageWindow(now),
                        s.getMinWindowSeen(), s.getMaxWindowSeen()))
                + "</table><p style='margin-top:8px'>Data integrity check: "
                + (intact ? "<b style='color:#15803d'>PASSED</b> - every packet reached the application in order and identical to what was sent."
                          : "<b style='color:#dc2626'>FAILED</b>")
                + "</p></body></html>";
        JOptionPane.showMessageDialog(this, html, "Simulation summary", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String row(String label, String value) {
        return "<tr><td>" + label + "</td><td><b>" + value + "</b></td></tr>";
    }

    /** Used by the screenshot/demo tooling. */
    Simulation getSimulation() {
        return simulation;
    }
}
