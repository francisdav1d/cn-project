package arq.core;

import java.util.PriorityQueue;

/**
 * Discrete-event clock that drives the whole simulation.
 *
 * Instead of real threads and Thread.sleep(), every future action (a packet reaching the
 * receiver, an ACK reaching the sender, a retransmission timer expiring) is stored together
 * with the simulated time at which it happens, and events are executed strictly in time order.
 * This keeps a run reproducible for a given random seed, lets the GUI pause / step / change
 * speed, and lets the experiment runner simulate thousands of packets in milliseconds.
 */
public class EventScheduler {

    /** An action scheduled for a future simulated time. Timers keep a reference so they can cancel it. */
    public static final class ScheduledEvent implements Comparable<ScheduledEvent> {
        private final double time;
        private final long order;          // tie-breaker: events at the same time run in FIFO order
        private final Runnable action;
        private boolean cancelled;

        private ScheduledEvent(double time, long order, Runnable action) {
            this.time = time;
            this.order = order;
            this.action = action;
        }

        public void cancel() {
            cancelled = true;
        }

        public double getTime() {
            return time;
        }

        @Override
        public int compareTo(ScheduledEvent other) {
            int byTime = Double.compare(time, other.time);
            return byTime != 0 ? byTime : Long.compare(order, other.order);
        }
    }

    private final PriorityQueue<ScheduledEvent> queue = new PriorityQueue<>();
    private double now;
    private long counter;

    /** Current simulated time in milliseconds. */
    public double now() {
        return now;
    }

    public ScheduledEvent schedule(double delayMs, Runnable action) {
        ScheduledEvent event = new ScheduledEvent(now + Math.max(0, delayMs), counter++, action);
        queue.add(event);
        return event;
    }

    /** True if a (non-cancelled) event is due at or before the given time. */
    public boolean hasEventAtOrBefore(double time) {
        dropCancelled();
        return !queue.isEmpty() && queue.peek().time <= time;
    }

    /** Executes the next event in time order. Returns false when nothing is left. */
    public boolean runNext() {
        dropCancelled();
        ScheduledEvent event = queue.poll();
        if (event == null) {
            return false;
        }
        now = event.time;
        event.action.run();
        return true;
    }

    /** Moves the clock forward when no event happens before the given time. */
    public void advanceClockTo(double time) {
        if (time > now) {
            now = time;
        }
    }

    private void dropCancelled() {
        while (!queue.isEmpty() && queue.peek().cancelled) {
            queue.poll();
        }
    }
}
