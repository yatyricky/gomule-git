package gomule.gui.sharedStash;

import gomule.d2i.D2Chronicle;
import randall.d2files.D2TxtFile;
import randall.d2files.D2TxtFileItemProperties;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class ChroniclePanel extends JPanel {

    private final Runnable onChronicleChanged;
    private D2Chronicle chronicle;
    private final JList<ChronicleDisplayEntry> entryList = new JList<>();
    private final JEditorPane detailPane = new JEditorPane();
    private final JRadioButton uniqueBtn = new JRadioButton("Unique");
    private final JRadioButton setBtn = new JRadioButton("Set");
    private final JRadioButton runeWordsBtn = new JRadioButton("Rune Words");
    private final JButton toggleFoundBtn = new JButton("Mark as Found");
    private ChronicleMode selectedMode = ChronicleMode.RUNEWORDS;

    ChroniclePanel(Runnable onChronicleChanged) {
        this.onChronicleChanged = onChronicleChanged;
        setLayout(new BorderLayout(0, 2));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        setPreferredSize(new Dimension(SharedStashPanel.BG_WIDTH, SharedStashPanel.BG_HEIGHT));

        // --- Radio buttons ---
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

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 2));
        toggleFoundBtn.setEnabled(false);
        actionRow.add(toggleFoundBtn);
        add(actionRow, BorderLayout.SOUTH);

        // --- Chronicle split panel ---
        entryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        entryList.setCellRenderer(new ChronicleCellRenderer());
        JScrollPane listScroll = new JScrollPane(entryList);
        listScroll.setBorder(BorderFactory.createTitledBorder("Items"));

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
        add(splitPane, BorderLayout.CENTER);

        // --- Listeners ---
        runeWordsBtn.addActionListener(e -> switchMode(ChronicleMode.RUNEWORDS));
        uniqueBtn.addActionListener(e -> switchMode(ChronicleMode.UNIQUE));
        setBtn.addActionListener(e -> switchMode(ChronicleMode.SET));
        toggleFoundBtn.addActionListener(e -> toggleSelectedFoundState());

        entryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateDetail();
        });
    }

    void setChronicle(D2Chronicle chronicle) {
        this.chronicle = chronicle;
        uniqueBtn.setEnabled(chronicle != null && !chronicle.getUniqueEntries().isEmpty());
        setBtn.setEnabled(chronicle != null && !chronicle.getSetEntries().isEmpty());
        runeWordsBtn.setEnabled(chronicle != null);
        toggleFoundBtn.setEnabled(chronicle != null);

        if (selectedMode == ChronicleMode.UNIQUE && !uniqueBtn.isEnabled()) {
            selectedMode = setBtn.isEnabled() ? ChronicleMode.SET : ChronicleMode.RUNEWORDS;
        } else if (selectedMode == ChronicleMode.SET && !setBtn.isEnabled()) {
            selectedMode = uniqueBtn.isEnabled() ? ChronicleMode.UNIQUE : ChronicleMode.RUNEWORDS;
        }

        selectCurrentModeButton();
        rebuildEntryList();
    }

    private void switchMode(ChronicleMode mode) {
        selectedMode = mode;
        rebuildEntryList();
    }

    private void selectCurrentModeButton() {
        switch (selectedMode) {
            case UNIQUE:
                uniqueBtn.setSelected(true);
                break;
            case SET:
                setBtn.setSelected(true);
                break;
            case RUNEWORDS:
            default:
                runeWordsBtn.setSelected(true);
                break;
        }
    }

    private void rebuildEntryList() {
        if (chronicle == null) {
            entryList.setListData(new ChronicleDisplayEntry[0]);
            detailPane.setText("");
            return;
        }

        List<D2Chronicle.ChronicleEntry> entries = getSelectedEntries();
        ChronicleDisplayEntry[] data = new ChronicleDisplayEntry[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            D2Chronicle.ChronicleEntry e = entries.get(i);
            data[i] = new ChronicleDisplayEntry(e.getItemName(), e.isFound(), i);
        }
        entryList.setListData(data);
        if (data.length > 0) {
            entryList.setSelectedIndex(0);
        } else {
            detailPane.setText("");
        }
        updateToggleFoundButtonState();
    }

    private void updateDetail() {
        int idx = entryList.getSelectedIndex();
        if (idx < 0 || chronicle == null) {
            detailPane.setText("");
            updateToggleFoundButtonState();
            return;
        }
        D2Chronicle.ChronicleEntry entry = getSelectedEntries().get(idx);
        detailPane.setText(buildDetailHtml(entry));
        detailPane.setCaretPosition(0);
        updateToggleFoundButtonState();
    }

    private void toggleSelectedFoundState() {
        if (chronicle == null) {
            return;
        }
        int selectedIndex = entryList.getSelectedIndex();
        if (selectedIndex < 0) {
            return;
        }
        boolean changed = chronicle.toggleFound(toSection(selectedMode), selectedIndex);
        if (!changed) {
            return;
        }
        rebuildEntryList();
        if (selectedIndex < entryList.getModel().getSize()) {
            entryList.setSelectedIndex(selectedIndex);
        }
        if (onChronicleChanged != null) {
            onChronicleChanged.run();
        }
    }

    private D2Chronicle.Section toSection(ChronicleMode mode) {
        switch (mode) {
            case UNIQUE:
                return D2Chronicle.Section.UNIQUE;
            case SET:
                return D2Chronicle.Section.SET;
            case RUNEWORDS:
            default:
                return D2Chronicle.Section.RUNEWORDS;
        }
    }

    private void updateToggleFoundButtonState() {
        int selectedIndex = entryList.getSelectedIndex();
        if (chronicle == null || selectedIndex < 0 || selectedIndex >= getSelectedEntries().size()) {
            toggleFoundBtn.setEnabled(false);
            toggleFoundBtn.setText("Mark as Found");
            return;
        }
        toggleFoundBtn.setEnabled(true);
        boolean isFound = getSelectedEntries().get(selectedIndex).isFound();
        toggleFoundBtn.setText(isFound ? "Mark as Not Found" : "Mark as Found");
    }

    private List<D2Chronicle.ChronicleEntry> getSelectedEntries() {
        switch (selectedMode) {
            case UNIQUE:
                return chronicle.getUniqueGrailEntries();
            case SET:
                return chronicle.getSetGrailEntries();
            case RUNEWORDS:
            default:
                return chronicle.getRunewordGrailEntries();
        }
    }

    private String buildDetailHtml(D2Chronicle.ChronicleEntry entry) {
        if (selectedMode == ChronicleMode.RUNEWORDS) {
            return buildRunewordHtml(entry);
        }
        return buildGenericChronicleHtml(entry, selectedMode == ChronicleMode.UNIQUE ? "Unique Item" : "Set Item");
    }

    private String buildGenericChronicleHtml(D2Chronicle.ChronicleEntry entry, String typeName) {
        String name = entry.getItemName();
        String foundColor = entry.isFound() ? "#d4af37" : "#888888";
        StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11px;padding:4px;'>");

        sb.append("<center>");
        sb.append("<b><font color='").append(foundColor).append("'>")
                .append(esc(name != null ? name : typeName)).append("</font></b><br/>");
        sb.append("<font color='#666666'>").append(typeName).append("</font><br/>");
        if (entry.isFound()) {
            sb.append("<font color='#44bb44'>\u2713 Found</font>");
        } else {
            sb.append("<font color='#999999'>\u2717 Not Found</font>");
        }
        sb.append("</center><hr/>");

        sb.append("<b>Name:</b> ").append(esc(name != null ? name : "Unknown")).append("<br/>");
        sb.append("<b>Chronicle Id:</b> ").append(entry.getRawField6() & 0xFFFF).append("<br/>");
        if (entry.isFound() && entry.getRawTimestamp() != 0) {
            sb.append("<b>Found Time:</b> ").append(formatTimestamp(entry.getRawTimestamp())).append("<br/>");
        }
        if (entry.isFound() && entry.getRawField0() != 0) {
            String monsterName = D2Chronicle.getMonsterNameByHcIdx(entry.getRawField0());
            sb.append("<b>Dropped By:</b> ").append(esc(monsterName != null ? monsterName : "Unknown (ID: " + entry.getRawField0() + ")")).append("<br/>");
        }

        sb.append("</body></html>");
        return sb.toString();
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

        if (entry.isFound() && entry.getRawTimestamp() != 0) {
            sb.append("<b>Found Time:</b> ").append(formatTimestamp(entry.getRawTimestamp())).append("<br/>");
        }

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

    private static String formatTimestamp(long ts) {
        if (ts <= 0) return String.valueOf(ts);
        // Timestamps are minutes since the Unix epoch.
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
        return sdf.format(new java.util.Date(ts * 60L * 1000L));
    }

    // ── Inner classes ──────────────────────────────────────────────────────────

    private enum ChronicleMode {
        UNIQUE,
        SET,
        RUNEWORDS
    }

    static class ChronicleDisplayEntry {
        final String name;
        final boolean found;
        final int index;

        ChronicleDisplayEntry(String name, boolean found, int index) {
            this.name = name != null ? name : "Item #" + index;
            this.found = found;
            this.index = index;
        }

        @Override
        public String toString() {
            return (found ? "\u2713 " : "\u2717 ") + name;
        }
    }

    static class ChronicleCellRenderer extends DefaultListCellRenderer {
        private static final Color FOUND_COLOR = new Color(212, 175, 55);      // gold
        private static final Color NOT_FOUND_COLOR = new Color(130, 130, 130); // grey

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ChronicleDisplayEntry && !isSelected) {
                setForeground(((ChronicleDisplayEntry) value).found ? FOUND_COLOR : NOT_FOUND_COLOR);
            }
            return this;
        }
    }
}
