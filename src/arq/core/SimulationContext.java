package arq.core;

import java.util.Random;

/** Objects shared by every component of one simulation run. */
public class SimulationContext {

    public final EventScheduler scheduler = new EventScheduler();
    public final Random random;
    public final PerformanceAnalyzer stats;
    private final SimulationListener listener;

    public SimulationContext(long seed, PerformanceAnalyzer stats, SimulationListener listener) {
        this.random = new Random(seed);
        this.stats = stats;
        this.listener = listener;
    }

    public double now() {
        return scheduler.now();
    }

    public void emit(SimEvent.Type type, int seq, String message) {
        if (listener != null) {
            listener.onEvent(new SimEvent(now(), type, seq, message));
        }
    }
}
