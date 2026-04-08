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
    /**
     * True for newer D2R game files (RoW patch) where the item *ID is stored in
     * field0 (bytes 0-1 of each 10-byte entry) rather than field6 (bytes 6-7).
     * These files also use 8-byte padding before entries instead of 12 bytes.
     */
    private final boolean newEntryFormat;

    public D2Chronicle(int version, int numSetItems, int numUniqueItems, int numRunewords,
                        List<ChronicleEntry> setEntries, List<ChronicleEntry> uniqueEntries,
                        List<ChronicleEntry> runewordEntries, boolean newEntryFormat) {
        this.version = version;
        this.numSetItems = numSetItems;
        this.numUniqueItems = numUniqueItems;
        this.numRunewords = numRunewords;
        this.modified = false;
        this.setEntries = setEntries;
        this.uniqueEntries = uniqueEntries;
        this.runewordEntries = runewordEntries;
        this.newEntryFormat = newEntryFormat;
    }

    public int getVersion() { return version; }
    /** Returns true if this chronicle uses the newer entry format (field0=*ID, 8-byte padding). */
    public boolean isNewEntryFormat() { return newEntryFormat; }
    /**
     * Returns the byte offset of the entry area relative to the chronicle pane start.
     * 88 for old format (12-byte padding), 84 for new format (8-byte padding).
     */
    public int getEntryAreaStart() { return newEntryFormat ? 84 : 88; }
    /**
     * Returns the *ID of a set/unique chronicle entry.
     * New format stores *ID in field0 (bytes 0-1); old format stores it in field6 (bytes 6-7).
     */
    private int getEntryId(ChronicleEntry entry) {
        return newEntryFormat ? entry.getRawField0() : entry.getRawField6();
    }
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
                foundById.put(getEntryId(slot), slot);
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
        // Field0 bytes[0-1] is always 0x0000 in real game runeword entries.
        int newField6 = RUNEWORD_FIELD6_STANDARD_BASE + rowIndex;

        // Use a donor entry from any section to preserve game metadata
        // (bytes 2-3 must be 0x0000, field8 must match game constant).
        ChronicleEntry donor = findAnyDonorEntry();
        ChronicleEntry newEntry = fabricateEntry(0, newField6, donor);
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
            if (entry.isFound() && getEntryId(entry) == astxId) {
                return false;
            }
        }

        // D2R appears to reject files where a sentinel entry in one section
        // shares its field6 with an entry in another section.  Only applies
        // to old-format files that have sentinels.
        if (!newEntryFormat) {
            List<ChronicleEntry> otherEntries = unique ? setEntries : uniqueEntries;
            relocateSentinelIfCollides(otherEntries, !unique, astxId);
        }

        // Find any found entry with rawBytes we can use as a donor.
        // withField0/withField6 patches only the *ID bytes, preserving all
        // other game data.
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

        // If we have a donor, patch the *ID field to produce a valid entry.
        // Otherwise fall back to fabricating one with correct binary layout.
        ChronicleEntry foundEntry;
        if (donor != null) {
            foundEntry = newEntryFormat ? donor.withField0(astxId) : donor.withField6(astxId);
        } else {
            foundEntry = null;
        }
        if (foundEntry == null) {
            // No same-section donor; try cross-section donor for correct game metadata.
            ChronicleEntry anyDonor = findAnyDonorEntry();
            if (newEntryFormat) {
                foundEntry = fabricateEntry(astxId, 0, anyDonor);
            } else {
                foundEntry = fabricateEntry(borrowField0(binaryEntries), astxId, anyDonor);
            }
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

        // No empty slots.  Insert before the sentinel to preserve it.
        // D2R uses field6=0 (and non-eligible field6 values) as end-of-list
        // markers.  Overwriting the sentinel causes the game to reject the
        // file.  Instead, grow the section by inserting the new entry just
        // before the sentinel.
        if (sentinelIndex >= 0) {
            binaryEntries.add(sentinelIndex, foundEntry);
        } else {
            // No sentinel and no empty slots — just append.
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
    private int findSentinelIndex(List<ChronicleEntry> binaryEntries, boolean unique) {
        if (binaryEntries.isEmpty()) return -1;
        // New-format files have no sentinel entries in any section.
        // In new format, field6 holds opaque game data (not the *ID), so
        // the eligibility check below would incorrectly mark every entry as a
        // sentinel.  Return -1 to disable sentinel logic entirely.
        if (newEntryFormat) return -1;
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
        // f6=0 is treated as a sentinel regardless of eligibility: D2R uses
        // field6=0 as the end-of-list marker for set/unique sections, even
        // though *ID=0 (Civerb's Ward) is technically a spawnable set item.
        // Appending after a f6=0 entry causes D2R to stop reading and reject
        // the file due to entry-count mismatch.
        for (int i = binaryEntries.size() - 1; i >= 0; i--) {
            ChronicleEntry entry = binaryEntries.get(i);
            if (entry.isFound() && (entry.getRawField6() == 0 || !eligibleIds.contains(entry.getRawField6()))) {
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
            if (entry.isFound() && getEntryId(entry) == astxId) {
                if (newEntryFormat) {
                    // New format: remove the entry (count = number of found entries)
                    binaryEntries.remove(i);
                } else {
                    // Old format: zero out the slot (pre-allocated empty slot)
                    binaryEntries.set(i, new ChronicleEntry(false, 0, 0, 0, 0, new byte[10]));
                }
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
     * Finds any found entry with rawBytes across all chronicle sections.
     * Used as a donor for fabricating new entries with correct game metadata
     * (bytes 2-3 = 0, field8 = game constant, etc.).
     */
    private ChronicleEntry findAnyDonorEntry() {
        for (List<ChronicleEntry> section : new List[]{setEntries, uniqueEntries, runewordEntries}) {
            for (ChronicleEntry entry : section) {
                if (entry.isFound() && entry.getRawBytes() != null) {
                    return entry;
                }
            }
        }
        return null;
    }

    /**
     * Fabricates a chronicle entry with the correct 5×u16LE binary layout.
     * <p>
     * Each 10-byte entry consists of five u16LE fields:
     * <pre>
     *   bytes 0-1: field0 (*ID for set/unique in new format, 0 for runewords)
     *   bytes 2-3: field2 (always 0x0000 in game-written entries)
     *   bytes 4-5: field4 (game-internal value, non-zero for found entries)
     *   bytes 6-7: field6 (game metadata for set/unique, runeword encoding for RW)
     *   bytes 8-9: field8 (game-internal constant, 0x01C3 observed in all entries)
     * </pre>
     * If a donor entry is available, its bytes 2-5 and 8-9 are copied to
     * preserve game metadata.  Otherwise a best-effort fabrication is used.
     */
    private ChronicleEntry fabricateEntry(int field0, int field6, ChronicleEntry donor) {
        byte[] raw = new byte[10];
        // field0 (bytes 0-1)
        raw[0] = (byte) (field0 & 0xFF);
        raw[1] = (byte) ((field0 >>> 8) & 0xFF);
        if (donor != null && donor.getRawBytes() != null) {
            byte[] donorRaw = donor.getRawBytes();
            // Copy bytes 2-5 (field2 + field4) and 8-9 (field8) from donor
            System.arraycopy(donorRaw, 2, raw, 2, 4);
            raw[8] = donorRaw[8];
            raw[9] = donorRaw[9];
        } else {
            // bytes 2-3 must be zero; bytes 4-5 need a non-zero value
            // Use a simple counter derived from current time
            int field4 = (int) ((System.currentTimeMillis() / 1000L) & 0xFFFF);
            if (field4 == 0) field4 = 1;
            raw[4] = (byte) (field4 & 0xFF);
            raw[5] = (byte) ((field4 >>> 8) & 0xFF);
            // field8 = 0x01C3 (observed constant in all game entries)
            raw[8] = (byte) 0xC3;
            raw[9] = (byte) 0x01;
        }
        // field6 (bytes 6-7)
        raw[6] = (byte) (field6 & 0xFF);
        raw[7] = (byte) ((field6 >>> 8) & 0xFF);

        long ts = (raw[2] & 0xFFL) | ((raw[3] & 0xFFL) << 8)
                | ((raw[4] & 0xFFL) << 16) | ((raw[5] & 0xFFL) << 24);
        int f8 = (raw[8] & 0xFF) | ((raw[9] & 0xFF) << 8);
        return new ChronicleEntry(true, ts, field0, field6, f8, raw);
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

        /**
         * Creates a new entry by patching the field0 (*ID) in this entry's
         * raw bytes.  Used for new-format files where *ID is stored in bytes 0-1.
         * Preserves all other game-written data (bytes 2-9).
         * Returns null if this entry has no raw bytes.
         */
        public ChronicleEntry withField0(int newField0) {
            if (rawBytes == null) return null;
            byte[] patched = rawBytes.clone();
            patched[0] = (byte) (newField0 & 0xFF);
            patched[1] = (byte) ((newField0 >>> 8) & 0xFF);
            long ts = (patched[2] & 0xFFL) | ((patched[3] & 0xFFL) << 8)
                    | ((patched[4] & 0xFFL) << 16) | ((patched[5] & 0xFFL) << 24);
            int f6 = (patched[6] & 0xFF) | ((patched[7] & 0xFF) << 8);
            int f8 = (patched[8] & 0xFF) | ((patched[9] & 0xFF) << 8);
            return new ChronicleEntry(true, ts, newField0, f6, f8, patched);
        }
    }
}
