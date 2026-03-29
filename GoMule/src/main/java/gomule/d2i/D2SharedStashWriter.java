package gomule.d2i;

import gomule.item.D2Item;
import gomule.model.VersionController;
import gomule.model.VersionController.Variant;
import gomule.util.D2BitReader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static gomule.d2i.D2SharedStashReader.getStashHeaderOffsets;

public class D2SharedStashWriter {
    private final Variant variant;
    private final File file;
    private final byte[] originalContent;

    // Pane-0 upgrade bytes used by the native stash format when switching JM version 1 -> 2.
    // Migration is triggered from actual chronicle growth in this file, not from fixed runeword counts.
    private static final byte[] PANE0_VERSION_UPGRADE_BLOCK = {
            0x10, 0x08, (byte) 0x80, 0x04, 0x05, 0x20, 0x54, 0x0F, 0x35, (byte) 0xA2,
            (byte) 0xCE, (byte) 0xF0, 0x30, (byte) 0xCA, (byte) 0xC0, 0x1D, 0x28, 0x4D,
            (byte) 0x90, (byte) 0xC9, 0x61, (byte) 0x96, (byte) 0x88, (byte) 0xFE, 0x7F,
            0x00, (byte) 0x98, 0x15, 0x07, (byte) 0x98, 0x60, 0x18, 0x1B, (byte) 0xC1,
            0x1F, 0x71, 0x33, (byte) 0xE0, 0x21, (byte) 0xFF, 0x01, 0x10, 0x00, (byte) 0xA0,
            0x00, 0x35, 0x00, (byte) 0xE0, 0x6C, 0x3F, 0x09, 0x00, 0x10, 0x00, (byte) 0xA0,
            0x00, 0x35, 0x04, (byte) 0xE0, 0x7C, (byte) 0xEF, 0x13, 0x00, 0x10, 0x00,
            (byte) 0xA0, 0x00, 0x35, 0x08, (byte) 0xE0, 0x6C, (byte) 0xBF, 0x13, 0x00
    };

    public D2SharedStashWriter(Variant variant, File file, byte[] originalContent) {
        this.variant = variant;
        this.file = file;
        this.originalContent = originalContent;
    }

    public D2SharedStashWriter(Variant variant, String filename, byte[] originalContent) {
        this(variant, new File(filename), originalContent);
    }


    public void write(D2SharedStash stash) {
        D2BitReader bitReader = new D2BitReader(originalContent.clone());
        int[] stashHeaderOffsets = getStashHeaderOffsets(variant, bitReader);
        
        // Check if pane 0 needs v1->v2 migration.
        // Pane 0 has a JM header at offset 64; the version byte at offset 66
        // is 0x01 for v1 (pre-migration) and 0x02 for v2 (post-migration).
        // Migration is needed only when chronicle runeword entries actually grow
        // and pane 0 is still in v1 format.
        boolean needsMigration = false;
        if (stash.getChronicle() != null && stash.getChronicle().isModified()) {
            int originalRunewords = getOriginalRunewordCount(stashHeaderOffsets);
            int newRunewords = stash.getChronicle().getNumRunewords();
            boolean pane0IsV1 = originalContent.length > 66
                    && originalContent[64] == (byte) 0x4A && originalContent[65] == (byte) 0x4D
                    && originalContent[66] == (byte) 0x01;
            int runewordDelta = newRunewords - originalRunewords;
            needsMigration = pane0IsV1 && runewordDelta > 0;
        }
        
        List<byte[]> stashPanes = new ArrayList<>();
        boolean chronicleWritten = false;
        
        for (int i = 0; i < stashHeaderOffsets.length; i++) {
            int paneStart = stashHeaderOffsets[i];
            int paneEnd = i + 1 < stashHeaderOffsets.length ? stashHeaderOffsets[i + 1] : originalContent.length;
            
            if (i == 0 && needsMigration) {
                // Apply v1->v2 migration to first pane
                stashPanes.add(applyMigrationToPane0(paneStart, paneEnd));
            } else if (i < variant.getSharedStashConfig().getItemStashPaneCount()) {
                if (stash.getChronicle() != null) {
                    // Chronicle-only updates must not mutate item panes.
                    stashPanes.add(Arrays.copyOfRange(originalContent, paneStart, paneEnd));
                } else {
                    stashPanes.add(writeStashPane(stash.getPane(i), bitReader, paneStart, paneEnd, bitReader.findNextFlag("JM", paneStart)));
                }
            } else if (!chronicleWritten && stash.getChronicle() != null && isChroniclePane(paneStart, paneEnd)) {
                if (stash.getChronicle().isModified()) {
                    stashPanes.add(writeChroniclePane(stash.getChronicle(), paneStart, paneEnd));
                } else {
                    stashPanes.add(Arrays.copyOfRange(originalContent, paneStart, paneEnd));
                }
                chronicleWritten = true;
            } else {
                stashPanes.add(Arrays.copyOfRange(originalContent, paneStart, paneEnd));
            }
        }
        writeToFile(stashPanes);
    }

