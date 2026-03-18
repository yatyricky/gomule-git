package gomule.d2x;

import gomule.model.VersionController.VersionException;
import gomule.util.D2BitReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import randall.d2files.D2TxtFile;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static gomule.item.D2ItemTest.decode;
import static gomule.model.VersionController.Variant.EXPANSION;
import static org.junit.jupiter.api.Assertions.*;

public class D2StashReaderTest {

    @BeforeAll
    public static void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    @Test
    public void testBadFilenames() {
        assertEquals("Incorrect Stash file name", assertThrows(RuntimeException.class, () -> new D2StashReader().readStash(EXPANSION, null)).getMessage());
        assertEquals("Incorrect Stash file name", assertThrows(RuntimeException.class, () -> new D2StashReader().readStash(EXPANSION, "bla.wrong")).getMessage());
    }

    @Test
    public void testNonD2XStash() {
        String stashBytes = "46325801006900640267071004A0080588144FB400";
        assertEquals("Incorrect Stash type: F2X", assertThrows(RuntimeException.class, () -> new D2StashReader().readStash(EXPANSION, "foo.d2x", new D2BitReader(decode(stashBytes)))).getMessage());
    }

    @Test
    public void testOldVersionStashLoadsReadOnly(@TempDir Path tempDir) throws Exception {
        // Build old-format stash (version 99 = D2R2_5) with 1 Super Healing Potion
        byte[] itemBytes = decode("1004A0080588144FB400");
        byte[] header = decode("44325801006300"); // D2X + 1 item + version 99
        byte[] oldFile = new byte[header.length + 4 + itemBytes.length];
        System.arraycopy(header, 0, oldFile, 0, header.length);
        System.arraycopy(itemBytes, 0, oldFile, header.length + 4, itemBytes.length);

        D2BitReader tempReader = new D2BitReader(oldFile);
        long checksum = calculateOldFormatChecksum(tempReader);
        tempReader.set_byte_pos(7);
        tempReader.write(checksum, 32);
        oldFile = tempReader.getFileContent();

        Path stashFile = tempDir.resolve("foo.d2x");
        Files.write(stashFile, oldFile);

        byte[] originalFileBytes = Files.readAllBytes(stashFile);

        D2Stash loaded = new D2StashReader().readStash(EXPANSION, stashFile.toString());
        assertEquals(1, loaded.getNrItems());
        assertFalse(loaded.isModified(), "Old stash should not be marked as modified");

        // Verify no backup was created
        Path backupFile = tempDir.resolve("foo.d2x.D2R2_5.bak");
        assertFalse(Files.exists(backupFile), "No backup file should be created");

        // Verify original file was not modified
        assertArrayEquals(originalFileBytes, Files.readAllBytes(stashFile), "Original file should be untouched");
    }

    @Test
    public void testWrongVariantStash() {
        String stashBytes = "44 32 58 01 00 69 00 01 00 64 02 9B 1D 10 04 A0 08 05 88 14 4F B4 00";
        assertEquals("Please change the workspace variant before loading this file.\n" +
                "Current Workspace: Expansion\n" +
                "File Needs: Classic", assertThrows(VersionException.class, () -> new D2StashReader().readStash(EXPANSION, "foo.d2x", new D2BitReader(decode(stashBytes)))).getMessage());
    }

    @Test
    public void testGarbledVariantStash() {
        String stashBytes = "44 32 58 01 00 69 00 63 00 64 02 CC 1D 10 04 A0 08 05 88 14 4F B4 00";
        assertEquals("Please change the workspace variant before loading this file.\n" +
                "Current Workspace: Expansion\n" +
                "File Needs: Unknown", assertThrows(VersionException.class, () -> new D2StashReader().readStash(EXPANSION, "foo.d2x", new D2BitReader(decode(stashBytes)))).getMessage());
    }

    @Test
    public void testWrongChecksumStash() {
        String stashBytes = "44325801006900020064829B1D1004A0080588144DB400";
        assertEquals("Checksum Incorrect! Expected: 496730724 Found: 496730716", assertThrows(RuntimeException.class, () -> new D2StashReader().readStash(EXPANSION, "foo.d2x", new D2BitReader(decode(stashBytes)))).getMessage());
    }

    @Test
    public void testEmptyRead() {
        D2Stash d2Stash = new D2StashReader().readStash(EXPANSION, "foo.d2x");
        StringWriter actual = new StringWriter();
        d2Stash.fullDump(new PrintWriter(actual));
        Assertions.assertEquals("foo.d2x\n" +
                "\n" +
                "Finished: foo.d2x\n\n", actual.toString().replace("\r", ""));
    }

    @Test
    public void testSimpleRead() {
        D2Stash d2Stash = new D2StashReader().readStash(EXPANSION, "foo.d2x", new D2BitReader(decode("44325801006900020064829B1D1004A0080588144FB400")));
        assertTrue(d2Stash.isHC());
        assertTrue(d2Stash.isSC());
        StringWriter actual = new StringWriter();
        d2Stash.fullDump(new PrintWriter(actual));
        Assertions.assertEquals("foo.d2x\n" +
                "\n" +
                "\n" +
                "Super Healing Potion\n" +
                "Version: Resurrected\n" +
                "Replenish Life +320\n" +
                "Finished: foo.d2x\n\n", actual.toString().replace("\r", ""));
    }

    @Test
    public void testSCRead() {
        D2Stash d2Stash = new D2StashReader().readStash(EXPANSION, "sc_foo.d2x");
        assertFalse(d2Stash.isHC());
        assertTrue(d2Stash.isSC());
    }

