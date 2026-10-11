package arq.core;

/**
 * Selective-Repeat retransmission logic.
 *
 * Every outstanding packet has its OWN timer. When a timer expires (the packet or its ACK was
 * lost) or the receiver sends a NAK (the packet arrived corrupted), only that single packet is
 * sent again - not the whole window as in Go-Back-N.
 *
 * The timeout (RTO) follows the measured round-trip time with the standard Jacobson/Karels
 * estimator from RFC 6298:  SRTT = 7/8 SRTT + 1/8 RTT,  RTTVAR = 3/4 RTTVAR + 1/4 |SRTT - RTT|,
 * RTO = SRTT + 4 * RTTVAR. So the timer gets longer automatically when the network slows down.
 */
public class RetransmissionController {

    private static final double INITIAL_RTO_MS = 2000;
    private static final double MIN_RTO_MS = 200;
    private static final double MAX_RTO_MS = 8000;

    private final SimulationContext ctx;
    private final EventScheduler.ScheduledEvent[] timers;
    private Sender sender;
    private double srtt = -1;
    private double rttVar;
    private double rto = INITIAL_RTO_MS;

    public RetransmissionController(SimulationContext ctx, int totalPackets) {
        this.ctx = ctx;
        this.timers = new EventScheduler.ScheduledEvent[totalPackets];
    }

    public void setSender(Sender sender) {
        this.sender = sender;
    }

    public void startTimer(final int seq) {
        stopTimer(seq);
        timers[seq] = ctx.scheduler.schedule(rto, () -> onTimeout(seq));
    }

    public void stopTimer(int seq) {
        if (timers[seq] != null) {
            timers[seq].cancel();
            timers[seq] = null;
        }
    }

    private void onTimeout(int seq) {
        timers[seq] = null;
        ctx.stats.recordTimeout(ctx.now());
        ctx.emit(SimEvent.Type.TIMEOUT, seq, String.format("Timer of P%d expired (RTO %.0f ms) - no ACK", seq, rto));
        sender.retransmit(seq);
    }

    /** NAK from the receiver: the packet arrived damaged, so resend it now instead of waiting for the timer. */
    public void onNak(int seq) {
        stopTimer(seq);
        ctx.stats.recordNak(ctx.now());
        sender.retransmit(seq);
    }

    public void updateRtt(double sample) {
        if (srtt < 0) {
            srtt = sample;
            rttVar = sample / 2;
        } else {
            rttVar = 0.75 * rttVar + 0.25 * Math.abs(srtt - sample);
            srtt = 0.875 * srtt + 0.125 * sample;
        }
        rto = Math.max(MIN_RTO_MS, Math.min(MAX_RTO_MS, srtt + 4 * rttVar));
    }

    public double getRto() {
        return rto;
    }

    public double getSrtt() {
        return srtt;
    }
}
