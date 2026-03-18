package gomule.d2x;

import gomule.item.D2Item;
import gomule.model.VersionController.Variant;
import gomule.util.D2BitReader;
import gomule.util.D2ItemException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.io.File;
import java.util.ArrayList;

import static gomule.d2x.D2Stash.FIXED_STASH_CHAR_LEVEL;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class D2StashMigrationDebugTest {

    @BeforeAll
    public static void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    @Test
    public void testLoadPatch26() {
        String path = "../savefiles/patch26.d2x";
        if (!new File(path).exists()) {
            System.out.println("Skipping: patch26.d2x not found at " + path);
            return;
        }
        D2BitReader bitReader = new D2BitReader(path);
        // Skip old-format header: D2X(3) + numItems(2) + version(2) + checksum(4) = 11 bytes
        bitReader.set_byte_pos(3);
        long numItems = bitReader.read(16);
        System.out.println("Total items: " + numItems);
        System.out.println("Total file length: " + bitReader.get_length() + " bytes");
        bitReader.set_byte_pos(11); // old format header length

        ArrayList<D2Item> items = new ArrayList<>();
        int failCount = 0;
        for (int i = 0; i < numItems; i++) {
            int bytePos = bitReader.get_byte_pos();
            try {
                D2Item item = new D2Item(path, bitReader, FIXED_STASH_CHAR_LEVEL, false);
                int endBytePos = bitReader.get_byte_pos();
                System.out.println("OK item #" + i + " startByte=" + bytePos + " endByte=" + endBytePos + " size=" + (endBytePos - bytePos) + " fp=" + item.getFingerprint() + " name=" + item.getName());
                if (i >= 1 && i <= 3) {
                    // Hex dump the next 30 bytes from where the item ends
                    int peekPos = endBytePos;
                    bitReader.set_byte_pos(peekPos);
                    StringBuilder hex = new StringBuilder();
                    for (int h = 0; h < 30 && peekPos + h < bitReader.get_length(); h++) {
                        hex.append(String.format("%02x ", bitReader.get_bytes(1)[0] & 0xFF));
                    }
                    bitReader.set_byte_pos(endBytePos);
                    System.out.println("  Hex after item #" + i + " at byte " + peekPos + ": " + hex);
                }
                items.add(item);
            } catch (Exception e) {
                failCount++;
                System.out.println("FAILED at item #" + i + " startByte=" + bytePos + ": " + e.getMessage());
                // Hex dump the next 20 bytes from where we are
                bitReader.set_byte_pos(bytePos);
                StringBuilder hex = new StringBuilder();
                for (int h = 0; h < 20 && bytePos + h < bitReader.get_length(); h++) {
                    hex.append(String.format("%02x ", bitReader.get_bytes(1)[0] & 0xFF));
                }
                System.out.println("  Hex at byte " + bytePos + ": " + hex);
                break;
            }
        }
        System.out.println("Loaded " + items.size() + " of " + numItems + " items (" + failCount + " failures)");
    }

    @Test
    public void testRoundTripPatch26() throws Exception {
        String path = "../savefiles/patch26.d2x";
        if (!new File(path).exists()) {
            System.out.println("Skipping: patch26.d2x not found at " + path);
            return;
        }

        // Load old-format stash (items get bit-converted during read)
        D2Stash oldStash = new D2StashReader().readStash(Variant.EXPANSION, path);
        System.out.println("Loaded " + oldStash.getNrItems() + " items from old stash");

        // Write all items to a new in-memory stash as if saving to new format
        int itemByteLength = oldStash.getItemList().stream()
                .map(it -> it.get_bytes().length).reduce(0, Integer::sum);
        D2BitReader bitWriter = new D2BitReader(new byte[D2StashWriter.HEADER_BYTE_LENGTH + itemByteLength]);
        bitWriter.write('D', 8);
        bitWriter.write('2', 8);
        bitWriter.write('X', 8);
        bitWriter.write(oldStash.getNrItems(), 16);
        bitWriter.write(105, 16); // D2R3 version
        bitWriter.write(Variant.EXPANSION.getStashIdentifier(), 16);
        bitWriter.skipBytes(4); // checksum placeholder
        for (D2Item item : oldStash.getItemList()) {
            byte[] bytes = item.get_bytes();
            bitWriter.setBytes(bitWriter.get_byte_pos(), bytes);
            bitWriter.set_byte_pos(bitWriter.get_byte_pos() + bytes.length);
        }
        // Write checksum
        bitWriter.set_byte_pos(D2StashWriter.CHECKSUM_BYTE_OFFSET_START);
        bitWriter.write(D2StashWriter.calculateChecksum(bitWriter), D2StashWriter.CHECKSUM_BYTE_LENGTH * 8);

        // Now try to re-read
        D2BitReader reReader = new D2BitReader(bitWriter.getFileContent());
        try {
            D2Stash reloaded = new D2StashReader().readStash(Variant.EXPANSION, "test.d2x", reReader);
            System.out.println("Round-trip SUCCESS: " + reloaded.getNrItems() + " items");
        } catch (Exception e) {
            // Try item by item to find which one fails
            System.out.println("Round-trip FAILED: " + e.getMessage());
            reReader = new D2BitReader(bitWriter.getFileContent());
            reReader.set_byte_pos(D2StashWriter.HEADER_BYTE_LENGTH);
            for (int i = 0; i < oldStash.getNrItems(); i++) {
                int bytePos = reReader.get_byte_pos();
                try {
                    D2Item item = new D2Item("test.d2x", reReader, FIXED_STASH_CHAR_LEVEL);
                    System.out.println("  OK item #" + i + " name=" + item.getName());
                } catch (Exception ex) {
                    D2Item origItem = oldStash.getItemList().get(i);
                    System.out.println("  FAILED at item #" + i + " bytePos=" + bytePos + ": " + ex.getMessage());
                    System.out.println("  Original item: name=" + origItem.getName() + " fp=" + origItem.getFingerprint());
                    byte[] origBytes = origItem.get_bytes();
                    StringBuilder hex = new StringBuilder();
                    for (int h = 0; h < Math.min(80, origBytes.length); h++) {
                        hex.append(String.format("%02x ", origBytes[h] & 0xFF));
                    }
                    System.out.println("  Converted item bytes length: " + origBytes.length);
                    System.out.println("  Converted item bytes (hex): " + hex);
                    break;
                }
            }
        }
    }
}
