package arq.core;

/** Control frame sent from the receiver back to the sender. */
public class AckPacket {

    public enum Kind {
        ACK,   // packet received correctly
        NAK    // packet arrived corrupted - please resend it now
    }

    private final Kind kind;
    private final int seqNo;
    private final int transmission;     // which copy of the packet this frame answers
    private final double echoSendTime;  // send time of that copy, lets the sender measure the RTT

    public AckPacket(Kind kind, int seqNo, int transmission, double echoSendTime) {
        this.kind = kind;
        this.seqNo = seqNo;
        this.transmission = transmission;
        this.echoSendTime = echoSendTime;
    }

    public Kind getKind() {
        return kind;
    }

    public int getSeqNo() {
        return seqNo;
    }

    public int getTransmission() {
        return transmission;
    }

    public double getEchoSendTime() {
        return echoSendTime;
    }
}
