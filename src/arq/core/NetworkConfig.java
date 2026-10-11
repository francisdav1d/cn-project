package arq.core;

/**
 * Settings of the unreliable network. The three presets are the good, moderate and poor
 * environments used in the demonstration; every value can also be set by hand.
 */
public class NetworkConfig {

    private String name;
    private double lossProbability;        // 0..1, chance that a frame is randomly dropped
    private double corruptionProbability;  // 0..1, chance that a data frame gets a bit error
    private int minDelayMs;                // propagation delay is uniform in [min, max]
    private int maxDelayMs;
    private double congestionLevel;        // 0..1, how strongly the network suffers when overloaded
    private int capacity;                  // frames the network can carry before queues build up
    private boolean ackLossEnabled;        // whether ACK/NAK frames can be randomly lost as well

    public NetworkConfig(String name, double lossProbability, double corruptionProbability,
                         int minDelayMs, int maxDelayMs, double congestionLevel, int capacity,
                         boolean ackLossEnabled) {
        this.name = name;
        this.lossProbability = lossProbability;
        this.corruptionProbability = corruptionProbability;
        this.minDelayMs = minDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.congestionLevel = congestionLevel;
        this.capacity = capacity;
        this.ackLossEnabled = ackLossEnabled;
    }

    public static NetworkConfig good() {
        return new NetworkConfig("Good", 0.02, 0.01, 40, 120, 0.3, 16, true);
    }

    public static NetworkConfig moderate() {
        return new NetworkConfig("Moderate", 0.06, 0.03, 80, 300, 0.6, 10, true);
    }

    public static NetworkConfig poor() {
        return new NetworkConfig("Poor", 0.15, 0.06, 150, 600, 0.9, 6, true);
    }

    /** Returns the preset with the given name, or null for an unknown name. */
    public static NetworkConfig preset(String name) {
        if ("Good".equalsIgnoreCase(name)) {
            return good();
        }
        if ("Moderate".equalsIgnoreCase(name)) {
            return moderate();
        }
        if ("Poor".equalsIgnoreCase(name)) {
            return poor();
        }
        return null;
    }

    public NetworkConfig copy() {
        return new NetworkConfig(name, lossProbability, corruptionProbability, minDelayMs, maxDelayMs,
                congestionLevel, capacity, ackLossEnabled);
    }

    public String describe() {
        return String.format("%s (loss %.0f%%, corruption %.0f%%, delay %d-%d ms, congestion %.0f%%, capacity %d)",
                name, lossProbability * 100, corruptionProbability * 100, minDelayMs, maxDelayMs,
                congestionLevel * 100, capacity);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLossProbability() {
        return lossProbability;
    }

    public void setLossProbability(double lossProbability) {
        this.lossProbability = lossProbability;
    }

    public double getCorruptionProbability() {
        return corruptionProbability;
    }

    public void setCorruptionProbability(double corruptionProbability) {
        this.corruptionProbability = corruptionProbability;
    }

    public int getMinDelayMs() {
        return minDelayMs;
    }

    public void setMinDelayMs(int minDelayMs) {
        this.minDelayMs = minDelayMs;
    }

    public int getMaxDelayMs() {
        return maxDelayMs;
    }

    public void setMaxDelayMs(int maxDelayMs) {
        this.maxDelayMs = maxDelayMs;
    }

    public double getCongestionLevel() {
        return congestionLevel;
    }

    public void setCongestionLevel(double congestionLevel) {
        this.congestionLevel = congestionLevel;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public boolean isAckLossEnabled() {
        return ackLossEnabled;
    }

    public void setAckLossEnabled(boolean ackLossEnabled) {
        this.ackLossEnabled = ackLossEnabled;
    }
}
