package arq.core;

/** Receives every event the simulation produces (used by the GUI's event log and packet view). */
public interface SimulationListener {

    void onEvent(SimEvent event);
}
