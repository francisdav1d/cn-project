package arq.core;

/**
 * The main feature of the project: changes the sliding-window size while the transfer runs.
 *
 * Feedback is collected for one "round" - one window's worth of ACKs and failures, which takes
 * roughly one round-trip time. At the end of each round two signals are checked:
 *
 *   1. Failure rate f = (timeouts + NAKs) / feedback of this round.
 *      It is compared with the "normal" failure rate p of this network (a moving average), so the
 *      controller reacts to loss that INCREASES - e.g. because the window pushes the network into
 *      congestion - and not to the background loss a wireless-like link always has.
 *      A rise only counts if it is bigger than normal random variation:  sigma = sqrt(p(1-p)/n).
 *
 *   2. Delay ratio d = average RTT of this round / long-term average RTT.
 *      Growing queues in a congested network show up as rising delay before packets are dropped.
 *
 * Decision (AIMD - additive increase, multiplicative decrease, as in TCP congestion control):
 *
 *   f - p > max(3 sigma, 20%)   or  d >= 2.0   ->  window / 2     severe loss or congestion
 *   f - p > 2 sigma             or  d >= 1.4   ->  window - 1     conditions getting worse
 *   f - p <= sigma             and  d <= 1.2   ->  window + 1     no rise in loss or delay
 *                                                  (window * 2 during the initial slow start, which
 *                                                   ends at half the maximum window or at the first problem)
 *   otherwise                                  ->  unchanged
 *
 * The normal failure rate learns improvements quickly but accepts a worse network only slowly,
 * so persistent congestion is not mistaken for "normal" and the window stays reduced.
 * When adaptation is switched off the window stays at its initial size (fixed-window mode).
 */
public class AdaptiveWindowController {

    private static final double SEVERE_EXCESS_SIGMAS = 3.0;
    private static final double SEVERE_EXCESS_MIN = 0.20;
    private static final double HIGH_EXCESS_SIGMAS = 2.0;
    private static final double LOW_EXCESS_SIGMAS = 1.0;
    private static final double SEVERE_DELAY_RATIO = 2.0;
    private static final double HIGH_DELAY_RATIO = 1.4;
    private static final double LOW_DELAY_RATIO = 1.2;
    private static final double BASELINE_LEARN_BETTER = 0.20;   // failure rate dropped: learn quickly
    private static final double BASELINE_LEARN_WORSE = 0.05;    // failure rate rose: accept only slowly
    private static final double RTT_SMOOTHING = 0.20;

    private final SimulationContext ctx;
    private final SlidingWindowController window;
    private final int minWindow;
    private final int maxWindow;
    private final int slowStartThreshold;
    private boolean enabled;
    private boolean slowStart = true;

    private int roundAcks;
    private int roundFailures;
    private double roundRttSum;
    private double normalFailureRate = -1;
    private double longTermRtt = -1;
    private double lastFailureRate;
    private double lastDelayRatio = 1;

    public AdaptiveWindowController(SimulationContext ctx, SlidingWindowController window,
                                    int minWindow, int maxWindow, boolean enabled) {
        this.ctx = ctx;
        this.window = window;
        this.minWindow = Math.max(1, minWindow);
        this.maxWindow = Math.max(this.minWindow, maxWindow);
        this.slowStartThreshold = Math.max(this.minWindow, this.maxWindow / 2);
        this.enabled = enabled;
    }

    /** A new packet was acknowledged after {@code rtt} ms. */
    public void onAck(double rtt) {
        roundAcks++;
        roundRttSum += rtt;
        evaluateIfRoundComplete();
    }

    /** A packet had to be retransmitted (timeout or NAK). */
    public void onFailure() {
        roundFailures++;
        evaluateIfRoundComplete();
    }

    private void evaluateIfRoundComplete() {
        int current = window.getWindowSize();
        int feedback = roundAcks + roundFailures;
        if (feedback < current) {
            return;
        }

        // Signal 1: failures compared with what is normal for this network
        double failureRate = (double) roundFailures / feedback;
        if (normalFailureRate < 0) {
            normalFailureRate = failureRate;
        }
        double normal = normalFailureRate;
        double sigma = Math.sqrt(Math.max(normal * (1 - normal), 0.01) / feedback);
        double excess = failureRate - normal;
        double learningRate = failureRate > normal ? BASELINE_LEARN_WORSE : BASELINE_LEARN_BETTER;
        normalFailureRate += learningRate * (failureRate - normalFailureRate);

        // Signal 2: delay compared with the long-term average
        double delayRatio = 1.0;
        if (roundAcks > 0) {
            double averageRtt = roundRttSum / roundAcks;
            if (longTermRtt < 0) {
                longTermRtt = averageRtt;
            }
            delayRatio = averageRtt / longTermRtt;
            longTermRtt += RTT_SMOOTHING * (averageRtt - longTermRtt);
        }

        lastFailureRate = failureRate;
        lastDelayRatio = delayRatio;
        roundAcks = 0;
        roundFailures = 0;
        roundRttSum = 0;

        if (!enabled) {
            return;
        }

        int target;
        String verdict;
        if (excess > Math.max(SEVERE_EXCESS_SIGMAS * sigma, SEVERE_EXCESS_MIN) || delayRatio >= SEVERE_DELAY_RATIO) {
            target = current / 2;
            slowStart = false;
            verdict = "loss/delay jumped well above normal -> halve window";
        } else if (excess > HIGH_EXCESS_SIGMAS * sigma || delayRatio >= HIGH_DELAY_RATIO) {
            target = current - 1;
            slowStart = false;
            verdict = "loss/delay rising -> shrink window";
        } else if (excess <= LOW_EXCESS_SIGMAS * sigma && delayRatio <= LOW_DELAY_RATIO) {
            if (slowStart && current * 2 >= slowStartThreshold) {
                slowStart = false;
                target = Math.max(current + 1, slowStartThreshold);
            } else {
                target = slowStart ? current * 2 : current + 1;
            }
            verdict = target >= current * 2 ? "no rise in loss or delay -> double window (slow start)"
                                            : "no rise in loss or delay -> grow window";
        } else {
            target = current;
            verdict = "";
        }
        target = Math.max(minWindow, Math.min(maxWindow, target));

        if (target != current) {
            window.setWindowSize(target);
            ctx.stats.recordWindow(ctx.now(), target);
            ctx.emit(SimEvent.Type.WINDOW_CHANGE, -1, String.format(
                    "Window %d -> %d   [failures %.0f%% (normal %.0f%%), delay x%.2f]  %s",
                    current, target, failureRate * 100, normal * 100, delayRatio, verdict));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isInSlowStart() {
        return slowStart;
    }

    /** Failure rate of the last completed round (0..1). */
    public double getLastFailureRate() {
        return lastFailureRate;
    }

    /** What the controller currently considers a normal failure rate for this network (0..1). */
    public double getNormalFailureRate() {
        return Math.max(0, normalFailureRate);
    }

    public double getLastDelayRatio() {
        return lastDelayRatio;
    }
}
