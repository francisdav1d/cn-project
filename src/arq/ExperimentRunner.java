package arq;

import arq.core.NetworkConfig;
import arq.core.PerformanceAnalyzer;
import arq.core.Simulation;
import arq.core.SimulationConfig;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Locale;

/**
 * Headless experiment: runs the protocol many times under good, moderate and poor network
 * conditions, with two fixed windows and with the adaptive window, and prints averaged metrics.
 * Every mode uses the same random seeds, so the comparison is fair.
 *
 * Usage:  java -cp out arq.Main --experiment [packets] [runs]
 */
public class ExperimentRunner {

    private static final String[] MODES = { "Fixed window = 4", "Fixed window = 16", "Adaptive window" };
    private static final double TIME_LIMIT_MS = 3_600_000;   // one simulated hour, just a safety net
    private static final String CSV_FILE = "experiment_results.csv";

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        int packets = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int runs = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        NetworkConfig[] networks = { NetworkConfig.good(), NetworkConfig.moderate(), NetworkConfig.poor() };

        System.out.printf("Adaptive Selective Repeat ARQ - experiment: %d packets, average of %d runs per row%n%n",
                packets, runs);
        String format = "%-9s %-18s %9s %7s %8s %10s %11s %11s %8s %8s %10s%n";
        System.out.printf(format, "Network", "Mode", "Delivered", "Loss %", "Retrans.", "Delay(ms)",
                "Thru(pkt/s)", "Efficiency", "Avg win", "Time(s)", "Integrity");
        System.out.println(repeat('-', 118));

        StringBuilder csv = new StringBuilder("network,mode,delivery_rate_pct,packet_loss_rate_pct,retransmissions,"
                + "avg_delay_ms,throughput_pkt_per_s,efficiency_pct,avg_window,completion_time_s,integrity_ok_runs\n");

        for (NetworkConfig network : networks) {
            for (int mode = 0; mode < MODES.length; mode++) {
                double delivery = 0, loss = 0, retrans = 0, delay = 0, throughput = 0, efficiency = 0, window = 0, time = 0;
                int intact = 0;
                for (int run = 0; run < runs; run++) {
                    SimulationConfig config = new SimulationConfig();
                    config.setNumPackets(packets);
                    config.setNetwork(network.copy());
                    config.setSeed(1000 + run);
                    config.setAdaptive(mode == 2);
                    config.setInitialWindow(mode == 1 ? 16 : 4);
                    config.setMinWindow(1);
                    config.setMaxWindow(32);

                    Simulation sim = new Simulation(config, null);
                    sim.runToCompletion(TIME_LIMIT_MS);
                    PerformanceAnalyzer s = sim.getStats();
                    double now = sim.now();
                    delivery += s.getDeliveryRate();
                    loss += s.getPacketLossRate();
                    retrans += s.getRetransmissions();
                    delay += s.getAverageDelay();
                    throughput += s.getThroughput(now);
                    efficiency += s.getEfficiency();
                    window += s.getAverageWindow(now);
                    time += s.getElapsed(now) / 1000.0;
                    if (sim.isComplete() && sim.verifyIntegrity()) {
                        intact++;
                    }
                }
                System.out.printf(format, network.getName(), MODES[mode],
                        String.format("%.1f%%", delivery / runs), String.format("%.1f", loss / runs),
                        String.format("%.1f", retrans / runs), String.format("%.0f", delay / runs),
                        String.format("%.1f", throughput / runs), String.format("%.1f%%", efficiency / runs),
                        String.format("%.1f", window / runs), String.format("%.1f", time / runs),
                        intact + "/" + runs);
                csv.append(String.format("%s,%s,%.2f,%.2f,%.2f,%.1f,%.2f,%.2f,%.2f,%.2f,%d%n",
                        network.getName(), MODES[mode], delivery / runs, loss / runs, retrans / runs, delay / runs,
                        throughput / runs, efficiency / runs, window / runs, time / runs, intact));
            }
            System.out.println();
        }

        try (Writer out = new FileWriter(CSV_FILE)) {
            out.write(csv.toString());
            System.out.println("Results also written to " + CSV_FILE);
        } catch (IOException e) {
            System.out.println("Could not write " + CSV_FILE + ": " + e.getMessage());
        }
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