    private boolean isChroniclePane(int paneStart, int paneEnd) {
        if (paneEnd - paneStart < 68) {
            return false;
        }
        return originalContent[paneStart + 64] == (byte) 0xC0
                && originalContent[paneStart + 65] == (byte) 0xED
                && originalContent[paneStart + 66] == (byte) 0xEA
                && originalContent[paneStart + 67] == (byte) 0xC0;
    }

    private int getOriginalRunewordCount(int[] stashHeaderOffsets) {
        // Find the chronicle pane and extract runeword count
        for (int i = 0; i < stashHeaderOffsets.length - 1; i++) {
            int paneStart = stashHeaderOffsets[i];
            int paneEnd = stashHeaderOffsets[i + 1];
            if (isChroniclePane(paneStart, paneEnd)) {
                return (originalContent[paneStart + 74] & 0xFF) | ((originalContent[paneStart + 75] & 0xFF) << 8);
            }
        }
        // Check last pane
        if (stashHeaderOffsets.length > 0) {
            int paneStart = stashHeaderOffsets[stashHeaderOffsets.length - 1];
            int paneEnd = originalContent.length;
            if (isChroniclePane(paneStart, paneEnd)) {
                return (originalContent[paneStart + 74] & 0xFF) | ((originalContent[paneStart + 75] & 0xFF) << 8);
            }
        }
        return 0;
    }

    private byte[] applyMigrationToPane0(int paneStart, int paneEnd) {
        final int insertionOffset = 96;
        if (paneEnd - paneStart < insertionOffset) {
            return Arrays.copyOfRange(originalContent, paneStart, paneEnd);
        }

        // Copy first 96 bytes
        byte[] migrated = new byte[paneEnd - paneStart + PANE0_VERSION_UPGRADE_BLOCK.length];
        System.arraycopy(originalContent, paneStart, migrated, 0, insertionOffset);

        // Update version byte from 1 to 2 (at offset 66)
        migrated[66] = 0x02;

        // Insert migration block at offset 96
        System.arraycopy(PANE0_VERSION_UPGRADE_BLOCK, 0, migrated, insertionOffset, PANE0_VERSION_UPGRADE_BLOCK.length);

        // Copy remaining pane data after inserted upgrade bytes.
        int remainingStart = paneStart + insertionOffset;
        int remainingLen = paneEnd - remainingStart;
        int migratedRemainingStart = insertionOffset + PANE0_VERSION_UPGRADE_BLOCK.length;
        System.arraycopy(originalContent, remainingStart, migrated, migratedRemainingStart, remainingLen);

        // Update the pane length field (bytes 16-19) to new size
        int newLength = migrated.length;
        writeInt32LE(migrated, 16, newLength);

        return migrated;
    }

    private byte[] writeChroniclePane(D2Chronicle chronicle, int paneStart, int paneEnd) {
        byte[] payload = buildChroniclePayload(chronicle);
        byte[] trailer = getOriginalChronicleTrailerBytes(paneStart, paneEnd);
        int paneLength = 64 + 4 + payload.length + trailer.length;
        byte[] paneBytes = new byte[paneLength];

        System.arraycopy(originalContent, paneStart, paneBytes, 0, 68);
        System.arraycopy(payload, 0, paneBytes, 68, payload.length);
        if (trailer.length > 0) {
            System.arraycopy(trailer, 0, paneBytes, 68 + payload.length, trailer.length);
        }
        writeInt32LE(paneBytes, 16, paneLength);
        return paneBytes;
    }

