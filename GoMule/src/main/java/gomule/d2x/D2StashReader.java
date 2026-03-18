package gomule.d2x;

import gomule.item.D2Item;
import gomule.model.VersionController;
import gomule.model.VersionController.Variant;
import gomule.util.D2BitReader;

import java.io.File;
import java.util.ArrayList;

import static gomule.d2x.D2Stash.FIXED_STASH_CHAR_LEVEL;
import static gomule.d2x.D2StashWriter.*;
import static gomule.model.VersionController.Variant.tryParseStashIdentifier;
import static gomule.model.VersionController.Version.D2R3;

public class D2StashReader {
    public D2Stash readStash(Variant variant, String filename) {
        return readStash(variant, filename, new D2BitReader(filename));
    }

    public D2Stash readStash(Variant variant, String filename, D2BitReader bitReader) {
        if (filename == null || !filename.toLowerCase().endsWith(".d2x")) {
            throw new RuntimeException("Incorrect Stash file name");
        }
        if (!bitReader.isNewFile() && isOldVersion(bitReader)) {
            return migrateAndRead(variant, filename, bitReader);
        }
        File file = new File(filename);
        boolean iSC = file.getName().toLowerCase().startsWith("sc_");
        boolean iHC = file.getName().toLowerCase().startsWith("hc_");
        if (!iSC && !iHC) {
            iSC = true;
            iHC = true;
        }
        return new D2Stash(variant, filename, checkHeaderAndLoadItems(variant, filename, bitReader), iSC, iHC, bitReader.isNewFile());
    }

    private boolean isOldVersion(D2BitReader bitReader) {
        int originalPos = bitReader.get_pos();
        bitReader.set_byte_pos(0);
        byte[] magic = bitReader.get_bytes(3);
        if (!"D2X".equals(new String(magic))) {
            bitReader.set_pos(originalPos);
            return false;
        }
        bitReader.set_byte_pos(5);
        long versionNumber = bitReader.read(16);
        bitReader.set_pos(originalPos);
        return versionNumber != D2R3.getFileVersionIdentifier();
    }

    private D2Stash migrateAndRead(Variant variant, String filename, D2BitReader bitReader) {
        bitReader.set_byte_pos(5);
        long versionNumber = bitReader.read(16);
        VersionController.Version oldVersion = VersionController.Version.tryParseFileVersionIdentifier((int) versionNumber);
        String versionLabel = (oldVersion != null) ? oldVersion.name() : "v" + versionNumber;

        // Read old format items in memory without writing to filesystem
        ArrayList<D2Item> items = extractItemsWithoutVariantCheck(filename, bitReader);
        File file = new File(filename);
        boolean iSC = file.getName().toLowerCase().startsWith("sc_");
        boolean iHC = file.getName().toLowerCase().startsWith("hc_");
        if (!iSC && !iHC) {
            iSC = true;
            iHC = true;
        }

        System.err.println("Loaded old stash (" + versionLabel + ") in memory: " + filename + " (" + items.size() + " items)");

        return new D2Stash(variant, filename, items, iSC, iHC, false);
    }

    private ArrayList<D2Item> checkHeaderAndLoadItems(Variant variant, String filename, D2BitReader bitReader) {
        if (!bitReader.isNewFile()) {
            bitReader.set_byte_pos(0);
            byte[] startingBytes = bitReader.get_bytes(3);
            String lStart = new String(startingBytes);
            if (!"D2X".equals(lStart)) throw new RuntimeException("Incorrect Stash type: " + lStart);
            checkVersionAndVariant(variant, bitReader);
            checkChecksum(bitReader, CHECKSUM_BYTE_OFFSET_START);
            return readItems(filename, bitReader);
        } else {
            return new ArrayList<>();
        }
    }

    private void checkVersionAndVariant(Variant expectedVariant, D2BitReader bitReader) {
        bitReader.set_byte_pos(5);
        long versionNumber = bitReader.read(16);
        if (versionNumber != D2R3.getFileVersionIdentifier())
            throw VersionController.VersionException.forVersion(D2R3, VersionController.Version.tryParseFileVersionIdentifier((int) versionNumber));
        int variantAsInt = (int) bitReader.read(16);
        Variant variantOrNull = tryParseStashIdentifier(variantAsInt);
        if (variantOrNull != expectedVariant)
            throw VersionController.VersionException.forVariant(expectedVariant, variantOrNull);
    }

    private void checkChecksum(D2BitReader bitReader, int checksumByteOffset) {
        bitReader.set_byte_pos(checksumByteOffset);
        long originalChecksum = bitReader.read(CHECKSUM_BYTE_LENGTH * 8);
        long calculatedChecksum = calculateChecksum(bitReader, checksumByteOffset);
        if (originalChecksum != calculatedChecksum) {
            throw new RuntimeException("Checksum Incorrect! Expected: " + originalChecksum + " Found: " + calculatedChecksum);
        }
    }

