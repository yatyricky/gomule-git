package gomule.gui.sharedStash;

import gomule.d2i.D2Chronicle;
import randall.d2files.D2TxtFile;
import randall.d2files.D2TxtFileItemProperties;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class ChroniclePanel extends JPanel {

    private D2Chronicle chronicle;
    private final JList<RunewordEntry> runeWordsList = new JList<>();
    private final JEditorPane detailPane = new JEditorPane();
    private final JPanel centerPanel = new JPanel(new CardLayout());

    ChroniclePanel() {
        setLayout(new BorderLayout(0, 2));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        setPreferredSize(new Dimension(SharedStashPanel.BG_WIDTH, SharedStashPanel.BG_HEIGHT));

        // --- Radio buttons ---
        JRadioButton uniqueBtn = new JRadioButton("Unique");
        JRadioButton setBtn = new JRadioButton("Set");
        JRadioButton runeWordsBtn = new JRadioButton("Rune Words");
        uniqueBtn.setEnabled(false);
        setBtn.setEnabled(false);
        runeWordsBtn.setSelected(true);

        ButtonGroup group = new ButtonGroup();
        group.add(uniqueBtn);
        group.add(setBtn);
        group.add(runeWordsBtn);

        JPanel radioRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        radioRow.add(uniqueBtn);
        radioRow.add(setBtn);
        radioRow.add(runeWordsBtn);
        add(radioRow, BorderLayout.NORTH);

        // --- Runewords split panel ---
        runeWordsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        runeWordsList.setCellRenderer(new RunewordCellRenderer());
        JScrollPane listScroll = new JScrollPane(runeWordsList);
        listScroll.setBorder(BorderFactory.createTitledBorder("Collected"));

        try {
            detailPane.setContentType("text/html");
        } catch (Exception ignored) {
        }
        detailPane.setEditable(false);
        detailPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        JScrollPane detailScroll = new JScrollPane(detailPane);
        detailScroll.setBorder(BorderFactory.createTitledBorder("Details"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, detailScroll);
        splitPane.setDividerLocation(170);
        splitPane.setResizeWeight(0.35);

        JLabel placeholder = new JLabel("<html><center>Not yet implemented</center></html>", SwingConstants.CENTER);

        centerPanel.add(splitPane, "RUNEWORDS");
        centerPanel.add(placeholder, "PLACEHOLDER");
        add(centerPanel, BorderLayout.CENTER);

        // --- Listeners ---
        runeWordsBtn.addActionListener(e -> ((CardLayout) centerPanel.getLayout()).show(centerPanel, "RUNEWORDS"));
        uniqueBtn.addActionListener(e -> ((CardLayout) centerPanel.getLayout()).show(centerPanel, "PLACEHOLDER"));
        setBtn.addActionListener(e -> ((CardLayout) centerPanel.getLayout()).show(centerPanel, "PLACEHOLDER"));

        runeWordsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateDetail();
        });
    }

    void setChronicle(D2Chronicle chronicle) {
        this.chronicle = chronicle;
        rebuildRunewordList();
    }

    private void rebuildRunewordList() {
        if (chronicle == null) {
            runeWordsList.setListData(new RunewordEntry[0]);
            detailPane.setText("");
            return;
        }
        List<D2Chronicle.ChronicleEntry> entries = chronicle.getRunewordGrailEntries();
        RunewordEntry[] data = new RunewordEntry[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            D2Chronicle.ChronicleEntry e = entries.get(i);
            data[i] = new RunewordEntry(e.getItemName(), e.isFound(), i);
        }
        runeWordsList.setListData(data);
        if (data.length > 0) runeWordsList.setSelectedIndex(0);
    }

    private void updateDetail() {
        int idx = runeWordsList.getSelectedIndex();
        if (idx < 0 || chronicle == null) {
            detailPane.setText("");
            return;
        }
        D2Chronicle.ChronicleEntry entry = chronicle.getRunewordGrailEntries().get(idx);
        detailPane.setText(buildRunewordHtml(entry));
        detailPane.setCaretPosition(0);
    }

    private String buildRunewordHtml(D2Chronicle.ChronicleEntry entry) {
        String name = entry.getItemName();
        D2TxtFileItemProperties row = findRunewordRow(name);
        StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11px;padding:4px;'>");

        // Title + status
        String foundColor = entry.isFound() ? "#d4af37" : "#888888";
        sb.append("<center>");
        sb.append("<b><font color='").append(foundColor).append("'>")
                .append(esc(name != null ? name : "Unknown")).append("</font></b><br/>");
        if (entry.isFound()) {
            sb.append("<font color='#44bb44'>\u2713 Found</font>");
        } else {
            sb.append("<font color='#999999'>\u2717 Not Found</font>");
        }
        sb.append("</center><hr/>");

        if (row != null) {
            // Rune recipe
            List<String> runes = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                String rCode = row.get("Rune" + i);
                if (rCode == null || rCode.isEmpty()) break;
                runes.add(runeCodeToName(rCode));
            }
            if (!runes.isEmpty()) {
                sb.append("<b>Recipe:</b> ").append(esc(String.join(" + ", runes))).append("<br/>");
            }

            // Item types
            List<String> types = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                String itype = row.get("itype" + i);
                if (itype == null || itype.isEmpty()) break;
                types.add(resolveItemType(itype));
            }
            if (!types.isEmpty()) {
                sb.append("<b>Items:</b> ").append(esc(String.join(", ", types))).append("<br/>");
            }

            // Properties
            sb.append("<hr/><b>Properties:</b><br/>");
            boolean hasProps = false;
            for (int i = 1; i <= 7; i++) {
                String code = row.get("T1Code" + i);
                if (code == null || code.isEmpty()) continue;
                String param = row.get("T1Param" + i);
                String min = row.get("T1Min" + i);
                String max = row.get("T1Max" + i);
                sb.append("&nbsp;&bull;&nbsp;").append(esc(formatProp(code, param, min, max))).append("<br/>");
                hasProps = true;
            }
            if (!hasProps) {
                sb.append("<font color='#888'>none</font><br/>");
            }
        } else {
            sb.append("<i><font color='#888'>Runeword data not found in game files.</font></i>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private D2TxtFileItemProperties findRunewordRow(String name) {
        if (name == null) return null;
        for (int i = 0; i < D2TxtFile.RUNES.getRowSize(); i++) {
            D2TxtFileItemProperties row = D2TxtFile.RUNES.getRow(i);
            if (name.equals(row.get("*Rune Name"))) return row;
        }
        return null;
    }

    private String runeCodeToName(String code) {
        D2TxtFileItemProperties row = D2TxtFile.MISC.searchColumns("code", code);
        if (row != null) {
            String n = row.get("name");
            if (n != null && !n.isEmpty()) return n;
        }
        return code;
    }

    private String resolveItemType(String code) {
        D2TxtFileItemProperties row = D2TxtFile.ITEM_TYPES.searchColumns("Code", code);
        if (row != null) {
            String n = row.get("ItemType");
            if (n != null && !n.isEmpty()) return n;
        }
        return code;
    }

    private String formatProp(String code, String param, String min, String max) {
        StringBuilder sb = new StringBuilder(code);
        if (param != null && !param.isEmpty()) sb.append(" (").append(param).append(")");
        boolean hasMin = min != null && !min.isEmpty();
        boolean hasMax = max != null && !max.isEmpty();
        if (hasMin && hasMax) {
            if (min.equals(max)) sb.append(": ").append(min);
            else sb.append(": ").append(min).append("-").append(max);
        }
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ── Inner classes ──────────────────────────────────────────────────────────

    static class RunewordEntry {
        final String name;
        final boolean found;
        final int index;

        RunewordEntry(String name, boolean found, int index) {
            this.name = name != null ? name : "Item #" + index;
            this.found = found;
            this.index = index;
        }

        @Override
        public String toString() {
            return (found ? "\u2713 " : "\u2717 ") + name;
        }
    }

    static class RunewordCellRenderer extends DefaultListCellRenderer {
        private static final Color FOUND_COLOR = new Color(212, 175, 55);      // gold
        private static final Color NOT_FOUND_COLOR = new Color(130, 130, 130); // grey

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof RunewordEntry && !isSelected) {
                setForeground(((RunewordEntry) value).found ? FOUND_COLOR : NOT_FOUND_COLOR);
            }
            return this;
        }
    }
}
