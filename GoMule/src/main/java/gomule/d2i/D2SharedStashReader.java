package gomule.d2i;

import com.google.common.io.BaseEncoding;
import gomule.d2i.D2SharedStash.D2SharedStashPane;
import gomule.d2i.D2Chronicle.ChronicleEntry;
import gomule.item.D2Item;
import gomule.model.VersionController;
import gomule.model.VersionController.Variant;
import gomule.util.D2BitReader;
import randall.d2files.D2TxtFile;

import java.util.ArrayList;
import java.util.List;

import static gomule.model.VersionController.Version.D2R3;

public class D2SharedStashReader {

    static final byte[] STASH_HEADER_START = BaseEncoding.base16().decode("55AA55AA");

    public D2SharedStash readStash(Variant expectedVariant, String filename) throws Exception {
        return readStash(expectedVariant, filename, new D2BitReader(filename));
    }

    public D2SharedStash readStash(Variant expectedVariant, String filename, D2BitReader bitReader) throws Exception {
        List<D2SharedStashPane> result = new ArrayList<>();
        int[] stashHeaderOffsets = getStashHeaderOffsets(expectedVariant, bitReader);
        for (int i = 0; i < expectedVariant.getSharedStashConfig().getItemStashPaneCount(); i++) {
            bitReader.set_byte_pos(stashHeaderOffsets[i]);
            result.add(readSharedStashPane(bitReader, filename));
        }
        D2Chronicle chronicle = null;
        int totalPanes = expectedVariant.getSharedStashConfig().getTotalStashPaneCount();
        if (totalPanes > expectedVariant.getSharedStashConfig().getItemStashPaneCount()) {
            for (int i = expectedVariant.getSharedStashConfig().getItemStashPaneCount(); i < totalPanes; i++) {
                chronicle = tryReadChroniclePane(bitReader, stashHeaderOffsets[i]);
                if (chronicle != null) break;
            }
        }
        return new D2SharedStash(expectedVariant, filename, result, bitReader.getFileContent(), chronicle);
    }

    public static int[] getStashHeaderOffsets(Variant expectedVariant, D2BitReader bitReader) {
        int[] stashHeaderOffsets = bitReader.findBytes(STASH_HEADER_START);
        Variant variantOrNull = Variant.tryParseSharedStashPaneCount(stashHeaderOffsets.length);
        if (variantOrNull != expectedVariant)
            throw VersionController.VersionException.forVariant(expectedVariant, variantOrNull);
        return stashHeaderOffsets;
    }

    private D2SharedStashPane readSharedStashPane(D2BitReader bitReader, String filename) throws Exception {
        int stashPaneStart = bitReader.get_byte_pos();
        D2SharedStash.Header header = D2SharedStash.Header.fromBytes(bitReader);
        if (header.getVersion() != D2R3.getFileVersionIdentifier())
            throw VersionController.VersionException.forVersion(D2R3, VersionController.Version.tryParseFileVersionIdentifier((int) header.getVersion()));
        bitReader.set_byte_pos(bitReader.findNextFlag("JM", bitReader.get_byte_pos()));
        bitReader.skipBytes(2);
        int numItems = (int) bitReader.read(16);
        List<D2Item> result = new ArrayList<>();
        for (int i = 0; i < numItems; i++) {
            int itemStartBitPos = bitReader.get_pos();
            D2Item parsedItem = parseItemWithResync(bitReader, filename, itemStartBitPos);
            if ("'s Ear".equals(parsedItem.getItemName())) {
                continue;
            }
            result.add(parsedItem);
        }
        int calculatedLength = bitReader.get_byte_pos() - stashPaneStart;
        if (calculatedLength != header.getLength()) {
            if (calculatedLength < header.getLength()) {
                // Some modern/shared stash variants can include trailing pane data that isn't item-encoded.
                bitReader.set_byte_pos((int) (stashPaneStart + header.getLength()));
            } else {
                throw new RuntimeException("Incorrect shared stash length: " + calculatedLength + " expected: " + header.getLength());
            }
        }
        return D2SharedStashPane.fromItems(result, header.getGold());
    }

    private D2Item parseItemWithResync(D2BitReader bitReader, String filename, int itemStartBitPos) throws Exception {
        int[] deltas = new int[]{0, -24, -16, -8, 8, 16, 24, -32, 32};
        Exception lastException = null;

        for (int delta : deltas) {
            int candidateStart = itemStartBitPos + delta;
            if (candidateStart < 0) {
                continue;
            }

            bitReader.set_pos(candidateStart);
            try {
                return new D2Item(filename, bitReader, 75);
            } catch (Exception primary) {
                lastException = primary;
            }

            bitReader.set_pos(candidateStart);
            try {
                return new D2Item(filename, bitReader, 75, false);
            } catch (Exception fallback) {
                lastException = fallback;
            }
        }

        bitReader.set_pos(itemStartBitPos);
        if (lastException != null) {
            throw lastException;
        }
        throw new RuntimeException("Failed to parse shared stash item at bit " + itemStartBitPos);
    }

    static final byte[] CHRONICLE_MAGIC = BaseEncoding.base16().decode("C0EDEAC0");

