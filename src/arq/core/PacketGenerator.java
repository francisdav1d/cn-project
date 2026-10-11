package arq.core;

import java.nio.charset.StandardCharsets;

/** Creates the application data that has to be delivered reliably, one packet per sequence number. */
public class PacketGenerator {

    private final int payloadSize;

    public PacketGenerator(int payloadSize) {
        this.payloadSize = payloadSize;
    }

    public Packet[] generate(int count) {
        Packet[] packets = new Packet[count];
        for (int seq = 0; seq < count; seq++) {
            packets[seq] = new Packet(seq, makePayload(seq));
        }
        return packets;
    }

    /** e.g. "DATA#7:HHHHHHHH..." - readable, and different for every packet. */
    private byte[] makePayload(int seq) {
        StringBuilder text = new StringBuilder("DATA#").append(seq).append(':');
        char filler = (char) ('A' + seq % 26);
        while (text.length() < payloadSize) {
            text.append(filler);
        }
        return text.substring(0, payloadSize).getBytes(StandardCharsets.US_ASCII);
    }
}
