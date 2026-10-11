package arq.core;

/**
 * Unreliable virtual network between the sender and the receiver.
 *
 * Every frame that passes through it can be:
 *   - lost       : randomly dropped with the configured loss probability
 *   - dropped    : by congestion, when more frames are in the network than its capacity
 *   - corrupted  : one payload bit is flipped, so the receiver's CRC check fails
 *   - delayed    : random propagation delay, plus queueing delay while the network is congested
 *
 * Because every frame gets its own random delay, frames can also arrive out of order.
 *
 * Congestion model: the network can carry {@code capacity} frames (data + ACKs) at once. Every
 * frame above that is "backlog" waiting in a queue. With backlog b and n frames in the network,
 * a new frame is dropped with probability congestionLevel * b / n, and is delayed by an extra
 * congestionLevel * b * QUEUE_DELAY_PER_FRAME_MS. So the more the sender pushes into an
 * overloaded network, the more loss and delay it gets back - which the adaptive window detects.
 */
public class UnreliableNetworkSimulator {

    /** Extra queueing delay for every frame waiting beyond the network capacity. */
    private static final double QUEUE_DELAY_PER_FRAME_MS = 15;

    private final SimulationContext ctx;
    private NetworkConfig config;
    private Receiver receiver;
    private AckManager ackManager;
    private int framesInFlight;

    public UnreliableNetworkSimulator(SimulationContext ctx, NetworkConfig config) {
        this.ctx = ctx;
        this.config = config;
    }

    public void connect(Receiver receiver, AckManager ackManager) {
        this.receiver = receiver;
        this.ackManager = ackManager;
    }

    /** Sender -> Receiver. */
    public void sendToReceiver(final Packet packet) {
        NetworkConfig c = config;
        int seq = packet.getSeqNo();
        int backlog = backlog(c);

        if (chance(c.getLossProbability())) {
            ctx.stats.recordLoss(false);
            ctx.emit(SimEvent.Type.LOST, seq, "P" + seq + " lost in the network (random loss)");
            return;
        }
        if (chance(congestionDropProbability(c, backlog))) {
            ctx.stats.recordLoss(true);
            ctx.emit(SimEvent.Type.LOST, seq, String.format(
                    "P%d dropped - network congested (%d frames over capacity)", seq, backlog));
            return;
        }
        if (chance(c.getCorruptionProbability())) {
            packet.corrupt(ctx.random);
            ctx.stats.recordCorruption();
            ctx.emit(SimEvent.Type.CORRUPTED, seq, "P" + seq + " corrupted in transit (bit error)");
        }
        deliverLater(delay(c, backlog), () -> receiver.onPacketArrived(packet));
    }

    /** Receiver -> Sender (ACK or NAK). */
    public void sendToSender(final AckPacket ack) {
        NetworkConfig c = config;
        int backlog = backlog(c);
        boolean randomLoss = c.isAckLossEnabled() && chance(c.getLossProbability());
        if (randomLoss || chance(congestionDropProbability(c, backlog))) {
            ctx.stats.recordAckLost();
            ctx.emit(SimEvent.Type.ACK_LOST, ack.getSeqNo(), ack.getKind() + " " + ack.getSeqNo()
                    + (randomLoss ? " lost on the way back" : " dropped - network congested"));
            return;
        }
        deliverLater(delay(c, backlog), () -> ackManager.onAckArrived(ack));
    }

    private void deliverLater(double delayMs, Runnable arrival) {
        framesInFlight++;
        ctx.scheduler.schedule(delayMs, () -> {
            framesInFlight--;
            arrival.run();
        });
    }

    /** How many frames (counting the new one) exceed the capacity of the network. */
    private int backlog(NetworkConfig c) {
        return Math.max(0, framesInFlight + 1 - c.getCapacity());
    }

    private double congestionDropProbability(NetworkConfig c, int backlog) {
        if (backlog == 0) {
            return 0;
        }
        return c.getCongestionLevel() * backlog / (framesInFlight + 1);
    }

    private double delay(NetworkConfig c, int backlog) {
        double propagation = c.getMinDelayMs() + ctx.random.nextDouble() * (c.getMaxDelayMs() - c.getMinDelayMs());
        double queueing = c.getCongestionLevel() * backlog * QUEUE_DELAY_PER_FRAME_MS;
        return propagation + queueing;
    }

    private boolean chance(double probability) {
        return probability > 0 && ctx.random.nextDouble() < probability;
    }

    public NetworkConfig getConfig() {
        return config;
    }

    /** Network conditions can be changed while the transfer is running. */
    public void setConfig(NetworkConfig config) {
        this.config = config;
    }

    public int getFramesInFlight() {
        return framesInFlight;
    }
}
