package gomule.d2i;

import randall.d2files.D2TxtFile;
import randall.d2files.D2TxtFileItemProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class D2Chronicle {

    // Standard RoW chronicle runeword field6 encoding:
    //   field6 = RUNEWORD_FIELD6_STANDARD_BASE + runes.txt_row_index
    // This works for the majority of entries (hi byte = 0x50).
    // Non-standard entries (Smoke, Cure, Delirium, etc.) use different bases
    // and are handled via an explicit outlier map.
    private static final int RUNEWORD_FIELD6_STANDARD_BASE = 0x501B;

    // Known non-standard field6 values observed in game save data.
    // Key = full 16-bit field6, Value = runes.txt row index.
    private static final Map<Integer, Integer> RUNEWORD_FIELD6_OUTLIERS = new HashMap<>();
    static {
        RUNEWORD_FIELD6_OUTLIERS.put(0x0000, 126); // Smoke
        RUNEWORD_FIELD6_OUTLIERS.put(0x6AE7, 176); // Cure
        RUNEWORD_FIELD6_OUTLIERS.put(0x2A9E,  21); // Delirium
    }

    // Baal (baalcrab) monstats *hcIdx — used as the drop-source field0 when
    // programmatically marking set/unique items as found.
    private static final int BAAL_FIELD0 = 544;

    private final int version;
    private int numSetItems;
    private int numUniqueItems;
    private int numRunewords;
    private boolean modified;
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
        this.modified = false;
        this.setEntries = setEntries;
        this.uniqueEntries = uniqueEntries;
        this.runewordEntries = runewordEntries;
    }

    public int getVersion() { return version; }
    public int getNumSetItems() { return numSetItems; }
    public int getNumUniqueItems() { return numUniqueItems; }
    public int getNumRunewords() { return numRunewords; }
    public boolean isModified() { return modified; }
    public List<ChronicleEntry> getSetEntries() { return setEntries; }
    public List<ChronicleEntry> getUniqueEntries() { return uniqueEntries; }
    public List<ChronicleEntry> getRunewordEntries() { return runewordEntries; }

    public int getFoundSetCount() { return (int) setEntries.stream().filter(ChronicleEntry::isFound).count(); }
    public int getFoundUniqueCount() { return (int) uniqueEntries.stream().filter(ChronicleEntry::isFound).count(); }
    public int getFoundRunewordCount() { return (int) runewordEntries.stream().filter(ChronicleEntry::isFound).count(); }
    public int getTotalFound() { return getFoundSetCount() + getFoundUniqueCount() + getFoundRunewordCount(); }
    public int getTotalItems() { return numSetItems + numUniqueItems + numRunewords; }

    public enum Section {
        UNIQUE,
        SET,
        RUNEWORDS
    }

    public boolean markFound(Section section, int grailIndex) {
        boolean changed;
        switch (section) {
            case UNIQUE:
                changed = markSetOrUniqueFound(getUniqueGrailEntries(), uniqueEntries, grailIndex, true);
                break;
            case SET:
                changed = markSetOrUniqueFound(getSetGrailEntries(), setEntries, grailIndex, false);
                break;
            case RUNEWORDS:
            default:
                changed = markRunewordFound(grailIndex);
                break;
        }
        if (changed) {
            modified = true;
        }
        return changed;
    }

    public boolean markNotFound(Section section, int grailIndex) {
        boolean changed;
        switch (section) {
            case UNIQUE:
                changed = markSetOrUniqueNotFound(getUniqueGrailEntries(), uniqueEntries, grailIndex, true);
                break;
            case SET:
                changed = markSetOrUniqueNotFound(getSetGrailEntries(), setEntries, grailIndex, false);
                break;
            case RUNEWORDS:
            default:
                changed = markRunewordNotFound(grailIndex);
                break;
        }
        if (changed) {
            modified = true;
        }
        return changed;
    }

    public boolean toggleFound(Section section, int grailIndex) {
        List<ChronicleEntry> entries;
        switch (section) {
            case UNIQUE:
                entries = getUniqueGrailEntries();
                break;
            case SET:
                entries = getSetGrailEntries();
                break;
            case RUNEWORDS:
            default:
                entries = getRunewordGrailEntries();
                break;
        }
        if (grailIndex < 0 || grailIndex >= entries.size()) {
            return false;
        }
        return entries.get(grailIndex).isFound() ? markNotFound(section, grailIndex) : markFound(section, grailIndex);
    }

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
        // Build a map from *ID to the matching binary entry (for found items).
        Map<Integer, ChronicleEntry> foundById = new HashMap<>();
        for (ChronicleEntry slot : binarySlots) {
            if (slot.isFound()) {
                foundById.put(slot.getRawField6(), slot);
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

            // Determine found status by matching this item's *ID against the found map.
            int id = 0;
            try {
                id = Integer.parseInt(idStr);
            } catch (NumberFormatException ignored) {
            }

            ChronicleEntry binaryEntry = foundById.get(id);
            ChronicleEntry grailEntry;
            if (binaryEntry != null) {
                grailEntry = new ChronicleEntry(true, binaryEntry.getRawTimestamp(), binaryEntry.getRawField0(), id);
            } else {
                grailEntry = new ChronicleEntry(false, 0, 0, id);
            }
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
        // Build a map from runes.txt row index to the matching binary entry
        Map<Integer, ChronicleEntry> foundByRow = new HashMap<>();
        for (ChronicleEntry entry : runewordEntries) {
            if (entry.isFound()) {
                int rowIndex = decodeRunewordRowIndex(entry.getRawField6());
                if (rowIndex >= 0) {
                    foundByRow.put(rowIndex, entry);
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

            ChronicleEntry binaryEntry = foundByRow.get(i);
            ChronicleEntry grailEntry;
            if (binaryEntry != null) {
                grailEntry = new ChronicleEntry(true, binaryEntry.getRawTimestamp(), 0, i);
            } else {
                grailEntry = new ChronicleEntry(false, 0, 0, i);
            }
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
        int full = field6 & 0xFFFF;
        int rowSize = D2TxtFile.RUNES.getRowSize();

        // 1. Check known non-standard field6 values first.
        Integer outlier = RUNEWORD_FIELD6_OUTLIERS.get(full);
        if (outlier != null) {
            return outlier < rowSize ? outlier : -1;
        }

        // 2. Standard decode: field6 = 0x501B + row_index.
        int row = full - RUNEWORD_FIELD6_STANDARD_BASE;
        if (row >= 0 && row < rowSize) {
            return row;
        }

        // 3. Legacy fallback: low byte - 27 (for older files).
        int encoded = full & 0xFF;
        int decoded = (encoded - 27) & 0xFF;
        if (decoded < rowSize) {
            return decoded;
        }

        // 4. Direct index fallback.
        if (encoded < rowSize) {
            return encoded;
        }

        return -1;
    }

    private boolean markRunewordFound(int grailIndex) {
        List<ChronicleEntry> grail = getRunewordGrailEntries();
        if (grailIndex < 0 || grailIndex >= grail.size()) {
            return false;
        }

        ChronicleEntry grailEntry = grail.get(grailIndex);
        int rowIndex = grailEntry.getRawField6();
        for (ChronicleEntry entry : runewordEntries) {
            if (!entry.isFound()) {
                continue;
            }
            if (decodeRunewordRowIndex(entry.getRawField6()) == rowIndex) {
                return false;
            }
        }

        // Field6 = standard base + row index (produces 0x50xx values).
        // Field0 bytes[0-1] is always 0x0000 in real game files.
        // Timestamp bytes[2-5] must be non-zero for the game to recognise the entry as found.
        int newField6 = RUNEWORD_FIELD6_STANDARD_BASE + rowIndex;
        int newField0 = 0;
        long timestamp = currentTimestamp();
        int newField8 = 0;

        ChronicleEntry newEntry = new ChronicleEntry(true, timestamp, newField0, newField6, newField8);
        newEntry.setItemName(grailEntry.getItemName());

        // The game keeps a sentinel entry (field6=0x0000) at the end of the
        // runeword list.  New entries must be inserted BEFORE that sentinel,
        // otherwise the game stops reading before it reaches the new entry.
        int insertPos = runewordEntries.size();
        for (int i = runewordEntries.size() - 1; i >= 0; i--) {
            if (runewordEntries.get(i).getRawField6() == 0x0000) {
                insertPos = i;
                break;
            }
        }
        runewordEntries.add(insertPos, newEntry);
        numRunewords = runewordEntries.size();
        return true;
    }

    private boolean markRunewordNotFound(int grailIndex) {
        List<ChronicleEntry> grail = getRunewordGrailEntries();
        if (grailIndex < 0 || grailIndex >= grail.size()) {
            return false;
        }

        int rowIndex = grail.get(grailIndex).getRawField6();
        for (int i = 0; i < runewordEntries.size(); i++) {
            ChronicleEntry entry = runewordEntries.get(i);
            if (!entry.isFound()) {
                continue;
            }
            if (decodeRunewordRowIndex(entry.getRawField6()) == rowIndex) {
                runewordEntries.remove(i);
                numRunewords = runewordEntries.size();
                return true;
            }
        }
        return false;
    }

    private boolean markSetOrUniqueFound(List<ChronicleEntry> grailEntries,
                                         List<ChronicleEntry> binaryEntries,
                                         int grailIndex,
                                         boolean unique) {
        if (grailIndex < 0 || grailIndex >= grailEntries.size()) {
            return false;
        }

        ChronicleEntry grailEntry = grailEntries.get(grailIndex);
        int astxId = grailEntry.getRawField6();

        for (ChronicleEntry entry : binaryEntries) {
            if (entry.isFound() && entry.getRawField6() == astxId) {
                return false;
            }
        }

        long timestamp = currentTimestamp();

        ChronicleEntry foundEntry = new ChronicleEntry(true, timestamp, BAAL_FIELD0, astxId);
        foundEntry.setItemName(grailEntry.getItemName());

        for (int i = 0; i < binaryEntries.size(); i++) {
            if (!binaryEntries.get(i).isFound()) {
                binaryEntries.set(i, foundEntry);
                return true;
            }
        }

        binaryEntries.add(foundEntry);
        if (unique) {
            numUniqueItems = binaryEntries.size();
        } else {
            numSetItems = binaryEntries.size();
        }
        return true;
    }

    private boolean markSetOrUniqueNotFound(List<ChronicleEntry> grailEntries,
                                            List<ChronicleEntry> binaryEntries,
                                            int grailIndex,
                                            boolean unique) {
        if (grailIndex < 0 || grailIndex >= grailEntries.size()) {
            return false;
        }

        int astxId = grailEntries.get(grailIndex).getRawField6();
        for (int i = 0; i < binaryEntries.size(); i++) {
            ChronicleEntry entry = binaryEntries.get(i);
            if (entry.isFound() && entry.getRawField6() == astxId) {
                binaryEntries.set(i, new ChronicleEntry(false, 0, 0, 0));
                if (unique) {
                    numUniqueItems = binaryEntries.size();
                } else {
                    numSetItems = binaryEntries.size();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the current time as a chronicle-compatible timestamp.
     * The game stores timestamps as minutes since the Unix epoch.
     */
    private static long currentTimestamp() {
        return System.currentTimeMillis() / 60000L;
    }

    /**
     * Look up a monster name by its *hcIdx value (stored in chronicle field0
     * for set/unique items).  Falls back to row-index lookup if *hcIdx search
     * fails.  Returns null if no match is found.
     */
    public static String getMonsterNameByHcIdx(int hcIdx) {
        if (hcIdx <= 0) return null;
        D2TxtFileItemProperties row = D2TxtFile.MONSTATS.searchColumns("*hcIdx", String.valueOf(hcIdx));
        if (row == null && hcIdx < D2TxtFile.MONSTATS.getRowSize()) {
            row = D2TxtFile.MONSTATS.getRow(hcIdx);
        }
        if (row == null) return null;
        String name = row.get("NameStr");
        if (name != null && !name.isEmpty()) return name;
        name = row.get("Id");
        return (name != null && !name.isEmpty()) ? name : null;
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
        private final int rawField8;
        private String itemName;

        public ChronicleEntry(boolean found, long rawTimestamp, int rawField0, int rawField6) {
            this(found, rawTimestamp, rawField0, rawField6, 0);
        }

        public ChronicleEntry(boolean found, long rawTimestamp, int rawField0, int rawField6, int rawField8) {
            this.found = found;
            this.rawTimestamp = rawTimestamp;
            this.rawField0 = rawField0;
            this.rawField6 = rawField6;
            this.rawField8 = rawField8;
        }

        public boolean isFound() { return found; }
        public long getRawTimestamp() { return rawTimestamp; }
        public int getRawField0() { return rawField0; }
        public int getRawField6() { return rawField6; }
        public int getRawField8() { return rawField8; }
        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
    }
}