    private static long calculateChecksum(D2BitReader bitReader, int checksumByteOffset) {
        long lCheckSum = 0;
        int originalPos = bitReader.get_pos();
        bitReader.set_byte_pos(0);
        for (int i = 0; i < bitReader.get_length(); i++) {
            long lByte = bitReader.read(8);
            if (i >= checksumByteOffset && i < (checksumByteOffset + CHECKSUM_BYTE_LENGTH)) {
                lByte = 0;
            }
            long upshift = lCheckSum << 33 >>> 32;
            long add = lByte + ((lCheckSum >>> 31) == 1 ? 1 : 0);
            lCheckSum = upshift + add;
        }
        bitReader.set_pos(originalPos);
        return lCheckSum;
    }

    private ArrayList<D2Item> readItems(String filename, D2BitReader bitReader) {
        bitReader.set_byte_pos(3);
        long numItems = bitReader.read(16);
        bitReader.set_byte_pos(HEADER_BYTE_LENGTH);
        ArrayList<D2Item> items = new ArrayList<>();
        for (int i = 0; i < numItems; i++) {
            D2Item lItem;
            try {
                lItem = new D2Item(filename, bitReader, FIXED_STASH_CHAR_LEVEL);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            items.add(lItem);
        }
        return items;
    }

    /**
     * Migrates an EXPANSION d2x stash file to the WARLOCK (ROW) variant.
     * Reads the file assuming EXPANSION variant, extracts all items, and writes
     * them back as a WARLOCK variant stash file.
     *
     * @param filename the path to the EXPANSION d2x file to migrate
     * @return the migrated D2Stash object with WARLOCK variant
     * @throws RuntimeException if the file is not a valid d2x file or if reading/writing fails
     */
    public D2Stash migrateExpansionToWarlock(String filename) {
        if (filename == null || !filename.toLowerCase().endsWith(".d2x")) {
            throw new RuntimeException("Incorrect Stash file name");
        }

        // Read the expansion stash (without strict variant checking)
        D2BitReader bitReader = new D2BitReader(filename);
        File file = new File(filename);
        boolean iSC = file.getName().toLowerCase().startsWith("sc_");
        boolean iHC = file.getName().toLowerCase().startsWith("hc_");
        if (!iSC && !iHC) {
            iSC = true;
            iHC = true;
        }

        // Verify it's a valid d2x file and extract items
        ArrayList<D2Item> items = extractItemsWithoutVariantCheck(filename, bitReader);

        // Create a new stash with WARLOCK variant
        D2Stash warlockStash = new D2Stash(Variant.ROW, filename, items, iSC, iHC, false);
        
        // Save the migrated stash (this will overwrite the original file)
        D2StashWriter writer = new D2StashWriter(Variant.ROW, filename);
        writer.write(warlockStash);
        
        return warlockStash;
    }

    /**
     * Extracts items from a d2x file without checking the version or variant.
     * This is useful for migration purposes where we need to read files regardless
     * of their version or variant type.
     * <p>
     * Old format (version &lt; 105): 11-byte header (no variant field, checksum at byte 7)
     * New format (version = 105): 13-byte header (has variant field, checksum at byte 9)
     *
     * @param filename the path to the d2x file
     * @param bitReader the bit reader for the file
     * @return list of items extracted from the file
     */
    private ArrayList<D2Item> extractItemsWithoutVariantCheck(String filename, D2BitReader bitReader) {
        if (bitReader.isNewFile()) {
            return new ArrayList<>();
        }

        // Verify header magic bytes
        bitReader.set_byte_pos(0);
        byte[] startingBytes = bitReader.get_bytes(3);
        String magic = new String(startingBytes);
        if (!"D2X".equals(magic)) {
            throw new RuntimeException("Incorrect Stash type: " + magic);
        }

        // Read version (but don't reject old versions)
        bitReader.set_byte_pos(5);
        long versionNumber = bitReader.read(16);
        boolean isOldFormat = versionNumber != D2R3.getFileVersionIdentifier();

        int checksumOffset;
        int headerLength;
        if (isOldFormat) {
            // Old format: no variant field, checksum at byte 7, header = 11 bytes
            checksumOffset = 7;
            headerLength = 11;
        } else {
            // New format: has variant field, checksum at byte 9, header = 13 bytes
            bitReader.read(16); // Read and discard variant
            checksumOffset = CHECKSUM_BYTE_OFFSET_START;
            headerLength = HEADER_BYTE_LENGTH;
        }

        // Verify checksum
        checkChecksum(bitReader, checksumOffset);

        // Read items starting after the header
        bitReader.set_byte_pos(3);
        long numItems = bitReader.read(16);
        bitReader.set_byte_pos(headerLength);
        ArrayList<D2Item> items = new ArrayList<>();
        for (int i = 0; i < numItems; i++) {
            try {
                items.add(new D2Item(filename, bitReader, FIXED_STASH_CHAR_LEVEL, false));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return items;
    }
}
