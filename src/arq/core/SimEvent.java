package arq.core;

/** One entry of the event log. The GUI also uses these to colour packets that were lost or corrupted. */
public class SimEvent {

    public enum Type {
        INFO,
        SEND,            // first transmission of a packet
        RETRANSMIT,      // selective retransmission of one packet
        LOST,            // data packet dropped by the network (random or congestion)
        CORRUPTED,       // data packet damaged by the network
        RECEIVED,        // receiver accepted the next in-order packet
        BUFFERED,        // receiver accepted an out-of-order packet and buffered it
        DUPLICATE,       // receiver got a packet it already had (its ACK was lost or late)
        CHECKSUM_FAIL,   // receiver detected corruption and sent a NAK
        DELIVERED,       // a gap was filled and several buffered packets went to the application
        ACK_LOST,        // ACK or NAK dropped on the way back
        ACK_RECEIVED,    // sender got an ACK
        NAK_RECEIVED,    // sender got a NAK
        TIMEOUT,         // retransmission timer of a packet expired
        WINDOW_CHANGE,   // adaptive controller changed the window size
        NETWORK_CHANGE,  // user changed the network conditions
        COMPLETE         // every packet acknowledged
    }

    private final double time;
    private final Type type;
    private final int seq;
    private final String message;

    public SimEvent(double time, Type type, int seq, String message) {
        this.time = time;
        this.type = type;
        this.seq = seq;
        this.message = message;
    }

    public double getTime() {
        return time;
    }

    public Type getType() {
        return type;
    }

    /** Sequence number the event is about, or -1 if it is not about a single packet. */
    public int getSeq() {
        return seq;
    }

    public String getMessage() {
        return message;
    }
}
