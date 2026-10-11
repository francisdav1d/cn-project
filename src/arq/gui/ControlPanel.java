package arq.gui;

import arq.core.NetworkConfig;
import arq.core.SimulationConfig;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

/** Left-hand side: network conditions, sliding-window settings and simulation buttons. */
public class ControlPanel extends JPanel {

    private static final String CUSTOM = "Custom";

    private final MainFrame frame;

    private final JComboBox<String> presetBox = new JComboBox<String>(new String[] { "Good", "Moderate", "Poor", CUSTOM });
    private final JSpinner lossSpinner = spinner(0, 0, 60, 1);
    private final JSpinner corruptionSpinner = spinner(0, 0, 60, 1);
    private final JSpinner minDelaySpinner = spinner(0, 0, 5000, 10);
    private final JSpinner maxDelaySpinner = spinner(0, 0, 5000, 10);
    private final JSpinner congestionSpinner = spinner(0, 0, 100, 10);
    private final JSpinner capacitySpinner = spinner(1, 1, 200, 1);
    private final JCheckBox ackLossBox = new JCheckBox("ACK/NAK frames can be lost too", true);

    private final JSpinner packetsSpinner = spinner(100, 5, 1000, 5);
    private final JSpinner initialWindowSpinner = spinner(4, 1, 64, 1);
    private final JSpinner minWindowSpinner = spinner(1, 1, 64, 1);
    private final JSpinner maxWindowSpinner = spinner(32, 1, 64, 1);
    private final JCheckBox adaptiveBox = new JCheckBox("Adaptive (dynamic) window", true);

    private final JSlider speedSlider = new JSlider(0, 20, 7);
    private final JLabel speedLabel = new JLabel();
    private final JButton startButton = new JButton("Start");
    private final JButton pauseButton = new JButton("Pause");
    private final JButton stepButton = new JButton("Step");
    private final JButton resetButton = new JButton("Reset");

    private boolean updating;   // true while the code itself is changing field values

    public ControlPanel(MainFrame frame) {
        this.frame = frame;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Theme.BACKGROUND);

        JPanel network = section("Network conditions");
        row(network, "Preset", presetBox);
        row(network, "Packet loss (%)", lossSpinner);
        row(network, "Corruption (%)", corruptionSpinner);
        row(network, "Min delay (ms)", minDelaySpinner);
        row(network, "Max delay (ms)", maxDelaySpinner);
        row(network, "Congestion level (%)", congestionSpinner);
        row(network, "Capacity (frames)", capacitySpinner);
        wide(network, ackLossBox);
        hint(network, "Can be changed while the transfer is running - watch the window react.");

        JPanel window = section("Sliding window");
        row(window, "Packets to send", packetsSpinner);
        row(window, "Initial window", initialWindowSpinner);
        row(window, "Minimum window", minWindowSpinner);
        row(window, "Maximum window", maxWindowSpinner);
        wide(window, adaptiveBox);
        hint(window, "Unticked = fixed window of the initial size (for comparison).");

        JPanel simulation = section("Simulation");
        wide(simulation, speedLabel);
        wide(simulation, speedSlider);
        JPanel buttons = new JPanel(new GridLayout(2, 2, 6, 6));
        buttons.setOpaque(false);
        buttons.add(startButton);
        buttons.add(pauseButton);
        buttons.add(stepButton);
        buttons.add(resetButton);
        wide(simulation, buttons);
        startButton.setFont(startButton.getFont().deriveFont(Font.BOLD));

        add(network);
        add(Box.createVerticalStrut(8));
        add(window);
        add(Box.createVerticalStrut(8));
        add(simulation);
        add(Box.createVerticalGlue());