    @Test
    public void testHCRead() {
        D2Stash d2Stash = new D2StashReader().readStash(EXPANSION, "hc_foo.d2x");
        assertTrue(d2Stash.isHC());
        assertFalse(d2Stash.isSC());
    }

    @Test
    public void testMigrateEmptyOldFormatStash(@TempDir Path tempDir) throws Exception {
        // v62.d2x: version 99 (D2R2_5), 0 items, no variant field, 11-byte header
        byte[] oldFormatBytes = decode("4432580000630060D80100");
        Path stashFile = tempDir.resolve("test.d2x");
        Files.write(stashFile, oldFormatBytes);

        D2Stash migrated = new D2StashReader().migrateExpansionToWarlock(stashFile.toString());
        assertEquals(0, migrated.getNrItems());

        // Verify the migrated file can be read as ROW variant
        D2Stash reloaded = new D2StashReader().readStash(
                gomule.model.VersionController.Variant.ROW,
                stashFile.toString());
        assertEquals(0, reloaded.getNrItems());
    }

    @Test
    public void testMigrateOldFormatStashWithItems(@TempDir Path tempDir) throws Exception {
        // Old format: D2X magic + 1 item + version 99 + checksum placeholder + item bytes
        // Build it: header = "D2X" + numItems(1) + version(99) + checksum(placeholder)
        // Then append a Super Healing Potion item: "1004A0080588144FB400"
        // The checksum needs to be computed after assembly
        
        // Start with a known working new-format stash: 1 item, version 105, variant 2
        // "44325801006900020064829B1D1004A0080588144FB400"
        // The item bytes are: "1004A0080588144FB400" (10 bytes = Super Healing Potion)
        byte[] itemBytes = decode("1004A0080588144FB400");
        
        // Build old format: "D2X" + numItems(1,16bit) + version(99,16bit) + checksum(4bytes) + items
        // Checksum will be calculated after writing everything else
        byte[] header = decode("44325801006300"); // D2X + 1 item + version 99
        byte[] oldFile = new byte[header.length + 4 + itemBytes.length]; // +4 for checksum
        System.arraycopy(header, 0, oldFile, 0, header.length);
        System.arraycopy(itemBytes, 0, oldFile, header.length + 4, itemBytes.length);
        
        // Calculate checksum (zeroed at offset 7, length 4)
        D2BitReader tempReader = new D2BitReader(oldFile);
        long checksum = calculateOldFormatChecksum(tempReader);
        
        // Write checksum at byte offset 7
        tempReader.set_byte_pos(7);
        tempReader.write(checksum, 32);
        oldFile = tempReader.getFileContent();
        
        Path stashFile = tempDir.resolve("test.d2x");
        Files.write(stashFile, oldFile);

        D2Stash migrated = new D2StashReader().migrateExpansionToWarlock(stashFile.toString());
        assertEquals(1, migrated.getNrItems());

        // Verify the migrated file can be read as ROW variant
        D2Stash reloaded = new D2StashReader().readStash(
                gomule.model.VersionController.Variant.ROW,
                stashFile.toString());
        assertEquals(1, reloaded.getNrItems());

        StringWriter actual = new StringWriter();
        reloaded.fullDump(new PrintWriter(actual));
        assertTrue(actual.toString().contains("Super Healing Potion"));
    }

    private static long calculateOldFormatChecksum(D2BitReader bitReader) {
        long lCheckSum = 0;
        bitReader.set_byte_pos(0);
        for (int i = 0; i < bitReader.get_length(); i++) {
            long lByte = bitReader.read(8);
            if (i >= 7 && i < 11) {
                lByte = 0;
            }
            long upshift = lCheckSum << 33 >>> 32;
            long add = lByte + ((lCheckSum >>> 31) == 1 ? 1 : 0);
            lCheckSum = upshift + add;
        }
        return lCheckSum;
    }

    @Test
    public void testOldItemsRoundTripThroughNewStash(@TempDir Path tempDir) throws Exception {
        // Build an old-format stash with 1 Super Healing Potion
        byte[] itemBytes = decode("1004A0080588144FB400");
        byte[] header = decode("44325801006300"); // D2X + 1 item + version 99
        byte[] oldFile = new byte[header.length + 4 + itemBytes.length];
        System.arraycopy(header, 0, oldFile, 0, header.length);
        System.arraycopy(itemBytes, 0, oldFile, header.length + 4, itemBytes.length);

        D2BitReader tempReader = new D2BitReader(oldFile);
        long checksum = calculateOldFormatChecksum(tempReader);
        tempReader.set_byte_pos(7);
        tempReader.write(checksum, 32);
        oldFile = tempReader.getFileContent();

        // Load old stash (read-only in memory, items get bit-converted)
        Path oldStashFile = tempDir.resolve("old.d2x");
        Files.write(oldStashFile, oldFile);
        D2Stash oldStash = new D2StashReader().readStash(EXPANSION, oldStashFile.toString());
        assertEquals(1, oldStash.getNrItems());

        // Create a new-format stash and transfer the item
        Path newStashFile = tempDir.resolve("new.d2x");
        D2Stash newStash = new D2StashReader().readStash(EXPANSION, newStashFile.toString());
        assertEquals(0, newStash.getNrItems());
        newStash.addItem(oldStash.getItemList().get(0));
        new D2StashWriter(EXPANSION, newStashFile.toString()).write(newStash);

        // Re-read the new stash - this should NOT throw (items have correct new-format bits)
        D2Stash reloaded = new D2StashReader().readStash(EXPANSION, newStashFile.toString());
        assertEquals(1, reloaded.getNrItems());

        StringWriter actual = new StringWriter();
        reloaded.fullDump(new PrintWriter(actual));
        assertTrue(actual.toString().contains("Super Healing Potion"), "Transferred item should be readable");
    }
}
