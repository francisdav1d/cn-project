package arq.core;

/** Everything needed to start one simulation run. */
public class SimulationConfig {

    public static final int PAYLOAD_SIZE = 64;   // bytes of data per packet

    private int numPackets = 100;
    private int initialWindow = 4;
    private int minWindow = 1;
    private int maxWindow = 32;
    private boolean adaptive = true;
    private NetworkConfig network = NetworkConfig.good();
    private long seed = System.nanoTime();

    public int getNumPackets() {
        return numPackets;
    }

    public void setNumPackets(int numPackets) {
        this.numPackets = numPackets;
    }

    public int getInitialWindow() {
        return initialWindow;
    }

    public void setInitialWindow(int initialWindow) {
        this.initialWindow = initialWindow;
    }

    public int getMinWindow() {
        return minWindow;
    }

    public void setMinWindow(int minWindow) {
        this.minWindow = minWindow;
    }

    public int getMaxWindow() {
        return maxWindow;
    }

    public void setMaxWindow(int maxWindow) {
        this.maxWindow = maxWindow;
    }

    public boolean isAdaptive() {
        return adaptive;
    }

    public void setAdaptive(boolean adaptive) {
        this.adaptive = adaptive;
    }

    public NetworkConfig getNetwork() {
        return network;
    }

    public void setNetwork(NetworkConfig network) {
        this.network = network;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }
}