        applyPreset("Good");
        updateSpeedLabel();
        wireListeners();
    }

    // ------------------------------------------------------------------ values

    public SimulationConfig buildConfig() {
        SimulationConfig config = new SimulationConfig();
        int min = intValue(minWindowSpinner);
        int max = Math.max(min, intValue(maxWindowSpinner));
        config.setNumPackets(intValue(packetsSpinner));
        config.setMinWindow(min);
        config.setMaxWindow(max);
        config.setInitialWindow(Math.max(min, Math.min(max, intValue(initialWindowSpinner))));
        config.setAdaptive(adaptiveBox.isSelected());
        config.setNetwork(getNetworkConfig());
        return config;
    }

    public NetworkConfig getNetworkConfig() {
        int minDelay = intValue(minDelaySpinner);
        int maxDelay = intValue(maxDelaySpinner);
        return new NetworkConfig((String) presetBox.getSelectedItem(),
                intValue(lossSpinner) / 100.0, intValue(corruptionSpinner) / 100.0,
                Math.min(minDelay, maxDelay), Math.max(minDelay, maxDelay),
                intValue(congestionSpinner) / 100.0, intValue(capacitySpinner), ackLossBox.isSelected());
    }

    /** Simulated milliseconds per real millisecond: 0.05x ... 5x on a logarithmic slider. */
    public double getSpeed() {
        return 0.05 * Math.pow(10, speedSlider.getValue() / 10.0);
    }

    public boolean isAdaptive() {
        return adaptiveBox.isSelected();
    }

    /** Enables/disables buttons and fields for the current state of the simulation. */
    public void updateState(boolean started, boolean running, boolean finished) {
        startButton.setText(!started ? "Start" : finished ? "Restart" : "Resume");
        startButton.setEnabled(!running);
        pauseButton.setEnabled(running);
        stepButton.setEnabled(!running && !finished);
        for (JComponent c : new JComponent[] { packetsSpinner, initialWindowSpinner, minWindowSpinner, maxWindowSpinner }) {
            c.setEnabled(!started || finished);
        }
    }

    // ------------------------------------------------------------------ behaviour

    private void wireListeners() {
        presetBox.addActionListener(e -> {
            String selected = (String) presetBox.getSelectedItem();
            if (!updating && !CUSTOM.equals(selected)) {
                applyPreset(selected);
                frame.networkChanged(getNetworkConfig());
            }
        });
        for (JSpinner s : new JSpinner[] { lossSpinner, corruptionSpinner, minDelaySpinner, maxDelaySpinner,
                congestionSpinner, capacitySpinner }) {
            s.addChangeListener(e -> networkFieldEdited());
        }
        ackLossBox.addActionListener(e -> frame.networkChanged(getNetworkConfig()));
        adaptiveBox.addActionListener(e -> frame.adaptiveChanged(adaptiveBox.isSelected()));
        for (JSpinner s : new JSpinner[] { packetsSpinner, initialWindowSpinner, minWindowSpinner, maxWindowSpinner }) {
            s.addChangeListener(e -> {
                if (!updating) {
                    frame.setupChanged();
                }
            });
        }
        speedSlider.addChangeListener(e -> {
            updateSpeedLabel();
            frame.setSpeed(getSpeed());
        });
        startButton.addActionListener(e -> frame.startOrResume());
        pauseButton.addActionListener(e -> frame.pause());
        stepButton.addActionListener(e -> frame.step());
        resetButton.addActionListener(e -> frame.resetSimulation());
    }

    private void networkFieldEdited() {
        if (updating) {
            return;
        }
        updating = true;
        presetBox.setSelectedItem(CUSTOM);
        updating = false;
        frame.networkChanged(getNetworkConfig());
    }

    private void applyPreset(String name) {
        NetworkConfig preset = NetworkConfig.preset(name);
        if (preset == null) {
            return;
        }
        updating = true;
        presetBox.setSelectedItem(name);
        lossSpinner.setValue((int) Math.round(preset.getLossProbability() * 100));
        corruptionSpinner.setValue((int) Math.round(preset.getCorruptionProbability() * 100));
        minDelaySpinner.setValue(preset.getMinDelayMs());
        maxDelaySpinner.setValue(preset.getMaxDelayMs());
        congestionSpinner.setValue((int) Math.round(preset.getCongestionLevel() * 100));
        capacitySpinner.setValue(preset.getCapacity());
        updating = false;
    }

    private void updateSpeedLabel() {
        double speed = getSpeed();
        speedLabel.setText(String.format(speed < 1 ? "Speed: %.2fx real time" : "Speed: %.1fx real time", speed));
    }

    // ------------------------------------------------------------------ layout helpers

    private static JSpinner spinner(int value, int min, int max, int step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(5);
        return spinner;
    }

    private static int intValue(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private static JPanel section(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Theme.PANEL);
        TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Theme.BORDER), title);
        border.setTitleFont(panel.getFont().deriveFont(Font.BOLD));
        panel.setBorder(BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static void row(JPanel panel, String label, JComponent field) {
        int y = panel.getComponentCount();
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = y;
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 1;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.insets = new Insets(3, 0, 3, 0);
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 0;
        c.fill = field instanceof JComboBox ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        panel.add(field, c);
        fitHeight(panel);
    }

    private static void wide(JPanel panel, JComponent component) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = panel.getComponentCount();
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 2, 0);
        component.setOpaque(false);
        panel.add(component, c);
        fitHeight(panel);
    }

    private static void hint(JPanel panel, String text) {
        JLabel label = new JLabel("<html><div style='width:210px'>" + text + "</div></html>");
        label.setForeground(Theme.MUTED);
        label.setFont(label.getFont().deriveFont(label.getFont().getSize2D() - 1));
        wide(panel, label);
    }

    private static void fitHeight(JPanel panel) {
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
    }
}
