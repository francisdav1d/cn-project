package arq.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.CRC32;

/**
 * A data frame: sequence number, payload and a CRC-32 checksum computed over both.
 *
 * The sender keeps the original packet until it is acknowledged. Every (re)transmission puts a
 * copy on the network, so the network can damage that copy without affecting the original that
 * may have to be retransmitted later.
 */
public class Packet {

    private final int seqNo;
    private final byte[] payload;
    private final long checksum;
    private int transmission;   // 1 = first transmission, 2+ = retransmission number
    private double sendTime;    // simulated time at which this copy left the sender

    public Packet(int seqNo, byte[] payload) {
        this(seqNo, payload, computeChecksum(seqNo, payload));
    }

    private Packet(int seqNo, byte[] payload, long checksum) {
        this.seqNo = seqNo;
        this.payload = payload;
        this.checksum = checksum;
    }

    public static long computeChecksum(int seqNo, byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(ByteBuffer.allocate(4).putInt(seqNo).array());
        crc.update(data);
        return crc.getValue();
    }

    /** Receiver-side validation: recompute the CRC and compare it with the one carried in the header. */
    public boolean isValid() {
        return computeChecksum(seqNo, payload) == checksum;
    }

    public Packet copyForTransmission(int transmission, double sendTime) {
        Packet copy = new Packet(seqNo, payload.clone(), checksum);
        copy.transmission = transmission;
        copy.sendTime = sendTime;
        return copy;
    }

    /** Simulates a bit error on the link: flips one random bit of the payload (the checksum is unchanged). */
    public void corrupt(Random random) {
        int index = random.nextInt(payload.length);
        payload[index] ^= (byte) (1 << random.nextInt(8));
    }

    public int getSeqNo() {
        return seqNo;
    }

    public byte[] getPayload() {
        return payload;
    }

    public String getPayloadText() {
        return new String(payload, StandardCharsets.US_ASCII);
    }

    public long getChecksum() {
        return checksum;
    }

    public int getTransmission() {
        return transmission;
    }

    public double getSendTime() {
        return sendTime;
    }
}