    private byte[] getOriginalChronicleTrailerBytes(int paneStart, int paneEnd) {
        int minChroniclePayloadStart = paneStart + 68;
        int entryDataStart = paneStart + 88;
        if (paneEnd <= entryDataStart || originalContent.length < minChroniclePayloadStart + 8) {
            return new byte[0];
        }

        int originalNumSetItems = readU16LEFromOriginal(paneStart + 70);
        int originalNumUniqueItems = readU16LEFromOriginal(paneStart + 72);
        int originalNumRunewords = readU16LEFromOriginal(paneStart + 74);
        int originalEntryBytes = (originalNumSetItems + originalNumUniqueItems + originalNumRunewords) * 10;
        int trailerStart = entryDataStart + originalEntryBytes;
        if (trailerStart < entryDataStart || trailerStart > paneEnd) {
            return new byte[0];
        }
        return Arrays.copyOfRange(originalContent, trailerStart, paneEnd);
    }

    private int readU16LEFromOriginal(int offset) {
        return (originalContent[offset] & 0xFF) | ((originalContent[offset + 1] & 0xFF) << 8);
    }

    private byte[] buildChroniclePayload(D2Chronicle chronicle) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeU16LE(out, chronicle.getVersion());
        writeU16LE(out, chronicle.getNumSetItems());
        writeU16LE(out, chronicle.getNumUniqueItems());
        writeU16LE(out, chronicle.getNumRunewords());

        for (int i = 0; i < 12; i++) {
            out.write(0);
        }

