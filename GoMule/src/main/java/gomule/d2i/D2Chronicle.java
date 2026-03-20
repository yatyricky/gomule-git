package gomule.d2i;

import randall.d2files.D2TxtFile;
import randall.d2files.D2TxtFileItemProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class D2Chronicle {

    // RoW chronicle runeword entries store an offset-encoded id in field6 low byte.
    // Most entries decode as: row = (encoded - 27) & 0xFF.
    private static final int RUNEWORD_FIELD6_OFFSET = 27;

    private final int version;
    private final int numSetItems;
    private final int numUniqueItems;
    private final int numRunewords;
    private final List<ChronicleEntry> setEntries;
    private final List<ChronicleEntry> uniqueEntries;
    private final List<ChronicleEntry> runewordEntries;

    public D2Chronicle(int version, int numSetItems, int numUniqueItems, int numRunewords,
                        List<ChronicleEntry> setEntries, List<ChronicleEntry> uniqueEntries,
                        List<ChronicleEntry> runewordEntries) {
        this.version = version;
        this.numSetItems = numSetItems;
        this.numUniqueItems = numUniqueItems;
        this.numRunewords = numRunewords;
        this.setEntries = setEntries;
        this.uniqueEntries = uniqueEntries;
        this.runewordEntries = runewordEntries;
    }

    public int getVersion() { return version; }
    public int getNumSetItems() { return numSetItems; }
    public int getNumUniqueItems() { return numUniqueItems; }
    public int getNumRunewords() { return numRunewords; }
    public List<ChronicleEntry> getSetEntries() { return setEntries; }
    public List<ChronicleEntry> getUniqueEntries() { return uniqueEntries; }
    public List<ChronicleEntry> getRunewordEntries() { return runewordEntries; }

    public int getFoundSetCount() { return (int) setEntries.stream().filter(ChronicleEntry::isFound).count(); }
    public int getFoundUniqueCount() { return (int) uniqueEntries.stream().filter(ChronicleEntry::isFound).count(); }
    public int getFoundRunewordCount() { return (int) runewordEntries.stream().filter(ChronicleEntry::isFound).count(); }
    public int getTotalFound() { return getFoundSetCount() + getFoundUniqueCount() + getFoundRunewordCount(); }
    public int getTotalItems() { return numSetItems + numUniqueItems + numRunewords; }

    /**
     * Returns grail entries for ALL chronicle-eligible unique items.
     * Items in binary slots 0..numUniqueItems-1 are found if their slot is non-zero.
     * Items beyond the tracked slot count are shown as not-found.
     */
    public List<ChronicleEntry> getUniqueGrailEntries() {
        // uniqueitems.txt uses "code" for the base item type code.
        return buildGrailEntries(D2TxtFile.UNIQUES, uniqueEntries, "disableChronicle", "code", "UniqueItem");
    }

    /**
     * Returns grail entries for ALL chronicle-eligible set items.
     * Items in binary slots 0..numSetItems-1 are found if their slot is non-zero.
     * Items beyond the tracked slot count are shown as not-found.
     */
    public List<ChronicleEntry> getSetGrailEntries() {
        // setitems.txt uses "item" for the base item type code (no "code" column).
        return buildGrailEntries(D2TxtFile.SETITEMS, setEntries, "disableChronicle", "item", "SetItem");
    }

    private List<ChronicleEntry> buildGrailEntries(D2TxtFile txtFile, List<ChronicleEntry> binarySlots,
                                                    String disableCol, String codeCol, String prefix) {
        // Build set of found item *IDs from binary chronicle entries.
        // For set/unique items, field6 = *ID of the item (the game's internal numeric identifier).
        Set<Integer> foundIds = new HashSet<>();
        for (ChronicleEntry slot : binarySlots) {
            if (slot.isFound()) {
                foundIds.add(slot.getRawField6());
            }
        }

        List<ChronicleEntry> result = new ArrayList<>();
        int ordinal = 0;
        for (int i = 0; i < txtFile.getRowSize(); i++) {
            D2TxtFileItemProperties row = txtFile.getRow(i);

            // Skip rows with no *ID or no item-type code (header/separator rows).
            String idStr = row.get("*ID");
            if (idStr == null || idStr.isEmpty()) continue;
            String code = row.get(codeCol);
            if (code == null || code.isEmpty()) continue;

            // Skip non-spawnable items.
            if (!"1".equals(row.get("spawnable"))) continue;

            // Skip chronicle-disabled items.
            if ("1".equals(row.get(disableCol))) continue;

            String name = row.get("index");
            if (name == null || name.isEmpty()) name = row.get("*ID");
            if (name == null || name.isEmpty()) name = prefix + "#" + ordinal;

            // Determine found status by matching this item's *ID against the set of found IDs.
            boolean found = false;
            try {
                found = foundIds.contains(Integer.parseInt(idStr));
            } catch (NumberFormatException ignored) {
            }

            ChronicleEntry grailEntry = new ChronicleEntry(found, 0, 0, ordinal);
            grailEntry.setItemName(name);
            result.add(grailEntry);
            ordinal++;
        }
        return result;
    }

    /**
     * Look up an item name from a txt file by its *ID column value.
     * Returns null if not found.
     */
    static String getItemNameByAstrixId(D2TxtFile txtFile, int uniqueId) {
        D2TxtFileItemProperties row = txtFile.searchColumns("*ID", String.valueOf(uniqueId));
        if (row == null) return null;
        String name = row.get("index");
        if (name == null || name.isEmpty()) name = row.get("*ID");
        return (name != null && !name.isEmpty()) ? name : null;
    }

    /**
     * Returns a list of ChronicleEntry for ALL complete runewords in runes.txt.
     * Entries that match a found runeword (by row index encoded in field6) have found=true.
     * All other complete runewords have found=false.
     * This is used to build the full Holy Grail view.
     */
    public List<ChronicleEntry> getRunewordGrailEntries() {
        // Build a set of found runes.txt row indices
        Set<Integer> foundRowIndices = new HashSet<>();
        for (ChronicleEntry entry : runewordEntries) {
            if (entry.isFound()) {
                int rowIndex = decodeRunewordRowIndex(entry.getRawField6());
                if (rowIndex >= 0) {
                    foundRowIndices.add(rowIndex);
                }
            }
        }

        List<ChronicleEntry> result = new ArrayList<>();
        for (int i = 0; i < D2TxtFile.RUNES.getRowSize(); i++) {
            D2TxtFileItemProperties row = D2TxtFile.RUNES.getRow(i);
            if (!"1".equals(row.get("complete"))) continue;

            String name = row.get("*Rune Name");
            if (name == null || name.isEmpty()) name = row.get("Name");
            if (name == null || name.isEmpty()) continue;

            boolean found = foundRowIndices.contains(i);
            ChronicleEntry grailEntry = new ChronicleEntry(found, 0, 0, i);
            grailEntry.setItemName(name);
            result.add(grailEntry);
        }
        return result;
    }

    public String toTextDump() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Chronicle / Holy Grail ===\n");
        sb.append(String.format("Version: %d\n", version));

        List<ChronicleEntry> setGrail = getSetGrailEntries();
        List<ChronicleEntry> uniqueGrail = getUniqueGrailEntries();
        List<ChronicleEntry> runeGrail = getRunewordGrailEntries();

        long setFound = setGrail.stream().filter(ChronicleEntry::isFound).count();
        long uniqueFound = uniqueGrail.stream().filter(ChronicleEntry::isFound).count();
        long runeFound = runeGrail.stream().filter(ChronicleEntry::isFound).count();
        long totalFound = setFound + uniqueFound + runeFound;
        long totalItems = setGrail.size() + uniqueGrail.size() + runeGrail.size();
        sb.append(String.format("Total Progress: %d / %d\n\n", totalFound, totalItems));

        appendSection(sb, "SET ITEMS", setGrail, setGrail.size(), (int) setFound);
        appendSection(sb, "UNIQUE ITEMS", uniqueGrail, uniqueGrail.size(), (int) uniqueFound);
        appendSection(sb, "RUNEWORDS", runeGrail, runeGrail.size(), (int) runeFound);

        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String title, List<ChronicleEntry> entries, int total, int found) {
        sb.append(String.format("--- %s (%d / %d) ---\n", title, found, total));
        for (int i = 0; i < entries.size(); i++) {
            ChronicleEntry entry = entries.get(i);
            String name = entry.getItemName() != null ? entry.getItemName() : "#" + i;
            if (entry.isFound()) {
                sb.append(String.format("  [X] %s\n", name));
            } else {
                sb.append(String.format("  [ ] %s\n", name));
            }
        }
        sb.append("\n");
    }

    public static List<String> getChronicleSetItemNames(int count) {
        return getChronicleItemNames(D2TxtFile.SETITEMS, "disableChronicle", count);
    }

    public static List<String> getChronicleUniqueItemNames(int count) {
        return getChronicleItemNames(D2TxtFile.UNIQUES, "disableChronicle", count);
    }

    public static String getSetItemNameByField6(int field6) {
        String name = tryGetChronicleItemNameByField6(D2TxtFile.SETITEMS, field6);
        return name != null ? name : "SetItem#" + (field6 & 0xFFFF);
    }

    public static String getUniqueItemNameByField6(int field6) {
        String name = tryGetChronicleItemNameByField6(D2TxtFile.UNIQUES, field6);
        return name != null ? name : "UniqueItem#" + (field6 & 0xFFFF);
    }

    /**
     * Resolves the runeword name for a chronicle entry using field6.
     * In modern RoW stashes this value is offset-encoded; older files may store
     * a direct row index. decodeRunewordRowIndex() handles both paths.
     */
    public static String getRunewordNameByField6(int field6) {
        int rowIndex = decodeRunewordRowIndex(field6);
        if (rowIndex >= 0 && rowIndex < D2TxtFile.RUNES.getRowSize()) {
            D2TxtFileItemProperties row = D2TxtFile.RUNES.getRow(rowIndex);
            String name = row.get("*Rune Name");
            if (name != null && !name.isEmpty()) return name;
            name = row.get("Name");
            if (name != null && !name.isEmpty()) return name;
        }
        return "Runeword#" + (field6 & 0xFF);
    }

    static int decodeRunewordRowIndex(int field6) {
        int encoded = field6 & 0xFF;
        int rowSize = D2TxtFile.RUNES.getRowSize();

        // Primary decode path used by modern RoW chronicles.
        int decoded = (encoded - RUNEWORD_FIELD6_OFFSET) & 0xFF;
        if (decoded < rowSize) {
            return decoded;
        }

        // Known outliers observed in live RoW stash data where ids decode
        // outside local runes.txt row bounds.
        if (encoded == 0xE7 && 176 < rowSize) {
            return 176; // Cure
        }
        if (encoded == 0x00 && 126 < rowSize) {
            return 126; // Smoke
        }

        // Compatibility fallback for older direct-index files.
        if (encoded < rowSize) {
            return encoded;
        }

        return -1;
    }

    private static List<String> getChronicleItemNames(D2TxtFile txtFile, String disableCol, int count) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < txtFile.getRowSize() && names.size() < count; i++) {
            D2TxtFileItemProperties row = txtFile.getRow(i);
            if (!"1".equals(row.get(disableCol))) {
                // In D2R txt files, the human-readable item name is often in the "index" column.
                String name = row.get("index");
                if (name == null || name.isEmpty()) {
                    name = row.get("*ID");
                }
                names.add(name != null && !name.isEmpty() ? name : "Item #" + i);
            }
        }
        return names;
    }

    static String tryGetChronicleItemNameByField6(D2TxtFile txtFile, int field6) {
        int rowSize = txtFile.getRowSize();
        int[] candidates = new int[]{field6 & 0xFFFF, field6 & 0xFF};
        for (int rowIndex : candidates) {
            if (rowIndex < 0 || rowIndex >= rowSize) {
                continue;
            }
            D2TxtFileItemProperties row = txtFile.getRow(rowIndex);
            String name = row.get("index");
            if (name == null || name.isEmpty()) {
                name = row.get("*ID");
            }
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return null;
    }

    public static class ChronicleEntry {
        private final boolean found;
        private final long rawTimestamp;
        private final int rawField0;
        private final int rawField6;
        private String itemName;

        public ChronicleEntry(boolean found, long rawTimestamp, int rawField0, int rawField6) {
            this.found = found;
            this.rawTimestamp = rawTimestamp;
            this.rawField0 = rawField0;
            this.rawField6 = rawField6;
        }

        public boolean isFound() { return found; }
        public long getRawTimestamp() { return rawTimestamp; }
        public int getRawField0() { return rawField0; }
        public int getRawField6() { return rawField6; }
        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
    }
}
