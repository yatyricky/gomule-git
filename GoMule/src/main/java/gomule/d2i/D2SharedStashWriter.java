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
        
        List<byte[]> stashPanes = new ArrayList<>();
        boolean chronicleWritten = false;
        
        for (int i = 0; i < stashHeaderOffsets.length; i++) {
            int paneStart = stashHeaderOffsets[i];
            int paneEnd = i + 1 < stashHeaderOffsets.length ? stashHeaderOffsets[i + 1] : originalContent.length;
            
            if (i < variant.getSharedStashConfig().getItemStashPaneCount()) {
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

    /**
     * Writes a modified chronicle pane by patching the original pane bytes.
     * Copies the first 88 bytes verbatim (pane header + C0EDEAC0 magic + chronicle header
     * including version and reserved bytes), then writes all entry raw bytes, appends the
     * original trailer, and patches only the count fields and pane length.
     *
     * Chronicle pane layout (offsets relative to pane start):
     *   [0..63]   standard pane header  (preserved verbatim)
     *   [64..67]  C0EDEAC0 magic        (preserved verbatim)
     *   [68..69]  version               (preserved verbatim)
     *   [70..71]  numSetItems           (patched)
     *   [72..73]  numUniqueItems        (patched)
     *   [74..75]  numRunewords          (patched)
     *   [76..87]  reserved 12 bytes     (preserved verbatim)
     *   [88..]    10-byte entries: set, then unique, then runeword
     *   [..]      trailer bytes         (preserved verbatim)
     */
    private byte[] writeChroniclePane(D2Chronicle chronicle, int paneStart, int paneEnd) {
        byte[] origPane = Arrays.copyOfRange(originalContent, paneStart, paneEnd);

        // Compute original entry area bounds
        int origNumSet = readU16LE(origPane, 70);
        int origNumUniq = readU16LE(origPane, 72);
        int origNumRW = readU16LE(origPane, 74);
        int origEntryBytes = (origNumSet + origNumUniq + origNumRW) * 10;
        int entryAreaStart = 88;
        int origTrailerStart = entryAreaStart + origEntryBytes;
        byte[] trailer = Arrays.copyOfRange(origPane, origTrailerStart, origPane.length);

        // Build new entry area — just raw bytes one after another
        ByteArrayOutputStream entryBuf = new ByteArrayOutputStream();
        writeEntryRawBytes(entryBuf, chronicle.getSetEntries(), chronicle.getNumSetItems());
        writeEntryRawBytes(entryBuf, chronicle.getUniqueEntries(), chronicle.getNumUniqueItems());
        writeEntryRawBytes(entryBuf, chronicle.getRunewordEntries(), chronicle.getNumRunewords());
        byte[] newEntryArea = entryBuf.toByteArray();

        // Assemble: original header (88 bytes) + new entries + original trailer
        int newPaneLength = entryAreaStart + newEntryArea.length + trailer.length;
        byte[] newPane = new byte[newPaneLength];
        System.arraycopy(origPane, 0, newPane, 0, entryAreaStart);
        System.arraycopy(newEntryArea, 0, newPane, entryAreaStart, newEntryArea.length);
        System.arraycopy(trailer, 0, newPane, entryAreaStart + newEntryArea.length, trailer.length);

        // Patch the three count fields
        writeU16LE(newPane, 70, chronicle.getNumSetItems());
        writeU16LE(newPane, 72, chronicle.getNumUniqueItems());
        writeU16LE(newPane, 74, chronicle.getNumRunewords());

        // Patch pane length (bytes 16..19)
        writeInt32LE(newPane, 16, newPaneLength);

        return newPane;
    }

    private void writeEntryRawBytes(ByteArrayOutputStream out, List<D2Chronicle.ChronicleEntry> entries, int count) {
        for (int i = 0; i < count; i++) {
            D2Chronicle.ChronicleEntry entry = i < entries.size() ? entries.get(i) : null;
            if (entry == null || !entry.isFound()) {
                out.write(new byte[10], 0, 10);
                continue;
            }
            byte[] raw = entry.getRawBytes();
            if (raw != null && raw.length == 10) {
                out.write(raw, 0, 10);
            } else {
                // Fabricated entry — build raw bytes from decoded fields
                writeU16LE(out, entry.getRawField0());
                writeU32LE(out, entry.getRawTimestamp());
                writeU16LE(out, entry.getRawField6());
                writeU16LE(out, entry.getRawField8());
            }
        }
    }

    private int readU16LE(byte[] buf, int offset) {
        return (buf[offset] & 0xFF) | ((buf[offset + 1] & 0xFF) << 8);
    }

    private void writeU16LE(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >>> 8) & 0xFF);
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