        writeChronicleEntries(out, chronicle.getSetEntries(), chronicle.getNumSetItems());
        writeChronicleEntries(out, chronicle.getUniqueEntries(), chronicle.getNumUniqueItems());
        writeChronicleEntries(out, chronicle.getRunewordEntries(), chronicle.getNumRunewords());
        return out.toByteArray();
    }

    private void writeChronicleEntries(ByteArrayOutputStream out, List<D2Chronicle.ChronicleEntry> entries, int count) {
        for (int i = 0; i < count; i++) {
            D2Chronicle.ChronicleEntry entry = i < entries.size() ? entries.get(i) : null;
            if (entry == null || !entry.isFound()) {
                for (int j = 0; j < 10; j++) {
                    out.write(0);
                }
                continue;
            }
            writeU16LE(out, entry.getRawField0());
            writeU32LE(out, entry.getRawTimestamp());
            writeU16LE(out, entry.getRawField6());
            writeU16LE(out, entry.getRawField8());
        }
    }

    private void writeU16LE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private void writeU32LE(ByteArrayOutputStream out, long value) {
        out.write((int) (value & 0xFF));
        out.write((int) ((value >>> 8) & 0xFF));
        out.write((int) ((value >>> 16) & 0xFF));
        out.write((int) ((value >>> 24) & 0xFF));
    }

    private void writeInt32LE(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value & 0xFF);
        bytes[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        bytes[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        bytes[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    private void writeToFile(List<byte[]> stashPanes) {
        D2BitReader bitWriter = new D2BitReader(new byte[0]);
        bitWriter.set_byte_pos(0);
        bitWriter.setBytes(concatenate(stashPanes));
        bitWriter.save(file.getAbsolutePath());
    }

    private byte[] writeStashPane(D2SharedStash.D2SharedStashPane pane,
                                  D2BitReader bitReader,
                                  int stashHeaderOffset,
                                  int paneEndOffset,
                                  int itemListStartOffset) {
        bitReader.set_byte_pos(stashHeaderOffset);
        D2SharedStash.Header.fromBytes(bitReader);
        int originalPaneEnd = paneEndOffset;
        int originalItemsEnd = findOriginalItemsEnd(bitReader, itemListStartOffset);
        byte[] trailingBytes = originalItemsEnd < originalPaneEnd
                ? Arrays.copyOfRange(originalContent, originalItemsEnd, originalPaneEnd)
                : new byte[0];

        int itemByteLength = pane.getItems().stream().map(it -> it.get_bytes().length).reduce(4, Integer::sum);
        bitReader.set_byte_pos(stashHeaderOffset);
        byte[] oldHeaderBytes = bitReader.get_bytes(itemListStartOffset - stashHeaderOffset);
        D2BitReader writer = new D2BitReader(new byte[oldHeaderBytes.length + itemByteLength + trailingBytes.length]);
        writer.setBytes(0, oldHeaderBytes);
        writeHeader(pane, writer, writer.get_length());
        writer.set_byte_pos(itemListStartOffset - stashHeaderOffset);
        writeItemBytes(pane, writer);
        if (trailingBytes.length > 0) {
            writer.setBytes(writer.get_byte_pos(), trailingBytes);
            writer.set_byte_pos(writer.get_byte_pos() + trailingBytes.length);
        }
        return writer.getFileContent();
    }

    private boolean isItemPaneUnchanged(D2SharedStash.D2SharedStashPane pane,
                                        D2BitReader bitReader,
                                        int paneStart,
                                        int itemListStartOffset) {
        bitReader.set_byte_pos(paneStart);
        D2SharedStash.Header header = D2SharedStash.Header.fromBytes(bitReader);
        if (header.getGold() != pane.getGold()) {
            return false;
        }

        List<D2Item> originalItems = readOriginalItems(bitReader, itemListStartOffset);
        List<D2Item> currentItems = pane.getItems();
        if (originalItems.size() != currentItems.size()) {
            return false;
        }
        for (int i = 0; i < currentItems.size(); i++) {
            if (!Arrays.equals(originalItems.get(i).get_bytes(), currentItems.get(i).get_bytes())) {
                return false;
            }
        }
        return true;
    }

    private int findOriginalItemsEnd(D2BitReader bitReader, int itemListStartOffset) {
        bitReader.set_byte_pos(itemListStartOffset);
        bitReader.skipBytes(2);
        int originalNumItems = (int) bitReader.read(16);
        for (int i = 0; i < originalNumItems; i++) {
            int itemStartBitPos = bitReader.get_pos();
            parseItemWithResync(bitReader, itemStartBitPos);
        }
        return bitReader.get_byte_pos();
    }

    private List<D2Item> readOriginalItems(D2BitReader bitReader, int itemListStartOffset) {
        bitReader.set_byte_pos(itemListStartOffset);
        bitReader.skipBytes(2);
        int originalNumItems = (int) bitReader.read(16);
        List<D2Item> items = new ArrayList<>();
        for (int i = 0; i < originalNumItems; i++) {
            int itemStartBitPos = bitReader.get_pos();
            items.add(parseItemWithResync(bitReader, itemStartBitPos));
        }
        return items;
    }

    private D2Item parseItemWithResync(D2BitReader bitReader, int itemStartBitPos) {
        int[] deltas = new int[]{0, -24, -16, -8, 8, 16, 24, -32, 32};
        Exception lastException = null;

        for (int delta : deltas) {
            int candidateStart = itemStartBitPos + delta;
            if (candidateStart < 0) {
                continue;
            }

            bitReader.set_pos(candidateStart);
            try {
                return new D2Item(file.getAbsolutePath(), bitReader, 75);
            } catch (Exception primary) {
                lastException = primary;
            }

            bitReader.set_pos(candidateStart);
            try {
                return new D2Item(file.getAbsolutePath(), bitReader, 75, false);
            } catch (Exception fallback) {
                lastException = fallback;
            }
        }

        bitReader.set_pos(itemStartBitPos);
        if (lastException != null) {
            throw new RuntimeException("Failed to parse original shared stash item", lastException);
        }
        throw new RuntimeException("Failed to parse original shared stash item");
    }

    public void writeHeader(D2SharedStash.D2SharedStashPane pane, D2BitReader bitWriter, long length) {
        bitWriter.skipBytes(8);
        long version = bitWriter.read(8);
        if (version != VersionController.Version.D2R3.getFileVersionIdentifier())
            throw new RuntimeException("Overwriting wrong version stash");
        bitWriter.skipBytes(3);
        bitWriter.write(pane.getGold(), 24);
        bitWriter.skipBytes(1);
        bitWriter.write(length, 24);
    }

    private void writeItemBytes(D2SharedStash.D2SharedStashPane pane, D2BitReader writer) {
        writer.write(19786, 16);
        List<D2Item> items = pane.getItems();
        writer.write(items.size(), 16);
        for (D2Item item : items) {
            byte[] bytesToWrite = item.get_bytes();
            writer.setBytes(writer.get_byte_pos(), bytesToWrite);
            writer.set_byte_pos(writer.get_byte_pos() + bytesToWrite.length);
        }
    }

    private byte[] concatenate(List<byte[]> panes) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (byte[] pane : panes) {
                outputStream.write(pane);
            }
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
