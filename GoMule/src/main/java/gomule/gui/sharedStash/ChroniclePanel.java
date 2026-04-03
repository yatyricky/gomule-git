package gomule.gui.sharedStash;

import gomule.D2Files;
import gomule.d2i.D2Chronicle;
import gomule.translations.Translations;
import randall.d2files.D2TxtFile;
import randall.d2files.D2TxtFileItemProperties;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class ChroniclePanel extends JPanel {

    private final Runnable onChronicleChanged;
    private D2Chronicle chronicle;
    private final JList<ChronicleDisplayEntry> entryList = new JList<>();
    private final JEditorPane detailPane = new JEditorPane();
    private final JRadioButton uniqueBtn = new JRadioButton("Unique");
    private final JRadioButton setBtn = new JRadioButton("Set");
    private final JRadioButton runeWordsBtn = new JRadioButton("Rune Words");
    private final JTextField filterField = new JTextField();
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

        JPanel filterRow = new JPanel(new BorderLayout(4, 0));
        filterRow.add(new JLabel("Filter:"), BorderLayout.WEST);
        filterRow.add(filterField, BorderLayout.CENTER);

        JPanel topPanel = new JPanel(new BorderLayout(0, 2));
        topPanel.add(radioRow, BorderLayout.NORTH);
        topPanel.add(filterRow, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 2));
        toggleFoundBtn.setEnabled(false);
        actionRow.add(toggleFoundBtn);
        JButton bulkMarkBtn = new JButton("Bulk Mark...");
        bulkMarkBtn.addActionListener(e -> showBulkMarkDialog());
        actionRow.add(bulkMarkBtn);
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

        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { rebuildEntryList(); }
            @Override public void removeUpdate(DocumentEvent e) { rebuildEntryList(); }
            @Override public void changedUpdate(DocumentEvent e) { rebuildEntryList(); }
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
        filterField.setText("");
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
        String filterText = filterField.getText().trim().toLowerCase();
        List<ChronicleDisplayEntry> filtered = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            D2Chronicle.ChronicleEntry e = entries.get(i);
            String name = e.getItemName();
            String displayName = translateDisplayName(name);
            if (!filterText.isEmpty() && (displayName == null || !displayName.toLowerCase().contains(filterText))) {
                continue;
            }
            filtered.add(new ChronicleDisplayEntry(displayName, e.isFound(), i));
        }
        if (selectedMode == ChronicleMode.UNIQUE || selectedMode == ChronicleMode.SET) {
            filtered.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name));
        }
        ChronicleDisplayEntry[] data = filtered.toArray(new ChronicleDisplayEntry[0]);
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
        int grailIndex = ((ChronicleDisplayEntry) entryList.getModel().getElementAt(idx)).index;
        D2Chronicle.ChronicleEntry entry = getSelectedEntries().get(grailIndex);
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
        ChronicleDisplayEntry displayEntry = (ChronicleDisplayEntry) entryList.getModel().getElementAt(selectedIndex);
        int grailIndex = displayEntry.index;
        boolean wasFound = displayEntry.found;
        boolean changed = chronicle.toggleFound(toSection(selectedMode), grailIndex);
        if (!changed) {
            if (!wasFound) {
                JOptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this),
                        "Unable to mark this item as found.\n"
                                + "It may already be marked, or an internal error occurred.",
                        "Cannot Mark as Found",
                        JOptionPane.WARNING_MESSAGE);
            }
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
        if (chronicle == null || selectedIndex < 0 || selectedIndex >= entryList.getModel().getSize()) {
            toggleFoundBtn.setEnabled(false);
            toggleFoundBtn.setText("Mark as Found");
            return;
        }
        toggleFoundBtn.setEnabled(true);
        boolean isFound = ((ChronicleDisplayEntry) entryList.getModel().getElementAt(selectedIndex)).found;
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
        String name = translateDisplayName(entry.getItemName());
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
        int itemId = entry.getRawField6() & 0xFFFF;
        sb.append("<b>Chronicle Id:</b> ").append(itemId).append("<br/>");

        // Show txt file *ID and properties for unique/set items
        boolean isUnique = "Unique Item".equals(typeName);
        D2TxtFile txtFile = isUnique ? D2TxtFile.UNIQUES : D2TxtFile.SETITEMS;
        D2TxtFileItemProperties row = txtFile.searchColumns("*ID", String.valueOf(itemId));
        if (row != null) {
            sb.append("<b>").append(isUnique ? "UniqueItems" : "SetItems").append(" *ID:</b> ").append(itemId).append("<br/>");

            // Show properties
            sb.append("<hr/><b>Properties:</b><br/>");
            boolean hasProps = false;
            for (int i = 1; i <= 12; i++) {
                String code = row.get("prop" + i);
                if (code == null || code.isEmpty()) continue;
                String param = row.get("par" + i);
                String min = row.get("min" + i);
                String max = row.get("max" + i);
                sb.append("&nbsp;&bull;&nbsp;").append(esc(formatProp(code, param, min, max))).append("<br/>");
                hasProps = true;
            }
            if (!hasProps) {
                sb.append("<font color='#888'>none</font><br/>");
            }
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private String buildRunewordHtml(D2Chronicle.ChronicleEntry entry) {
        String rawName = entry.getItemName();
        String name = translateDisplayName(rawName);
        D2TxtFileItemProperties row = findRunewordRow(rawName);
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

    private static String translateDisplayName(String name) {
        if (name == null) return null;
        try {
            Translations translations = D2Files.getInstance().getTranslations();
            String enUS = translations.getTranslationOrNull(name);
            if (enUS != null && !enUS.equals(name)) {
                return enUS + " (" + name + ")";
            }
        } catch (Exception ignored) {
        }
        return name;
    }

    private void showBulkMarkDialog() {
        if (chronicle == null) return;

        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner instanceof Frame ? (Frame) owner : null,
                "Bulk Mark as Found", true);
        dialog.setLayout(new BorderLayout(4, 4));
        dialog.setSize(420, 340);
        dialog.setLocationRelativeTo(this);

        JTextArea textArea = new JTextArea();
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Enter enUS item names (one per line)"));
        dialog.add(scroll, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        JButton markBtn = new JButton("Mark all as found");
        JButton cancelBtn = new JButton("Cancel");
        btnPanel.add(markBtn);
        btnPanel.add(cancelBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        cancelBtn.addActionListener(e -> dialog.dispose());
        markBtn.addActionListener(e -> {
            executeBulkMark(textArea.getText(), dialog);
        });

        dialog.setVisible(true);
    }

    private void executeBulkMark(String text, JDialog dialog) {
        if (chronicle == null) return;

        Translations translations;
        try {
            translations = D2Files.getInstance().getTranslations();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(dialog, "Translations not available.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        D2Chronicle.Section section = toSection(selectedMode);
        List<D2Chronicle.ChronicleEntry> grailEntries = getSelectedEntries();

        List<String> ambiguous = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        List<String> marked = new ArrayList<>();
        List<String> capacityFull = new ArrayList<>();

        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String enUS = line.trim();
            if (enUS.isEmpty()) continue;

            // Reverse lookup: enUS → Key(s)
            List<String> keys = translations.getKeysForEnUS(enUS);

            // Also check if the input itself is a Key directly
            if (keys.isEmpty()) {
                String directCheck = translations.getTranslationOrNull(enUS);
                if (directCheck != null) {
                    keys = Collections.singletonList(enUS);
                }
            }

            if (keys.isEmpty()) {
                notFound.add(enUS);
                continue;
            }

            // Filter keys to only those present in the current grail section
            List<Integer> matchingIndices = new ArrayList<>();
            List<String> matchingKeys = new ArrayList<>();
            for (String key : keys) {
                for (int i = 0; i < grailEntries.size(); i++) {
                    if (key.equals(grailEntries.get(i).getItemName())) {
                        matchingIndices.add(i);
                        matchingKeys.add(key);
                    }
                }
            }

            if (matchingIndices.isEmpty()) {
                notFound.add(enUS);
                continue;
            }
            if (matchingIndices.size() > 1) {
                ambiguous.add(enUS);
                continue;
            }

            int grailIndex = matchingIndices.get(0);
            if (grailEntries.get(grailIndex).isFound()) {
                continue;
            }
            boolean ok = chronicle.markFound(section, grailIndex);
            if (ok) {
                marked.add(enUS);
            } else {
                capacityFull.add(enUS);
            }
        }

        // Rebuild UI
        if (!marked.isEmpty()) {
            rebuildEntryList();
            if (onChronicleChanged != null) {
                onChronicleChanged.run();
            }
        }

        // Build summary
        StringBuilder msg = new StringBuilder();
        if (!marked.isEmpty()) {
            msg.append("Marked as found (").append(marked.size()).append("):\n");
            marked.forEach(n -> msg.append("  \u2713 ").append(n).append("\n"));
        }
        if (!capacityFull.isEmpty()) {
            if (msg.length() > 0) msg.append("\n");
            msg.append("Section full — no slots available (").append(capacityFull.size()).append("):\n");
            capacityFull.forEach(n -> msg.append("  \u2717 ").append(n).append("\n"));
        }
        if (!ambiguous.isEmpty()) {
            if (msg.length() > 0) msg.append("\n");
            msg.append("Can't batch process (multiple matches) (").append(ambiguous.size()).append("):\n");
            ambiguous.forEach(n -> msg.append("  \u2717 ").append(n).append("\n"));
        }
        if (!notFound.isEmpty()) {
            if (msg.length() > 0) msg.append("\n");
            msg.append("Not found in current section (").append(notFound.size()).append("):\n");
            notFound.forEach(n -> msg.append("  \u2717 ").append(n).append("\n"));
        }

        dialog.dispose();
        if (msg.length() > 0) {
            JTextArea resultArea = new JTextArea(msg.toString());
            resultArea.setEditable(false);
            resultArea.setRows(Math.min(20, msg.toString().split("\n").length + 1));
            resultArea.setColumns(40);
            JScrollPane resultScroll = new JScrollPane(resultArea);
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    resultScroll, "Bulk Mark Results", JOptionPane.INFORMATION_MESSAGE);
        }
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
