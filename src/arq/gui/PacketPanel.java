package arq.gui;

import arq.core.Receiver;
import arq.core.SimEvent;
import arq.core.Simulation;
import arq.core.SlidingWindowController;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Arrays;

/**
 * Shows every packet twice: the sender's view (top) and the receiver's view (bottom).
 * The shaded band on the sender grid is the current sliding window.
 */
public class PacketPanel extends JPanel implements Scrollable {

    private static final int CELL_W = 30;
    private static final int CELL_H = 22;
    private static final int GAP = 4;
    private static final int PAD = 14;
    private static final int LINE = 20;

    /** Last thing the network did to the current copy of a packet (shown until it is resent or ACKed). */
    private enum Mark { NONE, LOST, CORRUPTED, ACK_LOST }

    private static final Object[][] SENDER_LEGEND = {
            { Theme.NOT_SENT, "Not sent" }, { Theme.SENT, "Sent, awaiting ACK" },
            { Theme.RETRANSMITTED, "Retransmitted" }, { Theme.LOST, "Lost" },
            { Theme.CORRUPTED, "Corrupted" }, { "ACK_LOST", "ACK lost" }, { Theme.ACKED, "ACKed" },
            { "WINDOW", "Sliding window" } };
    private static final Object[][] RECEIVER_LEGEND = {
            { Theme.NOT_SENT, "Not received" }, { "REJECTED", "Rejected (CRC error)" },
            { Theme.BUFFERED, "Buffered (out of order)" }, { Theme.DELIVERED, "Delivered in order" },
            { "EXPECTED", "Next expected" } };

    private Simulation simulation;
    private Mark[] marks = new Mark[0];
    private boolean[] rejected = new boolean[0];

