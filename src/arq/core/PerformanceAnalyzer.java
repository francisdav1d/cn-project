package arq.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the raw counters during a run and turns them into the performance metrics:
 * delivery rate, packet loss rate, retransmissions, average delay, throughput, efficiency
 * and the history of the sliding-window size.
 */
public class PerformanceAnalyzer {

    private final int totalPackets;
    private final double[] firstSendTime;

    private int transmissions;
    private int retransmissions;
    private int lostRandom;
    private int lostCongestion;
    private int corrupted;
    private int timeouts;
    private int naks;
    private int acksReceived;
    private int duplicateAcks;
    private int acksLost;
    private int duplicatePackets;
    private int uniqueReceived;
    private int delivered;
    private double delaySum;
    private double rttSum;
    private double completionTime = -1;

    private final List<double[]> windowHistory = new ArrayList<double[]>();    // {time, window}
    private final List<double[]> capacityHistory = new ArrayList<double[]>();  // {time, capacity}
    private final List<Double> failureTimes = new ArrayList<Double>();
    private int windowIncreases;
    private int windowDecreases;
    private int minWindowSeen = Integer.MAX_VALUE;
    private int maxWindowSeen;

    public PerformanceAnalyzer(int totalPackets) {
        this.totalPackets = totalPackets;
        this.firstSendTime = new double[totalPackets];
    }

    // ------------------------------------------------------------------ recording

    public void recordTransmission(int seq, boolean first, double time) {
        transmissions++;
        if (first) {
            firstSendTime[seq] = time;
        } else {
            retransmissions++;
        }
    }

    public void recordLoss(boolean congestion) {
        if (congestion) {
            lostCongestion++;
        } else {
            lostRandom++;
        }
    }

    public void recordCorruption() {
        corrupted++;
    }

    /** First correct copy of a packet reached the receiver. */
    public void recordFirstReceipt(int seq, double time) {
        uniqueReceived++;
        delaySum += time - firstSendTime[seq];
    }

    public void recordDuplicatePacket() {
        duplicatePackets++;
    }

    public void recordDelivered(int count) {
        delivered += count;
    }

    public void recordAck(double rtt) {
        acksReceived++;
        rttSum += rtt;
    }

    public void recordDuplicateAck() {
        duplicateAcks++;
    }

    public void recordAckLost() {
        acksLost++;
    }

    public void recordTimeout(double time) {
        timeouts++;
        failureTimes.add(time);
    }

    public void recordNak(double time) {
        naks++;
        failureTimes.add(time);
    }

    public void recordWindow(double time, int window) {
        if (!windowHistory.isEmpty()) {
            int previous = (int) windowHistory.get(windowHistory.size() - 1)[1];
            if (window > previous) {
                windowIncreases++;
            } else if (window < previous) {
                windowDecreases++;
            }
        }
        windowHistory.add(new double[] { time, window });
        minWindowSeen = Math.min(minWindowSeen, window);
        maxWindowSeen = Math.max(maxWindowSeen, window);
    }

    public void recordCapacity(double time, int capacity) {
        capacityHistory.add(new double[] { time, capacity });
    }

    public void recordCompletion(double time) {
        completionTime = time;
    }

    // ------------------------------------------------------------------ derived metrics

    /** Elapsed simulated time in ms (frozen at the moment the transfer completed). */
    public double getElapsed(double now) {
        return completionTime >= 0 ? completionTime : now;
    }

    /** Percentage of the packets that were delivered to the application. */
    public double getDeliveryRate() {
        return 100.0 * delivered / totalPackets;
    }

    /** Percentage of data transmissions dropped by the network (random + congestion). */
    public double getPacketLossRate() {
        return transmissions == 0 ? 0 : 100.0 * (lostRandom + lostCongestion) / transmissions;
    }

    /** Average time from a packet's first transmission until a correct copy reached the receiver. */
    public double getAverageDelay() {
        return uniqueReceived == 0 ? 0 : delaySum / uniqueReceived;
    }

    public double getAverageRtt() {
        return acksReceived == 0 ? 0 : rttSum / acksReceived;
    }

    /** Delivered packets per second of simulated time. */
    public double getThroughput(double now) {
        double seconds = getElapsed(now) / 1000.0;
        return seconds <= 0 ? 0 : delivered / seconds;
    }

    /** Useful transmissions: unique packets received correctly / all data transmissions. */
    public double getEfficiency() {
        return transmissions == 0 ? 0 : 100.0 * uniqueReceived / transmissions;
    }

    /** Time-weighted average window size. */
    public double getAverageWindow(double now) {
        if (windowHistory.isEmpty()) {
            return 0;
        }
        double end = getElapsed(now);
        if (end <= 0) {
            return windowHistory.get(windowHistory.size() - 1)[1];
        }
        double area = 0;
        for (int i = 0; i < windowHistory.size(); i++) {
            double start = windowHistory.get(i)[0];
            double stop = i + 1 < windowHistory.size() ? windowHistory.get(i + 1)[0] : end;
            area += (Math.min(stop, end) - start) * windowHistory.get(i)[1];
        }
        return area / end;
    }

    // ------------------------------------------------------------------ raw counters

    public int getTotalPackets() {
        return totalPackets;
    }

    public int getTransmissions() {
        return transmissions;
    }

    public int getRetransmissions() {
        return retransmissions;
    }

    public int getLostRandom() {
        return lostRandom;
    }

    public int getLostCongestion() {
        return lostCongestion;
    }

    public int getLostTotal() {
        return lostRandom + lostCongestion;
    }

    public int getCorrupted() {
        return corrupted;
    }

    public int getTimeouts() {
        return timeouts;
    }

    public int getNaks() {
        return naks;
    }

    public int getAcksReceived() {
        return acksReceived;
    }

    public int getDuplicateAcks() {
        return duplicateAcks;
    }

    public int getAcksLost() {
        return acksLost;
    }

    public int getDuplicatePackets() {
        return duplicatePackets;
    }

    public int getDelivered() {
        return delivered;
    }

    public int getWindowIncreases() {
        return windowIncreases;
    }

    public int getWindowDecreases() {
        return windowDecreases;
    }

    public int getMinWindowSeen() {
        return minWindowSeen == Integer.MAX_VALUE ? 0 : minWindowSeen;
    }

    public int getMaxWindowSeen() {
        return maxWindowSeen;
    }

    public List<double[]> getWindowHistory() {
        return windowHistory;
    }

    public List<double[]> getCapacityHistory() {
        return capacityHistory;
    }

    public List<Double> getFailureTimes() {
        return failureTimes;
    }
}
