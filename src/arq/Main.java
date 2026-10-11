package arq;

import arq.gui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.Arrays;

/**
 * Entry point.
 *
 *   java -cp out arq.Main                      -> opens the Swing simulator
 *   java -cp out arq.Main --experiment [n] [r] -> runs the headless comparison (n packets, r runs)
 */
public class Main {

    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equalsIgnoreCase("--experiment") || args[0].equalsIgnoreCase("--cli"))) {
            ExperimentRunner.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // fall back to the default look and feel
            }
            new MainFrame().setVisible(true);
        });
    }
}
