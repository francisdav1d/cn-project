package arq.core;

/**
 * Sender side of Selective Repeat ARQ. Sends every packet the sliding window allows, keeps the
 * original of each packet until it is acknowledged, and retransmits single packets when the
 * RetransmissionController asks for it.
 */
public class Sender {

    private final SimulationContext ctx;
    private final Packet[] packets;
    private final int[] transmissionCount;
    private final SlidingWindowController window;
    private final UnreliableNetworkSimulator network;
    private final RetransmissionController retransmissions;
    private final AdaptiveWindowController adaptive;

    public Sender(SimulationContext ctx, Packet[] packets, SlidingWindowController window,
                  UnreliableNetworkSimulator network, RetransmissionController retransmissions,
                  AdaptiveWindowController adaptive) {
        this.ctx = ctx;
        this.packets = packets;
        this.transmissionCount = new int[packets.length];
        this.window = window;
        this.network = network;
        this.retransmissions = retransmissions;
        this.adaptive = adaptive;
    }

    public void start() {
        trySendNew();
    }

    /** Fills the free part of the window with packets that were never sent before. */
    public void trySendNew() {
        while (window.canSendNew()) {
            transmit(window.takeNextSeq());
        }
    }

    /** Selective retransmission: resends only this one packet. */
    public void retransmit(int seq) {
        if (window.isAcked(seq)) {
            return;
        }
        adaptive.onFailure();
        transmit(seq);
    }

    private void transmit(int seq) {
        int attempt = ++transmissionCount[seq];
        Packet copy = packets[seq].copyForTransmission(attempt, ctx.now());
        ctx.stats.recordTransmission(seq, attempt == 1, ctx.now());
        if (attempt == 1) {
            ctx.emit(SimEvent.Type.SEND, seq, String.format("Sending P%d   (window %d..%d, size %d)",
                    seq, window.getBase(), window.getBase() + window.getWindowSize() - 1, window.getWindowSize()));
        } else {
            ctx.emit(SimEvent.Type.RETRANSMIT, seq, String.format(
                    "Selective retransmission of P%d only (attempt %d)", seq, attempt));
        }
        retransmissions.startTimer(seq);
        network.sendToReceiver(copy);
    }

    public int getTransmissionCount(int seq) {
        return transmissionCount[seq];
    }

    public Packet[] getPackets() {
        return packets;
    }
}
