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

    // D2R encodes field0 using an internal monster numbering that does not
    // match the d2111 monstats *hcIdx column.  Using an unrecognised value
    // (like the old constant 544) causes the game to reject the file.
    // Instead we borrow field0 from an existing found entry in the same
    // section so the value is always one the game already accepted.

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

    /**
     * If the sentinel entry in the given section has the same field6 as a new
     * entry being added to the OTHER section, relocate the sentinel's field6
     * to a safe non-colliding value.  D2R appears to reject files where an
     * entry in one section shares its field6 with another section's sentinel.
     */
    private void relocateSentinelIfCollides(List<ChronicleEntry> entries, boolean unique, int newField6) {
        int sentIdx = findSentinelIndex(entries, unique);
        if (sentIdx < 0) return;
        ChronicleEntry sentinel = entries.get(sentIdx);
        if (sentinel.getRawField6() != newField6) return;

        int safeField6 = findNonCollidingSentinelValue(newField6);
        ChronicleEntry relocated = sentinel.withField6(safeField6);
        if (relocated != null) {
            entries.set(sentIdx, relocated);
            if (unique) {
                numUniqueItems = entries.size();
            } else {
                numSetItems = entries.size();
            }
        }
    }

    /**
     * Finds a sentinel field6 value that does not collide with any valid set or
     * unique *ID, nor with any field6 already present in either section.
     * Picks a value above max valid unique *ID, below the runeword encoding base.
     */
    private int findNonCollidingSentinelValue(int avoidValue) {
        Set<Integer> avoid = new HashSet<>();
        avoid.add(avoidValue);
        // Collect all valid *IDs from both txt files.
        addValidIds(avoid, D2TxtFile.SETITEMS);
        addValidIds(avoid, D2TxtFile.UNIQUES);
        // Collect all field6 values already in use.
        for (ChronicleEntry e : setEntries) avoid.add(e.getRawField6());
        for (ChronicleEntry e : uniqueEntries) avoid.add(e.getRawField6());

        // Search in the safe zone: above max *IDs, below runeword base.
        int candidate = avoidValue + 1;
        while (avoid.contains(candidate) && candidate < RUNEWORD_FIELD6_STANDARD_BASE) {
            candidate++;
        }
        return candidate;
    }

    private static void addValidIds(Set<Integer> dest, D2TxtFile txtFile) {
        for (int i = 0; i < txtFile.getRowSize(); i++) {
            String idStr = txtFile.getRow(i).get("*ID");
            if (idStr != null && !idStr.isEmpty()) {
                try {
                    dest.add(Integer.parseInt(idStr));
                } catch (NumberFormatException ignored) {
                }
            }
        }
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

        // Already found — nothing to do.
        for (ChronicleEntry entry : binaryEntries) {
            if (entry.isFound() && entry.getRawField6() == astxId) {
                return false;
            }
        }

        // D2R appears to reject files where a sentinel entry in one section
        // shares its field6 with an entry in another section.  If the new
        // item's *ID collides with the OTHER section's sentinel, relocate
        // that sentinel before inserting the new entry.
        List<ChronicleEntry> otherEntries = unique ? setEntries : uniqueEntries;
        relocateSentinelIfCollides(otherEntries, !unique, astxId);

        // Find any found entry with rawBytes we can use as a donor.
        // withField6() patches only bytes 6-7, preserving all other game data.
        // Prefer a non-first entry so the replacement's bytes[0-5] differ from
        // entry[0]; this prevents findSentinelIndex from mistaking the newly
        // written entry for a sentinel on the next mark operation.
        int sentinelIndex = findSentinelIndex(binaryEntries, unique);
        ChronicleEntry donor = null;
        for (int i = 1; i < binaryEntries.size(); i++) {
            if (i == sentinelIndex) continue; // never use sentinel as donor
            ChronicleEntry entry = binaryEntries.get(i);
            if (entry.isFound() && entry.getRawBytes() != null) {
                donor = entry;
                break;
            }
        }
        // Fall back to the first found entry if no distinct donor exists.
        if (donor == null) {
            for (int i = 0; i < binaryEntries.size(); i++) {
                if (i == sentinelIndex) continue;
                ChronicleEntry entry = binaryEntries.get(i);
                if (entry.isFound() && entry.getRawBytes() != null) {
                    donor = entry;
                    break;
                }
            }
        }

        // If we have a donor, patch field6 to produce a valid entry.
        // Otherwise fall back to fabricating one (best effort — may not
        // round-trip perfectly, but at least populates the slot).
        ChronicleEntry foundEntry;
        if (donor != null) {
            foundEntry = donor.withField6(astxId);
        } else {
            foundEntry = new ChronicleEntry(true, currentTimestamp(), borrowField0(binaryEntries), astxId);
        }
        foundEntry.setItemName(grailEntry.getItemName());

        // Try to fill an empty slot first (but not the sentinel).
        for (int i = 0; i < binaryEntries.size(); i++) {
            if (i == sentinelIndex) continue;
            if (!binaryEntries.get(i).isFound()) {
                binaryEntries.set(i, foundEntry);
                return true;
            }
        }

        // No empty slots.  Replace the sentinel entry with the new item.
        if (sentinelIndex >= 0) {
            binaryEntries.set(sentinelIndex, foundEntry);
            return true;
        }

        // No sentinel and no empty slots in this section.
        // Grow the section by appending.  D2R itself grows set/unique
        // sections as the player discovers items, so this is safe.
        //
        // Unique section: D2R keeps a non-eligible sentinel as the LAST
        // entry (like runewords keep field6=0x0000 at the end).  New
        // entries must go BEFORE that sentinel.
        // Set section: no sentinel — just append at the end.
        if (unique) {
            // Find the sentinel at the end and insert before it.
            int sentinelAtEnd = -1;
            for (int i = binaryEntries.size() - 1; i >= 0; i--) {
                ChronicleEntry entry = binaryEntries.get(i);
                if (entry.isFound()) {
                    // Re-check if this specific entry is a sentinel
                    // (non-eligible field6).  The earlier findSentinelIndex
                    // might have returned -1 because it was used as
                    // sentinelIndex above.
                    sentinelAtEnd = i;
                    break;
                }
            }
            if (sentinelAtEnd >= 0) {
                binaryEntries.add(sentinelAtEnd, foundEntry);
            } else {
                binaryEntries.add(foundEntry);
            }
        } else {
            // Set section: no sentinel — simply append.
            binaryEntries.add(foundEntry);
        }
        if (unique) {
            numUniqueItems = binaryEntries.size();
        } else {
            numSetItems = binaryEntries.size();
        }
        return true;
    }

    /**
     * Finds the sentinel entry in a set/unique binary entry list.
     * A sentinel is any found entry whose field6 doesn't correspond to an
     * ELIGIBLE *ID (spawnable=1, disableChronicle!=1) in the respective txt
     * file.  This includes D2R's internal placeholder entries (e.g. field6=0
     * for non-spawnable items) which are invisible in the chronicle UI.
     * Scans all entries (not just the last) because the sentinel's position
     * can shift when GoMule appends entries after it.
     *
     * @return the index of the sentinel, or -1 if none found.
     */
    private static int findSentinelIndex(List<ChronicleEntry> binaryEntries, boolean unique) {
        if (binaryEntries.isEmpty()) return -1;
        // Build a set of ELIGIBLE *IDs — only spawnable, chronicle-enabled items.
        D2TxtFile txtFile = unique ? D2TxtFile.UNIQUES : D2TxtFile.SETITEMS;
        String codeCol = unique ? "code" : "item";
        Set<Integer> eligibleIds = new HashSet<>();
        for (int i = 0; i < txtFile.getRowSize(); i++) {
            D2TxtFileItemProperties row = txtFile.getRow(i);
            String idStr = row.get("*ID");
            if (idStr == null || idStr.isEmpty()) continue;
            String code = row.get(codeCol);
            if (code == null || code.isEmpty()) continue;
            if (!"1".equals(row.get("spawnable"))) continue;
            if ("1".equals(row.get("disableChronicle"))) continue;
            try {
                eligibleIds.add(Integer.parseInt(idStr));
            } catch (NumberFormatException ignored) {
            }
        }
        // Scan from the end — the sentinel is typically at or near the end.
        for (int i = binaryEntries.size() - 1; i >= 0; i--) {
            ChronicleEntry entry = binaryEntries.get(i);
            if (entry.isFound() && !eligibleIds.contains(entry.getRawField6())) {
                return i;
            }
        }
        return -1;
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
                binaryEntries.set(i, new ChronicleEntry(false, 0, 0, 0, 0, new byte[10]));
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
     * Borrows field0 from the first found entry in the list.
     * The meaning of field0 is unknown (not a monster ID as originally assumed),
     * so we reuse a value the game already wrote rather than fabricating one.
     */
    private static int borrowField0(List<ChronicleEntry> entries) {
        for (ChronicleEntry entry : entries) {
            if (entry.isFound() && entry.getRawField0() != 0) {
                return entry.getRawField0();
            }
        }
        return 0;
    }

    /**
     * Returns the current time as a chronicle-compatible timestamp.
     * Note: the actual encoding of this field is unknown — it may not
     * represent minutes-since-epoch. We still write a plausible value
     * here for fabricated entries so the game sees a non-zero value
     * (which it uses to distinguish found from not-found).
     */
    private static long currentTimestamp() {
        return System.currentTimeMillis() / 60000L;
    }

    /**
     * Attempts to look up a monster name by *hcIdx from monstats.
     * Note: field0 does NOT actually encode the monster — this method is
     * retained for diagnostic use only.  Always returns null or an unrelated
     * name when called with real chronicle field0 values.
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
        /** Original 10-byte entry from the game file, null for fabricated entries. */
        private final byte[] rawBytes;
        private String itemName;

        public ChronicleEntry(boolean found, long rawTimestamp, int rawField0, int rawField6) {
            this(found, rawTimestamp, rawField0, rawField6, 0, null);
        }

        public ChronicleEntry(boolean found, long rawTimestamp, int rawField0, int rawField6, int rawField8) {
            this(found, rawTimestamp, rawField0, rawField6, rawField8, null);
        }

        public ChronicleEntry(boolean found, long rawTimestamp, int rawField0, int rawField6, int rawField8, byte[] rawBytes) {
            this.found = found;
            this.rawTimestamp = rawTimestamp;
            this.rawField0 = rawField0;
            this.rawField6 = rawField6;
            this.rawField8 = rawField8;
            this.rawBytes = rawBytes;
        }

        public boolean isFound() { return found; }
        public long getRawTimestamp() { return rawTimestamp; }
        public int getRawField0() { return rawField0; }
        public int getRawField6() { return rawField6; }
        public int getRawField8() { return rawField8; }
        /** Returns the original 10-byte entry, or null for entries created by GoMule. */
        public byte[] getRawBytes() { return rawBytes; }
        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }

        /**
         * Creates a new entry by patching the field6 (*ID) in this entry's
         * raw bytes.  Preserves all other game-written data.
         * Returns null if this entry has no raw bytes.
         */
        public ChronicleEntry withField6(int newField6) {
            if (rawBytes == null) return null;
            byte[] patched = rawBytes.clone();
            patched[6] = (byte) (newField6 & 0xFF);
            patched[7] = (byte) ((newField6 >>> 8) & 0xFF);
            int f0 = (patched[0] & 0xFF) | ((patched[1] & 0xFF) << 8);
            long ts = (patched[2] & 0xFFL) | ((patched[3] & 0xFFL) << 8)
                    | ((patched[4] & 0xFFL) << 16) | ((patched[5] & 0xFFL) << 24);
            int f8 = (patched[8] & 0xFF) | ((patched[9] & 0xFF) << 8);
            return new ChronicleEntry(true, ts, f0, newField6, f8, patched);
        }
    }
}
