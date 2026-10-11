package arq.core;

/**
 * Processes the ACK and NAK frames that reach the sender: marks packets as acknowledged, stops
 * their timers, measures the round-trip time, slides the window and lets the sender fill it again.
 */
public class AckManager {

    private final SimulationContext ctx;
    private final SlidingWindowController window;
    private final RetransmissionController retransmissions;
    private final AdaptiveWindowController adaptive;
    private final Sender sender;

    public AckManager(SimulationContext ctx, SlidingWindowController window,
                      RetransmissionController retransmissions, AdaptiveWindowController adaptive, Sender sender) {
        this.ctx = ctx;
        this.window = window;
        this.retransmissions = retransmissions;
        this.adaptive = adaptive;
        this.sender = sender;
    }

    public void onAckArrived(AckPacket ack) {
        if (ack.getKind() == AckPacket.Kind.NAK) {
            handleNak(ack);
            return;
        }
        int seq = ack.getSeqNo();
        if (!window.markAcked(seq)) {
            ctx.stats.recordDuplicateAck();
            ctx.emit(SimEvent.Type.ACK_RECEIVED, seq, "Duplicate ACK " + seq + " ignored");
            return;
        }

        double rtt = ctx.now() - ack.getEchoSendTime();
        retransmissions.stopTimer(seq);
        retransmissions.updateRtt(rtt);
        ctx.stats.recordAck(rtt);

        int oldBase = window.getBase();
        int moved = window.slide();
        if (moved > 0) {
            ctx.emit(SimEvent.Type.ACK_RECEIVED, seq, String.format(
                    "ACK %d received (RTT %.0f ms) -> window slides, base %d -> %d", seq, rtt, oldBase, window.getBase()));
        } else {
            ctx.emit(SimEvent.Type.ACK_RECEIVED, seq, String.format(
                    "ACK %d received (RTT %.0f ms) -> window waits for ACK %d", seq, rtt, window.getBase()));
        }

        adaptive.onAck(rtt);

        if (window.isComplete()) {
            ctx.stats.recordCompletion(ctx.now());
            ctx.emit(SimEvent.Type.COMPLETE, -1, "All packets acknowledged - transfer complete");
            return;
        }
        sender.trySendNew();
    }

    private void handleNak(AckPacket nak) {
        int seq = nak.getSeqNo();
        // A NAK for an older copy is stale: that packet was already retransmitted or acknowledged.
        if (window.isAcked(seq) || nak.getTransmission() != sender.getTransmissionCount(seq)) {
            ctx.emit(SimEvent.Type.NAK_RECEIVED, seq, "Stale NAK " + seq + " ignored (packet already resent or ACKed)");
            return;
        }
        ctx.emit(SimEvent.Type.NAK_RECEIVED, seq, "NAK " + seq + " received -> P" + seq + " was corrupted, resend it");
        retransmissions.onNak(seq);
    }
}
