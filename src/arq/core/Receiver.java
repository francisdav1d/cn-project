package arq.core;

/**
 * Receiver side of Selective Repeat ARQ.
 *
 *  - Validates every packet with its CRC-32 checksum. A corrupted packet is discarded and a NAK
 *    is sent so the sender can resend just that packet immediately.
 *  - Accepts correct packets even when earlier ones are missing and keeps them in a buffer.
 *  - Acknowledges every correct packet individually (also duplicates, because the earlier ACK
 *    may have been lost).
 *  - Delivers data to the application strictly in order once the gaps are filled.
 */
public class Receiver {

    private final SimulationContext ctx;
    private final UnreliableNetworkSimulator network;
    private final int totalPackets;
    private final boolean[] received;
    private final byte[][] buffer;          // correct packets waiting for in-order delivery
    private final byte[][] deliveredData;   // what the application received, in order
    private int expectedSeq;                // receive-window base: next packet the application needs

    public Receiver(SimulationContext ctx, UnreliableNetworkSimulator network, int totalPackets) {
        this.ctx = ctx;
        this.network = network;
        this.totalPackets = totalPackets;
        this.received = new boolean[totalPackets];
        this.buffer = new byte[totalPackets][];
        this.deliveredData = new byte[totalPackets][];
    }

    public void onPacketArrived(Packet packet) {
        int seq = packet.getSeqNo();

        if (!packet.isValid()) {
            ctx.emit(SimEvent.Type.CHECKSUM_FAIL, seq,
                    "P" + seq + " failed CRC-32 check -> discarded, NAK " + seq + " sent");
            network.sendToSender(new AckPacket(AckPacket.Kind.NAK, seq, packet.getTransmission(), packet.getSendTime()));
            return;
        }

        if (received[seq]) {
            ctx.stats.recordDuplicatePacket();
            ctx.emit(SimEvent.Type.DUPLICATE, seq,
                    "Duplicate P" + seq + " (its ACK was lost or late) -> ACK " + seq + " sent again");
        } else {
            received[seq] = true;
            buffer[seq] = packet.getPayload();
            ctx.stats.recordFirstReceipt(seq, ctx.now());
            if (seq == expectedSeq) {
                ctx.emit(SimEvent.Type.RECEIVED, seq, "P" + seq + " received correctly -> ACK " + seq);
            } else {
                ctx.emit(SimEvent.Type.BUFFERED, seq, String.format(
                        "P%d received out of order -> buffered, ACK %d (still waiting for P%d)", seq, seq, expectedSeq));
            }
            deliverInOrder();
        }
        network.sendToSender(new AckPacket(AckPacket.Kind.ACK, seq, packet.getTransmission(), packet.getSendTime()));
    }

    private void deliverInOrder() {
        int first = expectedSeq;
        while (expectedSeq < totalPackets && received[expectedSeq]) {
            deliveredData[expectedSeq] = buffer[expectedSeq];
            buffer[expectedSeq] = null;
            expectedSeq++;
        }
        int count = expectedSeq - first;
        if (count > 0) {
            ctx.stats.recordDelivered(count);
        }
        if (count > 1) {
            ctx.emit(SimEvent.Type.DELIVERED, first, String.format(
                    "Gap filled -> P%d..P%d delivered in order to the application", first, expectedSeq - 1));
        }
    }

    public boolean isReceived(int seq) {
        return received[seq];
    }

    public boolean isDelivered(int seq) {
        return seq < expectedSeq;
    }

    public int getExpectedSeq() {
        return expectedSeq;
    }

    /** Correct packets held in the buffer because an earlier packet is still missing. */
    public int getBufferedCount() {
        int count = 0;
        for (int seq = expectedSeq; seq < totalPackets; seq++) {
            if (received[seq]) {
                count++;
            }
        }
        return count;
    }

    public byte[] getDeliveredData(int seq) {
        return deliveredData[seq];
    }
}