    public PacketPanel() {
        setBackground(Theme.PANEL);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                revalidate();
            }
        });
    }

    public void setSimulation(Simulation simulation) {
        this.simulation = simulation;
        int n = simulation.getTotalPackets();
        marks = new Mark[n];
        Arrays.fill(marks, Mark.NONE);
        rejected = new boolean[n];
        revalidate();
        repaint();
    }

    public void onEvent(SimEvent event) {
        int seq = event.getSeq();
        if (seq < 0 || seq >= marks.length) {
            return;
        }
        switch (event.getType()) {
            case SEND:
            case RETRANSMIT:
                marks[seq] = Mark.NONE;
                break;
            case LOST:
                marks[seq] = Mark.LOST;
                break;
            case CORRUPTED:
                marks[seq] = Mark.CORRUPTED;
                break;
            case ACK_LOST:
                if (marks[seq] == Mark.NONE) {
                    marks[seq] = Mark.ACK_LOST;
                }
                break;
            case CHECKSUM_FAIL:
                rejected[seq] = true;
                break;
            case RECEIVED:
            case BUFFERED:
                rejected[seq] = false;
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (simulation == null) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        SlidingWindowController window = simulation.getWindow();
        Receiver receiver = simulation.getReceiver();
        int n = simulation.getTotalPackets();
        int cols = columns(getWidth());
        int y = PAD;

        y = drawTitle(g, y, "SENDER", String.format("base = %d     next = %d     window size = %d     outstanding = %d",
                window.getBase(), window.getNextSeq(), window.getWindowSize(), window.getOutstanding()));
        y = drawLegend(g, y, SENDER_LEGEND, getWidth());
        drawSenderGrid(g, y, cols, window);
        y += gridHeight(n, cols) + 18;

        y = drawTitle(g, y, "RECEIVER", String.format("expecting = %d     buffered = %d     delivered = %d / %d",
                Math.min(receiver.getExpectedSeq(), n), receiver.getBufferedCount(), receiver.getExpectedSeq(), n));
        y = drawLegend(g, y, RECEIVER_LEGEND, getWidth());
        drawReceiverGrid(g, y, cols, receiver);
        g.dispose();
    }

    private int drawTitle(Graphics2D g, int y, String title, String info) {
        Font base = getFont();
        g.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 1));
        g.setColor(Theme.TEXT);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, PAD, y + fm.getAscent());
        int x = PAD + fm.stringWidth(title) + 16;
        g.setFont(base);
        g.setColor(Theme.MUTED);
        g.drawString(info, x, y + fm.getAscent());
        return y + LINE + 2;
    }

    /** Draws (or only measures, when g is null) a wrapping legend. Returns the y below it. */
    private int drawLegend(Graphics2D g, int y, Object[][] items, int width) {
        Font font = getFont().deriveFont(getFont().getSize2D() - 1);
        FontMetrics fm = getFontMetrics(font);
        int x = PAD;
        int swatch = 12;
        for (Object[] item : items) {
            String label = (String) item[1];
            int itemWidth = swatch + 6 + fm.stringWidth(label) + 18;
            if (x + itemWidth > width - PAD && x > PAD) {
                x = PAD;
                y += LINE - 2;
            }
            if (g != null) {
                drawSwatch(g, x, y + 3, swatch, item[0]);
                g.setFont(font);
                g.setColor(Theme.MUTED);
                g.drawString(label, x + swatch + 6, y + 2 + fm.getAscent());
            }
            x += itemWidth;
        }
        return y + LINE + 4;
    }

    private void drawSwatch(Graphics2D g, int x, int y, int size, Object kind) {
        if (kind instanceof Color) {
            g.setColor((Color) kind);
            g.fillRoundRect(x, y, size, size, 4, 4);
        } else if ("WINDOW".equals(kind)) {
            g.setColor(Theme.WINDOW_BAND);
            g.fillRoundRect(x - 1, y - 1, size + 2, size + 2, 5, 5);
            g.setColor(Theme.WINDOW_OUTLINE);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(x - 1, y - 1, size + 2, size + 2, 5, 5);
        } else if ("ACK_LOST".equals(kind)) {
            g.setColor(Theme.SENT);
            g.fillRoundRect(x, y, size, size, 4, 4);
            g.setColor(Theme.LOST);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(x, y, size, size, 4, 4);
        } else if ("REJECTED".equals(kind)) {
            g.setColor(Theme.REJECTED_FILL);
            g.fillRoundRect(x, y, size, size, 4, 4);
            g.setColor(Theme.LOST);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(x, y, size, size, 4, 4);
        } else if ("EXPECTED".equals(kind)) {
            g.setColor(Theme.NOT_SENT);
            g.fillRoundRect(x, y, size, size, 4, 4);
            g.setColor(Theme.TEXT);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(x, y, size, size, 4, 4);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void drawSenderGrid(Graphics2D g, int top, int cols, SlidingWindowController window) {
        int n = window.getTotalPackets();
        int base = window.getBase();
        int end = Math.min(n, base + window.getWindowSize());

        // window band behind the cells, then an outline around each row segment of it
        g.setColor(Theme.WINDOW_BAND);
        for (int seq = base; seq < end; seq++) {
            Rectangle r = cell(seq, cols, top);
            g.fillRect(r.x - GAP / 2 - 1, r.y - 3, r.width + GAP + 2, r.height + 6);
        }
        g.setColor(Theme.WINDOW_OUTLINE);
        g.setStroke(new BasicStroke(2f));
        int seq = base;
        while (seq < end) {
            int rowEnd = Math.min(end, (seq / cols + 1) * cols);
            Rectangle first = cell(seq, cols, top);
            Rectangle last = cell(rowEnd - 1, cols, top);
            g.drawRoundRect(first.x - GAP / 2 - 1, first.y - 3, last.x + last.width - first.x + GAP + 2, first.height + 6, 8, 8);
            seq = rowEnd;
        }
        g.setStroke(new BasicStroke(1f));

        for (int i = 0; i < n; i++) {
            SlidingWindowController.Status status = window.getStatus(i);
            Color fill;
            boolean redBorder = false;
            if (status == SlidingWindowController.Status.ACKED) {
                fill = Theme.ACKED;
            } else if (status == SlidingWindowController.Status.NOT_SENT) {
                fill = Theme.NOT_SENT;
            } else if (marks[i] == Mark.LOST) {
                fill = Theme.LOST;
            } else if (marks[i] == Mark.CORRUPTED) {
                fill = Theme.CORRUPTED;
            } else {
                fill = simulation.getSender().getTransmissionCount(i) > 1 ? Theme.RETRANSMITTED : Theme.SENT;
                redBorder = marks[i] == Mark.ACK_LOST;
            }
            drawCell(g, cell(i, cols, top), i, fill, fill == Theme.NOT_SENT ? Theme.MUTED : Color.WHITE,
                    redBorder ? Theme.LOST : null);
        }
    }

    private void drawReceiverGrid(Graphics2D g, int top, int cols, Receiver receiver) {
        int n = simulation.getTotalPackets();
        for (int i = 0; i < n; i++) {
            Color fill;
            Color text = Color.WHITE;
            Color border = null;
            if (receiver.isDelivered(i)) {
                fill = Theme.DELIVERED;
            } else if (receiver.isReceived(i)) {
                fill = Theme.BUFFERED;
            } else if (rejected[i]) {
                fill = Theme.REJECTED_FILL;
                text = Theme.LOST;
                border = Theme.LOST;
            } else {
                fill = Theme.NOT_SENT;
                text = Theme.MUTED;
            }
            if (i == receiver.getExpectedSeq()) {
                border = Theme.TEXT;
            }
            drawCell(g, cell(i, cols, top), i, fill, text, border);
        }
    }

    private void drawCell(Graphics2D g, Rectangle r, int seq, Color fill, Color text, Color border) {
        g.setColor(fill);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
        if (border != null) {
            g.setColor(border);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 6, 6);
            g.setStroke(new BasicStroke(1f));
        }
        g.setFont(getFont().deriveFont(Font.BOLD, 11f));
        FontMetrics fm = g.getFontMetrics();
        String label = String.valueOf(seq);
        g.setColor(text);
        g.drawString(label, r.x + (r.width - fm.stringWidth(label)) / 2, r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent());
    }

    // ------------------------------------------------------------------ layout

    private static int columns(int width) {
        return Math.max(5, (width - 2 * PAD + GAP) / (CELL_W + GAP));
    }

    private static int gridHeight(int n, int cols) {
        int rows = (n + cols - 1) / cols;
        return rows * (CELL_H + GAP) - GAP;
    }

    private static Rectangle cell(int seq, int cols, int top) {
        return new Rectangle(PAD + (seq % cols) * (CELL_W + GAP), top + (seq / cols) * (CELL_H + GAP), CELL_W, CELL_H);
    }

    @Override
    public Dimension getPreferredSize() {
        int width = getParent() != null ? getParent().getWidth() : 800;
        if (width <= 0) {
            width = 800;
        }
        int n = simulation == null ? 0 : simulation.getTotalPackets();
        int cols = columns(width);
        int height = PAD;
        height = (LINE + 2) + drawLegend(null, height, SENDER_LEGEND, width) + gridHeight(n, cols) + 18;
        height = (LINE + 2) + drawLegend(null, height, RECEIVER_LEGEND, width) + gridHeight(n, cols) + PAD;
        return new Dimension(width, height);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(800, 300);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 120;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
