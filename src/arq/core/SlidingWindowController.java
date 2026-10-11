package arq.core;

import java.util.Arrays;

/**
 * Sender-side sliding-window bookkeeping.
 *
 *   base       = oldest packet that is not yet acknowledged
 *   nextSeq    = next packet that has never been sent
 *   windowSize = how many packets may be outstanding: [base, base + windowSize - 1]
 *
 * In Selective Repeat every packet is acknowledged individually, so packets inside the window
 * can be ACKed in any order. The window only slides forward once the packet at the base is ACKed.
 *
 * Sequence numbers are not wrapped around in this simulator (they simply run 0..N-1) so they are
 * easy to follow in the GUI. With k-bit wrapped sequence numbers a real implementation would also
 * have to keep the window at most 2^(k-1).
 */
public class SlidingWindowController {

    public enum Status { NOT_SENT, AWAITING_ACK, ACKED }

    private final Status[] status;
    private int base;
    private int nextSeq;
    private int windowSize;

    public SlidingWindowController(int totalPackets, int initialWindowSize) {
        status = new Status[totalPackets];
        Arrays.fill(status, Status.NOT_SENT);
        windowSize = Math.max(1, initialWindowSize);
    }

    /** True if the window still has room for a packet that has never been sent. */
    public boolean canSendNew() {
        return nextSeq < status.length && nextSeq < base + windowSize;
    }

    public int takeNextSeq() {
        status[nextSeq] = Status.AWAITING_ACK;
        return nextSeq++;
    }

    /** Marks a packet as acknowledged. Returns false if it was already ACKed (duplicate ACK). */
    public boolean markAcked(int seq) {
        if (status[seq] == Status.ACKED) {
            return false;
        }
        status[seq] = Status.ACKED;
        return true;
    }

    /** Moves the base past every acknowledged packet at the front of the window. Returns how far it moved. */
    public int slide() {
        int oldBase = base;
        while (base < status.length && status[base] == Status.ACKED) {
            base++;
        }
        return base - oldBase;
    }

    public boolean isAcked(int seq) {
        return status[seq] == Status.ACKED;
    }

    public boolean isComplete() {
        return base >= status.length;
    }

    /** Packets that were sent but are not yet acknowledged. */
    public int getOutstanding() {
        int count = 0;
        for (int seq = base; seq < nextSeq; seq++) {
            if (status[seq] == Status.AWAITING_ACK) {
                count++;
            }
        }
        return count;
    }

    public Status getStatus(int seq) {
        return status[seq];
    }

    public int getBase() {
        return base;
    }

    public int getNextSeq() {
        return nextSeq;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = Math.max(1, windowSize);
    }

    public int getTotalPackets() {
        return status.length;
    }
}
