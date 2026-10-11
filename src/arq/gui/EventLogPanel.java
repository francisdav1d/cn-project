package arq.gui;

import arq.core.SimEvent;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

/** Colour-coded, time-stamped log of everything that happens during the simulation. */
public class EventLogPanel extends JPanel {

    private static final int MAX_ENTRIES = 5000;

    private final DefaultListModel<SimEvent> model = new DefaultListModel<SimEvent>();
    private final JList<SimEvent> list = new JList<SimEvent>(model);
    private final JCheckBox autoScroll = new JCheckBox("Auto-scroll", true);
    private boolean dirty;

    public EventLogPanel() {
        super(new BorderLayout());
        setBackground(Theme.PANEL);
        setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        JLabel title = new JLabel("Event log");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 1));
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.PANEL);
        header.setBorder(BorderFactory.createEmptyBorder(6, 10, 4, 6));
        header.add(title, BorderLayout.WEST);
        autoScroll.setBackground(Theme.PANEL);
        header.add(autoScroll, BorderLayout.EAST);

        list.setFont(Theme.MONO);
        list.setFixedCellHeight(18);
        list.setCellRenderer(new EventRenderer());
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.GRID));

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    public void add(SimEvent event) {
        model.addElement(event);
        if (model.size() > MAX_ENTRIES) {
            model.removeRange(0, model.size() - MAX_ENTRIES - 1);
        }
        dirty = true;
    }

    /** Scrolls to the newest entry once per GUI refresh (not once per event). */
    public void flush() {
        if (dirty && autoScroll.isSelected() && !model.isEmpty()) {
            list.ensureIndexIsVisible(model.size() - 1);
        }
        dirty = false;
    }

    public void clear() {
        model.clear();
    }

    private static String tag(SimEvent.Type type) {
        switch (type) {
            case SEND: return "SEND";
            case RETRANSMIT: return "RETRANSMIT";
            case LOST: return "LOST";
            case CORRUPTED: return "CORRUPTED";
            case RECEIVED: return "RECEIVED";
            case BUFFERED: return "BUFFERED";
            case DUPLICATE: return "DUPLICATE";
            case CHECKSUM_FAIL: return "CRC ERROR";
            case DELIVERED: return "DELIVERED";
            case ACK_LOST: return "ACK LOST";
            case ACK_RECEIVED: return "ACK";
            case NAK_RECEIVED: return "NAK";
            case TIMEOUT: return "TIMEOUT";
            case WINDOW_CHANGE: return "WINDOW";
            case NETWORK_CHANGE: return "NETWORK";
            case COMPLETE: return "DONE";
            default: return "INFO";
        }
    }

    private static Color color(SimEvent.Type type) {
        switch (type) {
            case SEND: return Theme.SENT;
            case RETRANSMIT: return Theme.RETRANSMITTED;
            case LOST:
            case ACK_LOST:
            case TIMEOUT: return Theme.LOST;
            case CORRUPTED:
            case CHECKSUM_FAIL:
            case NAK_RECEIVED: return Theme.CORRUPTED;
            case RECEIVED:
            case DELIVERED:
            case ACK_RECEIVED:
            case COMPLETE: return Theme.LOG_GOOD;
            case BUFFERED:
            case DUPLICATE: return Theme.LOG_WARN;
            case WINDOW_CHANGE: return Theme.LOG_WINDOW;
            default: return Theme.LOG_INFO;
        }
    }

    private static final class EventRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            SimEvent e = (SimEvent) value;
            String text = String.format("%9.3f s  %-11s %s", e.getTime() / 1000.0, tag(e.getType()), e.getMessage());
            super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            if (!isSelected) {
                setForeground(color(e.getType()));
            }
            boolean highlight = e.getType() == SimEvent.Type.WINDOW_CHANGE || e.getType() == SimEvent.Type.NETWORK_CHANGE
                    || e.getType() == SimEvent.Type.COMPLETE || e.getType() == SimEvent.Type.INFO;
            setFont(highlight ? Theme.MONO.deriveFont(Font.BOLD) : Theme.MONO);
            return this;
        }
    }
}
