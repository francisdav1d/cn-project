package arq.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;

/** Colours and fonts shared by all panels. */
final class Theme {

    static final Color BACKGROUND = new Color(0xF3F4F6);
    static final Color PANEL = Color.WHITE;
    static final Color TEXT = new Color(0x111827);
    static final Color MUTED = new Color(0x6B7280);
    static final Color GRID = new Color(0xE5E7EB);
    static final Color BORDER = new Color(0xD1D5DB);

    // packet states
    static final Color NOT_SENT = new Color(0xE5E7EB);
    static final Color SENT = new Color(0x2563EB);
    static final Color RETRANSMITTED = new Color(0x7C3AED);
    static final Color LOST = new Color(0xDC2626);
    static final Color CORRUPTED = new Color(0xEA580C);
    static final Color ACKED = new Color(0x16A34A);
    static final Color BUFFERED = new Color(0xD97706);
    static final Color DELIVERED = new Color(0x16A34A);
    static final Color REJECTED_FILL = new Color(0xFEE2E2);

    // sliding window
    static final Color WINDOW_BAND = new Color(0xDBEAFE);
    static final Color WINDOW_OUTLINE = new Color(0x1D4ED8);
    static final Color WINDOW_LINE = new Color(0x2563EB);
    static final Color WINDOW_AREA = new Color(37, 99, 235, 40);
    static final Color CAPACITY_LINE = new Color(0x9CA3AF);

    // event log
    static final Color LOG_INFO = new Color(0x374151);
    static final Color LOG_WINDOW = new Color(0x1E3A8A);
    static final Color LOG_GOOD = new Color(0x15803D);
    static final Color LOG_WARN = new Color(0xB45309);

    static final Font MONO = new Font(pick("Consolas", "Menlo", "DejaVu Sans Mono", Font.MONOSPACED), Font.PLAIN, 12);

    private Theme() {
    }

    private static String pick(String... names) {
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String name : names) {
            if (Arrays.asList(available).contains(name)) {
                return name;
            }
        }
        return names[names.length - 1];
    }
}
