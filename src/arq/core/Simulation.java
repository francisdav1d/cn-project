package arq.core;

import java.util.Arrays;

/**
 * Wires all components together (see the architecture diagram in the README):
 *
 *   PacketGenerator -> Sender -> SlidingWindowController -> UnreliableNetworkSimulator -> Receiver
 *                        ^                                                                 |
 *                        |   RetransmissionController <- AckManager <-- ACK / NAK ---------+
 *                        +-- AdaptiveWindowController (changes the window size)
 *
 * and exposes the run to the GUI and to the experiment runner.
 */
public class Simulation {

    private final SimulationConfig config;
    private final SimulationContext ctx;
    private final PerformanceAnalyzer stats;
    private final Packet[] packets;
    private final SlidingWindowController window;
    private final UnreliableNetworkSimulator network;
    private final Receiver receiver;
    private final RetransmissionController retransmissions;
    private final AdaptiveWindowController adaptive;
    private final Sender sender;
    private boolean started;

    public Simulation(SimulationConfig config, SimulationListener listener) {
        this.config = config;
        int n = config.getNumPackets();
        int minWindow = Math.max(1, config.getMinWindow());
        int maxWindow = Math.max(minWindow, config.getMaxWindow());
        int initialWindow = Math.max(minWindow, Math.min(maxWindow, config.getInitialWindow()));

        stats = new PerformanceAnalyzer(n);
        ctx = new SimulationContext(config.getSeed(), stats, listener);
        packets = new PacketGenerator(SimulationConfig.PAYLOAD_SIZE).generate(n);
        window = new SlidingWindowController(n, initialWindow);
        network = new UnreliableNetworkSimulator(ctx, config.getNetwork().copy());
        receiver = new Receiver(ctx, network, n);
        retransmissions = new RetransmissionController(ctx, n);
        adaptive = new AdaptiveWindowController(ctx, window, minWindow, maxWindow, config.isAdaptive());
        sender = new Sender(ctx, packets, window, network, retransmissions, adaptive);
        AckManager ackManager = new AckManager(ctx, window, retransmissions, adaptive, sender);
        network.connect(receiver, ackManager);
        retransmissions.setSender(sender);
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        stats.recordWindow(0, window.getWindowSize());
        stats.recordCapacity(0, network.getConfig().getCapacity());
        ctx.emit(SimEvent.Type.INFO, -1, String.format("Start: %d packets over a %s network, %s window (initial size %d)",
                packets.length, network.getConfig().describe(), adaptive.isEnabled() ? "adaptive" : "fixed",
                window.getWindowSize()));
        sender.start();
    }

    /** Runs every event up to the given simulated time (called by the GUI timer). */
    public void advanceTo(double time) {
        while (!isComplete() && ctx.scheduler.hasEventAtOrBefore(time)) {
            ctx.scheduler.runNext();
        }
        if (!isComplete()) {
            ctx.scheduler.advanceClockTo(time);
        }
    }

    /** Runs exactly one event (GUI "Step" button). */
    public boolean step() {
        return !isComplete() && ctx.scheduler.runNext();
    }

    /** Runs as fast as possible until every packet is acknowledged (experiment runner). */
    public void runToCompletion(double timeLimitMs) {
        start();
        while (!isComplete() && ctx.now() < timeLimitMs && ctx.scheduler.runNext()) {
            // keep processing events
        }
    }

    public boolean isComplete() {
        return window.isComplete();
    }

    public boolean isStarted() {
        return started;
    }

    /** Changes the network conditions in the middle of a transfer. */
    public void setNetworkConfig(NetworkConfig networkConfig) {
        network.setConfig(networkConfig.copy());
        if (started && !isComplete()) {
            stats.recordCapacity(ctx.now(), networkConfig.getCapacity());
            ctx.emit(SimEvent.Type.NETWORK_CHANGE, -1, "Network conditions changed: " + networkConfig.describe());
        } else if (!started) {
            config.setNetwork(networkConfig.copy());
        }
    }

    public void setAdaptive(boolean enabled) {
        if (adaptive.isEnabled() == enabled) {
            return;
        }
        adaptive.setEnabled(enabled);
        if (started && !isComplete()) {
            ctx.emit(SimEvent.Type.INFO, -1, enabled
                    ? "Adaptive window switched ON"
                    : "Adaptive window switched OFF - window stays at " + window.getWindowSize());
        }
    }

    /** True if the application received every packet, in order, identical to what was sent. */
    public boolean verifyIntegrity() {
        for (int seq = 0; seq < packets.length; seq++) {
            if (!Arrays.equals(packets[seq].getPayload(), receiver.getDeliveredData(seq))) {
                return false;
            }
        }
        return true;
    }

    public double now() {
        return ctx.now();
    }

    public int getTotalPackets() {
        return packets.length;
    }

    public SimulationConfig getConfig() {
        return config;
    }

    public PerformanceAnalyzer getStats() {
        return stats;
    }

    public SlidingWindowController getWindow() {
        return window;
    }

    public Receiver getReceiver() {
        return receiver;
    }

    public Sender getSender() {
        return sender;
    }

    public UnreliableNetworkSimulator getNetwork() {
        return network;
    }

    public RetransmissionController getRetransmissions() {
        return retransmissions;
    }

    public AdaptiveWindowController getAdaptive() {
        return adaptive;
    }
}