    private D2Chronicle tryReadChroniclePane(D2BitReader bitReader, int paneOffset) {
        try {
            bitReader.set_byte_pos(paneOffset);
            // Skip 64-byte standard pane header
            bitReader.skipBytes(64);

            // Check for C0EDEAC0 magic
            byte[] magic = bitReader.get_bytes(4);
            if (magic[0] != (byte) 0xC0 || magic[1] != (byte) 0xED || magic[2] != (byte) 0xEA || magic[3] != (byte) 0xC0) {
                return null;
            }
            bitReader.set_byte_pos(bitReader.get_byte_pos() + 4);

            // Read chronicle header
            int version = readU16LE(bitReader);
            int numSetItems = readU16LE(bitReader);
            int numUniqueItems = readU16LE(bitReader);
            int numRunewords = readU16LE(bitReader);

            // Detect entry format by peeking at bytes at pane offset +84
            // (i.e. after the 8-byte minimum padding zone at [76..83]).
            // New-format files (RoW patch): 8-byte padding, *ID in field0 (bytes 0-1).
            // Old-format files: 12-byte padding, *ID in field6 (bytes 6-7).
            // In old format, bytes [84..87] are always zero (part of 12-byte padding).
            // In new format, bytes [84..87] are either the first entry or the start
            // of the trailer — both typically non-zero.
            // This check works even when totalEntries == 0: an empty new-format file
            // has trailer data at offset 84, while an empty old-format file has
            // zero-padding at offset 84.
            int afterCountsPos = bitReader.get_byte_pos();
            boolean newEntryFormat = false;
            bitReader.set_byte_pos(afterCountsPos + 8);
            byte[] peek = bitReader.get_bytes(4);
            if (peek[0] != 0 || peek[1] != 0 || peek[2] != 0 || peek[3] != 0) {
                newEntryFormat = true;
            }
            bitReader.set_byte_pos(afterCountsPos);

            // Skip padding: 8 bytes for new format, 12 for old
            bitReader.skipBytes(newEntryFormat ? 8 : 12);

            // Read set item entries
            List<ChronicleEntry> setEntries = readChronicleEntries(bitReader, numSetItems);
            List<ChronicleEntry> uniqueEntries = readChronicleEntries(bitReader, numUniqueItems);
            List<ChronicleEntry> runewordEntries = readChronicleEntries(bitReader, numRunewords);

            // Set/unique entries: *ID is in field0 for new format, field6 for old.
            for (ChronicleEntry entry : setEntries) {
                if (entry.isFound()) {
                    int idForLookup = newEntryFormat ? entry.getRawField0() : entry.getRawField6();
                    String name = D2Chronicle.getItemNameByAstrixId(D2TxtFile.SETITEMS, idForLookup);
                    entry.setItemName(name != null ? name : "SetItem#" + idForLookup);
                }
            }
            for (ChronicleEntry entry : uniqueEntries) {
                if (entry.isFound()) {
                    int idForLookup = newEntryFormat ? entry.getRawField0() : entry.getRawField6();
                    String name = D2Chronicle.getItemNameByAstrixId(D2TxtFile.UNIQUES, idForLookup);
                    entry.setItemName(name != null ? name : "UniqueItem#" + idForLookup);
                }
            }
            // Runewords: field6 low byte is offset-encoded in modern RoW files.
            for (ChronicleEntry entry : runewordEntries) {
                entry.setItemName(D2Chronicle.getRunewordNameByField6(entry.getRawField6()));
            }

            return new D2Chronicle(version, numSetItems, numUniqueItems, numRunewords,
                    setEntries, uniqueEntries, runewordEntries, newEntryFormat);
        } catch (Exception e) {
            return null;
        }
    }

    private List<ChronicleEntry> readChronicleEntries(D2BitReader bitReader, int count) {
        List<ChronicleEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            byte[] entryBytes = bitReader.get_bytes(10);
            int pos = bitReader.get_byte_pos();
            bitReader.set_byte_pos(pos + 10);

            boolean allZero = true;
            for (byte b : entryBytes) {
                if (b != 0) { allZero = false; break; }
            }

            if (allZero) {
                entries.add(new ChronicleEntry(false, 0, 0, 0, 0, new byte[10]));
            } else {
                int field0 = (entryBytes[0] & 0xFF) | ((entryBytes[1] & 0xFF) << 8);
                long timestamp = (entryBytes[2] & 0xFFL) | ((entryBytes[3] & 0xFFL) << 8)
                        | ((entryBytes[4] & 0xFFL) << 16) | ((entryBytes[5] & 0xFFL) << 24);
                int field6 = (entryBytes[6] & 0xFF) | ((entryBytes[7] & 0xFF) << 8);
                int field8 = (entryBytes[8] & 0xFF) | ((entryBytes[9] & 0xFF) << 8);
                entries.add(new ChronicleEntry(true, timestamp, field0, field6, field8, entryBytes.clone()));
            }
        }
        return entries;
    }

    private int readU16LE(D2BitReader bitReader) {
        byte[] bytes = bitReader.get_bytes(2);
        int pos = bitReader.get_byte_pos();
        bitReader.set_byte_pos(pos + 2);
        return (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8);
    }
}
