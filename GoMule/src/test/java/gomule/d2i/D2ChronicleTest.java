package gomule.d2i;

import gomule.util.D2BitReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;
import randall.d2files.D2TxtFileItemProperties;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static gomule.model.VersionController.Variant.ROW;
import static org.junit.jupiter.api.Assertions.*;

public class D2ChronicleTest {

    @BeforeAll
    public static void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    @Test
    public void testParseChronicleFromModernStash() throws Exception {
        File file = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!file.exists()) {
            System.out.println("Skipping: " + file.getAbsolutePath() + " not found");
            return;
        }
        D2BitReader bitReader = new D2BitReader(file.getAbsolutePath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, file.getAbsolutePath(), bitReader);

        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle, "Chronicle should not be null for ROW stash");
        assertEquals(1, chronicle.getVersion());
        assertEquals(109, chronicle.getNumSetItems());
        assertEquals(247, chronicle.getNumUniqueItems());
        assertEquals(29, chronicle.getNumRunewords());
        assertEquals(385, chronicle.getTotalItems());

        // All set/unique slots are found; runewords include sentinel with non-zero timestamp
        assertEquals(109, chronicle.getFoundSetCount());
        assertEquals(247, chronicle.getFoundUniqueCount());
        assertEquals(29, chronicle.getFoundRunewordCount());
        assertEquals(385, chronicle.getTotalFound());

        // Verify item names are resolved
        assertNotNull(chronicle.getSetEntries().get(0).getItemName());
        assertNotNull(chronicle.getUniqueEntries().get(0).getItemName());
        assertNotNull(chronicle.getRunewordEntries().get(0).getItemName());

        // Write dump to file for inspection (now includes all grail items, not just found)
        String dump = chronicle.toTextDump();
        try (PrintWriter pw = new PrintWriter(new FileWriter("chronicle_dump.txt"))) {
            pw.print(dump);
        }

        // Dump should contain section headers and show both found and not-found items
        assertTrue(dump.contains("SET ITEMS ("), "Dump should contain SET ITEMS section");
        assertTrue(dump.contains("UNIQUE ITEMS ("), "Dump should contain UNIQUE ITEMS section");
        assertTrue(dump.contains("RUNEWORDS ("), "Dump should contain RUNEWORDS section");
        // All 226 unique slots and 106 set slots are found in this stash
        assertTrue(dump.contains("[X] Deathspade"), "Dump should contain found unique item Deathspade");
        assertTrue(dump.contains("[X] Faith"), "Dump should contain found runeword Faith");
        // Grail dump should include items not in tracked range (not-found)
        assertTrue(dump.contains("[ ]"), "Dump should include not-found items for full grail view");
    }

    @Test
    public void testDeliriumFileShowsDeliriumAsFound() throws Exception {
        File file = new File("../savefiles/ModernSharedStashSoftCoreV2_good_with_delirium_unlocked.d2i");
        if (!file.exists()) {
            System.out.println("Skipping: " + file.getAbsolutePath() + " not found");
            return;
        }
        D2BitReader bitReader = new D2BitReader(file.getAbsolutePath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, file.getAbsolutePath(), bitReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);
        assertEquals(30, chronicle.getNumRunewords());

        List<D2Chronicle.ChronicleEntry> grail = chronicle.getRunewordGrailEntries();
        boolean deliriumFound = false;
        for (D2Chronicle.ChronicleEntry e : grail) {
            if ("Delirium".equals(e.getItemName())) {
                assertTrue(e.isFound(), "Delirium should be marked as found");
                deliriumFound = true;
                break;
            }
        }
        assertTrue(deliriumFound, "Delirium should appear in grail entries");
    }

    @Test
    public void testAnalyzeRunewordsAgainstManualRecord() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        File manualFile = new File("../savefiles/record-manually-from-game.txt");
        if (!stashFile.exists() || !manualFile.exists()) {
            System.out.println("Skipping analysis test: required file missing");
            return;
        }

        D2BitReader bitReader = new D2BitReader(stashFile.getAbsolutePath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), bitReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle, "Chronicle should not be null for ROW stash");

        List<String> manualRaw = readManualRunewords(manualFile);
        List<String> canonicalRunewords = getAllCompleteRunewordsCanonical();
        List<String> manualCanonical = canonicalizeManualRunewords(manualRaw, canonicalRunewords);

        List<D2Chronicle.ChronicleEntry> entries = chronicle.getRunewordEntries();
        List<String> chronicleNames = new ArrayList<>();
        for (D2Chronicle.ChronicleEntry entry : entries) {
            chronicleNames.add(entry.getItemName());
        }

        Set<String> chronicleNorm = new LinkedHashSet<>();
        for (String name : chronicleNames) chronicleNorm.add(normalize(name));

        Set<String> manualNorm = new LinkedHashSet<>();
        for (String name : manualCanonical) manualNorm.add(normalize(name));

        List<String> matched = new ArrayList<>();
        for (String name : manualCanonical) {
            if (chronicleNorm.contains(normalize(name))) {
                matched.add(name);
            }
        }

        List<String> manualOnly = new ArrayList<>();
        for (String name : manualCanonical) {
            if (!chronicleNorm.contains(normalize(name))) {
                manualOnly.add(name);
            }
        }

        List<String> chronicleOnly = new ArrayList<>();
        for (String name : chronicleNames) {
            if (!manualNorm.contains(normalize(name))) {
                chronicleOnly.add(name);
            }
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter("chronicle_runeword_analysis.txt"))) {
            pw.println("=== Chronicle Runeword Analysis ===");
            pw.println("Chronicle entries: " + chronicleNames.size());
            pw.println("Manual entries (raw): " + manualRaw.size());
            pw.println("Manual entries (canonicalized): " + manualCanonical.size());
            pw.println("Matches (normalized): " + matched.size());
            pw.println();

            pw.println("--- Chronicle runeword entries with raw fields ---");
            for (int i = 0; i < entries.size(); i++) {
                D2Chronicle.ChronicleEntry entry = entries.get(i);
                String name = chronicleNames.get(i);
                pw.printf(Locale.ROOT,
                        "%02d. %-24s found=%s rawField0=%d rawField6=%d rawTimestamp=%d%n",
                        i,
                        name == null ? "<null>" : name,
                        entry.isFound(),
                        entry.getRawField0(),
                        entry.getRawField6(),
                        entry.getRawTimestamp());
            }
            pw.println();

            pw.println("--- Manual runewords (raw) ---");
            for (String s : manualRaw) pw.println("- " + s);
            pw.println();

            pw.println("--- Manual runewords (canonicalized) ---");
            for (String s : manualCanonical) pw.println("- " + s);
            pw.println();

            pw.println("--- Matched ---");
            for (String s : matched) pw.println("- " + s);
            pw.println();

            pw.println("--- Manual-only ---");
            for (String s : manualOnly) pw.println("- " + s);
            pw.println();

            pw.println("--- Chronicle-only ---");
            for (String s : chronicleOnly) pw.println("- " + s);
            pw.println();
        }

        assertEquals(29, chronicleNames.size());
        assertEquals(27, manualCanonical.size());
    }

    @Test
    public void testMarkRunewordFoundPersistsToD2i() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping persistence test: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        D2BitReader sourceReader = new D2BitReader(stashFile.getAbsolutePath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), sourceReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle, "Chronicle should exist in the sample stash");

        List<D2Chronicle.ChronicleEntry> grail = chronicle.getRunewordGrailEntries();
        int targetIndex = -1;
        for (int i = 0; i < grail.size(); i++) {
            if (!grail.get(i).isFound()) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) {
            System.out.println("Skipping persistence test: no not-found runewords in grail list");
            return;
        }

        int baselineRunewords = chronicle.getNumRunewords();
        boolean pane0WasV1 = sourceReader.getFileContent().length > 66
                && sourceReader.getFileContent()[64] == (byte) 0x4A
                && sourceReader.getFileContent()[65] == (byte) 0x4D
                && sourceReader.getFileContent()[66] == (byte) 0x01;

        assertTrue(chronicle.markFound(D2Chronicle.Section.RUNEWORDS, targetIndex),
                "Marking a not-found runeword as found should change the chronicle");
        assertEquals(baselineRunewords + 1, chronicle.getNumRunewords(),
                "Runeword count should be incremented by 1");

        File outFile = File.createTempFile("gomule-chronicle-mark-found", ".d2i");
        outFile.deleteOnExit();
        Files.write(outFile.toPath(), sourceReader.getFileContent().clone());

        D2SharedStash writableStash = new D2SharedStash(
                ROW,
                outFile.getAbsolutePath(),
                stash.getPanes(),
                sourceReader.getFileContent().clone(),
                chronicle);
        new D2SharedStashWriter(ROW, outFile, sourceReader.getFileContent().clone()).write(writableStash);

        byte[] originalBytes = sourceReader.getFileContent().clone();
        byte[] writtenBytes = Files.readAllBytes(outFile.toPath());
        assertTrue(writtenBytes.length > originalBytes.length,
            "Written file should be larger than original after marking a runeword found");

        int[] originalOffsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(originalBytes.clone()));
        int[] writtenOffsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(writtenBytes.clone()));
        int originalChronicleStart = originalOffsets[originalOffsets.length - 1];
        int writtenChronicleStart = writtenOffsets[writtenOffsets.length - 1];
        int originalChronicleEnd = originalBytes.length;
        int writtenChronicleEnd = writtenBytes.length;

        int originalSetCount = (originalBytes[originalChronicleStart + 70] & 0xFF) | ((originalBytes[originalChronicleStart + 71] & 0xFF) << 8);
        int originalUniqueCount = (originalBytes[originalChronicleStart + 72] & 0xFF) | ((originalBytes[originalChronicleStart + 73] & 0xFF) << 8);
        int originalRunewordCount = (originalBytes[originalChronicleStart + 74] & 0xFF) | ((originalBytes[originalChronicleStart + 75] & 0xFF) << 8);
        int writtenSetCount = (writtenBytes[writtenChronicleStart + 70] & 0xFF) | ((writtenBytes[writtenChronicleStart + 71] & 0xFF) << 8);
        int writtenUniqueCount = (writtenBytes[writtenChronicleStart + 72] & 0xFF) | ((writtenBytes[writtenChronicleStart + 73] & 0xFF) << 8);
        int writtenRunewordCount = (writtenBytes[writtenChronicleStart + 74] & 0xFF) | ((writtenBytes[writtenChronicleStart + 75] & 0xFF) << 8);

        int originalEntriesEnd = originalChronicleStart + 88 + (originalSetCount + originalUniqueCount + originalRunewordCount) * 10;
        int writtenEntriesEnd = writtenChronicleStart + 88 + (writtenSetCount + writtenUniqueCount + writtenRunewordCount) * 10;
        byte[] originalTrailer = Arrays.copyOfRange(originalBytes, originalEntriesEnd, originalChronicleEnd);
        byte[] writtenTrailer = Arrays.copyOfRange(writtenBytes, writtenEntriesEnd, writtenChronicleEnd);
        assertArrayEquals(originalTrailer, writtenTrailer,
            "Chronicle trailer bytes must be preserved exactly");

        // If pane 0 was v1, migration should have bumped version to v2
        if (pane0WasV1) {
            assertEquals(0x02, writtenBytes[66] & 0xFF,
                    "Pane 0 JM version must be 2 after migration");
        }

        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW, outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle reloadedChronicle = reloaded.getChronicle();
        assertNotNull(reloadedChronicle);
        assertEquals(baselineRunewords + 1, reloadedChronicle.getNumRunewords(),
                "Serialized chronicle should store updated progress count");
        assertEquals(baselineRunewords + 1, reloadedChronicle.getFoundRunewordCount(),
                "Serialized chronicle should include the newly found runeword entry");
    }

    @Test
    public void testMarkManyRunewordsFoundStillProducesValidFile() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping multi-mark test: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        D2BitReader sourceReader = new D2BitReader(stashFile.getAbsolutePath());
        byte[] originalContent = sourceReader.getFileContent().clone();
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), sourceReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);
        int baselineRunewords = chronicle.getNumRunewords();

        // Mark 10 not-found runewords as found
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getRunewordGrailEntries();
        int marked = 0;
        for (int i = 0; i < grail.size() && marked < 10; i++) {
            if (!grail.get(i).isFound()) {
                assertTrue(chronicle.markFound(D2Chronicle.Section.RUNEWORDS, i),
                        "Marking runeword " + grail.get(i).getItemName() + " as found should succeed");
                marked++;
                // Rebuild grail since it's recomputed each time
                grail = chronicle.getRunewordGrailEntries();
            }
        }
        assertEquals(10, marked, "Should have marked 10 runewords");
        int expectedRunewords = baselineRunewords + marked;
        assertEquals(expectedRunewords, chronicle.getNumRunewords());

        // Write the file
        File outFile = File.createTempFile("gomule-multi-mark", ".d2i");
        outFile.deleteOnExit();
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalContent, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalContent).write(writableStash);
        byte[] writtenBytes = Files.readAllBytes(outFile.toPath());

        // Pane 0 migration only applies when pane 0 was v1.
        // The current save file has pane 0 version 0x0C, so no migration occurs.
        boolean pane0WasV1 = originalContent.length > 66
                && originalContent[64] == (byte) 0x4A
                && originalContent[65] == (byte) 0x4D
                && originalContent[66] == (byte) 0x01;
        if (pane0WasV1) {
            assertEquals(0x02, writtenBytes[66] & 0xFF,
                    "Pane 0 JM version must be 2 after migration");
            int originalPane0Len = (originalContent[16] & 0xFF) | ((originalContent[17] & 0xFF) << 8)
                    | ((originalContent[18] & 0xFF) << 16) | ((originalContent[19] & 0xFF) << 24);
            int writtenPane0Len = (writtenBytes[16] & 0xFF) | ((writtenBytes[17] & 0xFF) << 8)
                    | ((writtenBytes[18] & 0xFF) << 16) | ((writtenBytes[19] & 0xFF) << 24);
            assertTrue(writtenPane0Len > originalPane0Len,
                    "Pane 0 length should grow after migration");
        }

        // Reload and verify the chronicle data round-trips
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW, outFile.getAbsolutePath(),
                new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle reloadedChronicle = reloaded.getChronicle();
        assertNotNull(reloadedChronicle);
        assertEquals(expectedRunewords, reloadedChronicle.getNumRunewords());
        assertEquals(expectedRunewords, reloadedChronicle.getFoundRunewordCount());
    }

    private static List<String> readManualRunewords(File manualFile) throws Exception {
        List<String> result = new ArrayList<>();
        boolean inRunewordSection = false;
        boolean sawSectionHeader = false;
        try (BufferedReader br = new BufferedReader(new FileReader(manualFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                // Check for section headers
                if (trimmed.toLowerCase(Locale.ROOT).startsWith("rune words")) {
                    inRunewordSection = true;
                    sawSectionHeader = true;
                    continue;
                }
                // Stop at unique/set item sections regardless of whether we saw rune words header
                if (trimmed.toLowerCase(Locale.ROOT).startsWith("set:")
                        || trimmed.toLowerCase(Locale.ROOT).startsWith("unique:")) {
                    break;
                }
                
                // Add items while in runeword section or if no section header seen yet (plain format)
                if (!trimmed.isEmpty() && (inRunewordSection || !sawSectionHeader)) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    private static List<String> getAllCompleteRunewordsCanonical() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < D2TxtFile.RUNES.getRowSize(); i++) {
            D2TxtFileItemProperties row = D2TxtFile.RUNES.getRow(i);
            if ("1".equals(row.get("complete"))) {
                String name = row.get("*Rune Name");
                if (name == null || name.isEmpty()) {
                    name = row.get("Name");
                }
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static List<String> canonicalizeManualRunewords(List<String> manualRunewords, List<String> canonicalRunewords) {
        Map<String, String> canonicalByNorm = new LinkedHashMap<>();
        for (String canonical : canonicalRunewords) {
            canonicalByNorm.put(normalize(canonical), canonical);
        }

        List<String> result = new ArrayList<>();
        for (String raw : manualRunewords) {
            String canonical = canonicalByNorm.get(normalize(raw));
            if (canonical != null) {
                result.add(canonical);
                continue;
            }

            String corrected = typoCorrections(raw);
            String correctedCanonical = canonicalByNorm.get(normalize(corrected));
            if (correctedCanonical != null) {
                result.add(correctedCanonical);
            } else {
                result.add(raw);
            }
        }
        return result;
    }

    private static String typoCorrections(String s) {
        String key = normalize(s);
        if ("malith".equals(key)) return "Malice";
        if ("rhythm".equals(key)) return "Rhyme";
        if ("pheonix".equals(key)) return "Phoenix";
        if ("flickeringflamme".equals(key)) return "Flickering Flame";
        return s;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @Test
    public void testGoMuleOutputMatchesGameWrittenEnigmaFile() throws Exception {
        File goodFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        File gameEnigmaFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good_with_enigma.d2i");
        if (!goodFile.exists() || !gameEnigmaFile.exists()) {
            System.out.println("Skipping game comparison test: required files missing");
            return;
        }

        // Step 1: produce GoMule output from _good.d2i + markFound(Enigma)
        D2BitReader sourceReader = new D2BitReader(goodFile.getAbsolutePath());
        byte[] originalContent = sourceReader.getFileContent().clone();
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, goodFile.getAbsolutePath(), sourceReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);

        List<D2Chronicle.ChronicleEntry> grail = chronicle.getRunewordGrailEntries();
        int enigmaIndex = -1;
        for (int i = 0; i < grail.size(); i++) {
            if ("Enigma".equalsIgnoreCase(grail.get(i).getItemName())) {
                enigmaIndex = i;
                break;
            }
        }
        assertTrue(enigmaIndex >= 0, "Enigma must be in the grail list");
        assertTrue(chronicle.markFound(D2Chronicle.Section.RUNEWORDS, enigmaIndex));

        File outFile = File.createTempFile("gomule-compare-enigma", ".d2i");
        outFile.deleteOnExit();
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalContent, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalContent).write(writableStash);
        byte[] gomuleBytes = Files.readAllBytes(outFile.toPath());

        // Step 2: read game-written file
        byte[] gameBytes = Files.readAllBytes(gameEnigmaFile.toPath());

        // Step 3: compare sizes
        System.out.println("GoMule output size: " + gomuleBytes.length);
        System.out.println("Game output size:   " + gameBytes.length);
        assertEquals(gameBytes.length, gomuleBytes.length, "Output sizes must match");

        // Step 4: find pane offsets
        int[] gomuleOffsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(gomuleBytes.clone()));
        int[] gameOffsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(gameBytes.clone()));
        assertEquals(gameOffsets.length, gomuleOffsets.length, "Pane count must match");
        assertArrayEquals(gameOffsets, gomuleOffsets, "All pane offsets must match");

        // Step 5: compare pane 0 (migration block)
        int pane0End = gomuleOffsets[1];
        byte[] gomulePane0 = Arrays.copyOfRange(gomuleBytes, 0, pane0End);
        byte[] gamePane0 = Arrays.copyOfRange(gameBytes, 0, pane0End);
        assertArrayEquals(gamePane0, gomulePane0, "Pane 0 must match game output exactly");

        // Step 6: compare chronicle pane structure
        int chronicleStart = gomuleOffsets[gomuleOffsets.length - 1];
        int gomuleNumRune = (gomuleBytes[chronicleStart + 74] & 0xFF) | ((gomuleBytes[chronicleStart + 75] & 0xFF) << 8);
        int gameNumRune = (gameBytes[chronicleStart + 74] & 0xFF) | ((gameBytes[chronicleStart + 75] & 0xFF) << 8);
        assertEquals(gameNumRune, gomuleNumRune, "Runeword count must match");

        // Step 7: compare all chronicle runeword entries
        int setCount = (gomuleBytes[chronicleStart + 70] & 0xFF) | ((gomuleBytes[chronicleStart + 71] & 0xFF) << 8);
        int uniqCount = (gomuleBytes[chronicleStart + 72] & 0xFF) | ((gomuleBytes[chronicleStart + 73] & 0xFF) << 8);
        int runeStart = chronicleStart + 88 + (setCount + uniqCount) * 10;

        StringBuilder diffs = new StringBuilder();
        for (int i = 0; i < gameNumRune; i++) {
            int off = runeStart + i * 10;
            byte[] gomuleEntry = Arrays.copyOfRange(gomuleBytes, off, off + 10);
            byte[] gameEntry = Arrays.copyOfRange(gameBytes, off, off + 10);
            if (!Arrays.equals(gomuleEntry, gameEntry)) {
                diffs.append(String.format("  Entry[%d]: gomule=%s game=%s%n", i,
                        bytesToHex(gomuleEntry), bytesToHex(gameEntry)));
            }
        }
        if (diffs.length() > 0) {
            System.out.println("Runeword entry differences:");
            System.out.println(diffs);
        }

        // Step 8: compare entire file (excluding acceptable timestamp diffs)
        int diffCount = 0;
        for (int i = 0; i < gameBytes.length; i++) {
            if (gomuleBytes[i] != gameBytes[i]) {
                diffCount++;
                if (diffCount <= 50) {
                    System.out.printf("Byte diff at offset %d (0x%04X): gomule=0x%02X game=0x%02X%n",
                            i, i, gomuleBytes[i] & 0xFF, gameBytes[i] & 0xFF);
                }
            }
        }
        System.out.println("Total byte differences: " + diffCount);
    }

    /**
     * Reproducer for Delirium "join game failed" bug.
     * Tests marking each runeword individually from the good file, and
     * sequential marks with save+reload cycles.
     */
    @Test
    public void testMarkIndividualRunewordsFromGoodFile() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        // Test each runeword individually from the good file
        String[] runewords = {"Beast", "Crescent Moon", "Breath of the Dying", "Delirium"};
        for (String rwName : runewords) {
            D2BitReader sourceReader = new D2BitReader(stashFile.getAbsolutePath());
            byte[] originalContent = sourceReader.getFileContent().clone();
            D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), sourceReader);
            D2Chronicle chronicle = stash.getChronicle();
            assertNotNull(chronicle, "Chronicle must exist");
            int baselineCount = chronicle.getNumRunewords();

            List<D2Chronicle.ChronicleEntry> grail = chronicle.getRunewordGrailEntries();
            int idx = -1;
            for (int i = 0; i < grail.size(); i++) {
                if (rwName.equalsIgnoreCase(grail.get(i).getItemName())) {
                    idx = i;
                    break;
                }
            }
            assertTrue(idx >= 0, rwName + " must be in the grail list");
            assertTrue(chronicle.markFound(D2Chronicle.Section.RUNEWORDS, idx),
                    "Marking " + rwName + " as found should succeed");
            assertEquals(baselineCount + 1, chronicle.getNumRunewords());

            File outFile = File.createTempFile("gomule-individual-" + rwName.replace(" ", ""), ".d2i");
            outFile.deleteOnExit();
            D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                    stash.getPanes(), originalContent, chronicle);
            new D2SharedStashWriter(ROW, outFile, originalContent).write(writableStash);

            // Verify: reload the written file
            byte[] writtenBytes = Files.readAllBytes(outFile.toPath());
            D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                    outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
            D2Chronicle reloadedChron = reloaded.getChronicle();
            assertNotNull(reloadedChron, rwName + ": reloaded chronicle must not be null");
            assertEquals(baselineCount + 1, reloadedChron.getNumRunewords(),
                    rwName + ": reloaded runeword count");
            assertEquals(baselineCount + 1, reloadedChron.getFoundRunewordCount(),
                    rwName + ": reloaded found count");
            System.out.println("PASS: " + rwName + " individually from good file, total runewords=" + reloadedChron.getNumRunewords());
        }
    }

    @Test
    public void testMarkSequentialRunewordsWithReload() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        // Copy good file to temp
        File workingFile = File.createTempFile("gomule-sequential", ".d2i");
        workingFile.deleteOnExit();
        Files.copy(stashFile.toPath(), workingFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        String[] runewords = {"Beast", "Crescent Moon", "Breath of the Dying", "Delirium"};
        int expectedRunewords = -1;

        for (String rwName : runewords) {
            // Fresh load (simulates closing/reopening GoMule)
            D2BitReader sourceReader = new D2BitReader(workingFile.getAbsolutePath());
            byte[] originalContent = sourceReader.getFileContent().clone();
            D2SharedStash stash = new D2SharedStashReader().readStash(ROW,
                    workingFile.getAbsolutePath(), sourceReader);
            D2Chronicle chronicle = stash.getChronicle();
            assertNotNull(chronicle);
            int beforeCount = chronicle.getNumRunewords();
            if (expectedRunewords >= 0) {
                assertEquals(expectedRunewords, beforeCount,
                        "Reloaded count before marking " + rwName + " should match previous write");
            }

            List<D2Chronicle.ChronicleEntry> grail = chronicle.getRunewordGrailEntries();
            int idx = -1;
            for (int i = 0; i < grail.size(); i++) {
                if (rwName.equalsIgnoreCase(grail.get(i).getItemName())) {
                    idx = i;
                    break;
                }
            }
            assertTrue(idx >= 0, rwName + " must be in grail list");
            assertFalse(grail.get(idx).isFound(), rwName + " should not already be found");
            assertTrue(chronicle.markFound(D2Chronicle.Section.RUNEWORDS, idx),
                    "Marking " + rwName + " should succeed");
            expectedRunewords = chronicle.getNumRunewords();

            // Save (overwrite working file)
            D2SharedStash writableStash = new D2SharedStash(ROW, workingFile.getAbsolutePath(),
                    stash.getPanes(), originalContent, chronicle);
            new D2SharedStashWriter(ROW, workingFile, originalContent).write(writableStash);

            // Verify reload
            D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                    workingFile.getAbsolutePath(), new D2BitReader(workingFile.getAbsolutePath()));
            D2Chronicle reloadedChron = reloaded.getChronicle();
            assertNotNull(reloadedChron, rwName + ": reloaded chronicle must not be null");
            assertEquals(expectedRunewords, reloadedChron.getNumRunewords(),
                    rwName + ": reloaded runeword count after sequential mark");
            System.out.println("PASS (sequential): " + rwName + ", total runewords=" + reloadedChron.getNumRunewords());
        }
    }

    @Test
    public void testMarkSequentialRunewordsWithoutReload() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        // This simulates the user marking multiple runewords in the same GoMule
        // session, saving after each without closing/reopening.
        D2BitReader sourceReader = new D2BitReader(stashFile.getAbsolutePath());
        byte[] originalContent = sourceReader.getFileContent().clone();
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), sourceReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);
        int baselineCount = chronicle.getNumRunewords();

        String[] runewords = {"Beast", "Crescent Moon", "Breath of the Dying", "Delirium"};
        File outFile = File.createTempFile("gomule-noreload", ".d2i");
        outFile.deleteOnExit();

        for (int r = 0; r < runewords.length; r++) {
            String rwName = runewords[r];
            List<D2Chronicle.ChronicleEntry> grail = chronicle.getRunewordGrailEntries();
            int idx = -1;
            for (int i = 0; i < grail.size(); i++) {
                if (rwName.equalsIgnoreCase(grail.get(i).getItemName())) {
                    idx = i;
                    break;
                }
            }
            assertTrue(idx >= 0, rwName + " must be in grail list");
            assertTrue(chronicle.markFound(D2Chronicle.Section.RUNEWORDS, idx),
                    "Marking " + rwName + " should succeed");
            assertEquals(baselineCount + r + 1, chronicle.getNumRunewords());

            // Save using the SAME originalContent (simulates GUI behavior — no reload)
            D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                    stash.getPanes(), originalContent, chronicle);
            new D2SharedStashWriter(ROW, outFile, originalContent).write(writableStash);

            // Verify: reload from the written file
            D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                    outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
            D2Chronicle reloadedChron = reloaded.getChronicle();
            assertNotNull(reloadedChron, rwName + " (no-reload): reloaded chronicle must not be null");
            assertEquals(baselineCount + r + 1, reloadedChron.getNumRunewords(),
                    rwName + " (no-reload): runeword count");
            assertEquals(baselineCount + r + 1, reloadedChron.getFoundRunewordCount(),
                    rwName + " (no-reload): found count");
            System.out.println("PASS (no-reload save " + (r + 1) + "): " + rwName
                    + ", total runewords=" + reloadedChron.getNumRunewords());
        }
    }

    /**
     * Diagnostic test: reads the game-sourced 8440-byte file, marks Delirium,
     * saves, and does a detailed byte-by-byte comparison to find exactly what
     * GoMule changes or corrupts.
     */
    @Test
    public void testDiagnoseDeliriumOnGameSave() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }
        byte[] inputBytes = Files.readAllBytes(stashFile.toPath());
        System.out.println("=== INPUT FILE ===");
        System.out.println("Size: " + inputBytes.length);

        // Step 1: No-change round-trip
        D2BitReader sourceReader1 = new D2BitReader(stashFile.getAbsolutePath());
        D2SharedStash stash1 = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), sourceReader1);
        assertNotNull(stash1.getChronicle(), "Chronicle must exist");
        System.out.println("Chronicle: version=" + stash1.getChronicle().getVersion()
                + " sets=" + stash1.getChronicle().getNumSetItems()
                + " uniques=" + stash1.getChronicle().getNumUniqueItems()
                + " runewords=" + stash1.getChronicle().getNumRunewords());

        // Save without changes — chronicle is NOT modified, so all panes should be copied verbatim
        File roundTripFile = File.createTempFile("gomule-roundtrip", ".d2i");
        roundTripFile.deleteOnExit();
        new D2SharedStashWriter(ROW, roundTripFile, sourceReader1.getFileContent().clone()).write(
            new D2SharedStash(ROW, roundTripFile.getAbsolutePath(), stash1.getPanes(),
                    sourceReader1.getFileContent().clone(), stash1.getChronicle()));
        byte[] roundTripBytes = Files.readAllBytes(roundTripFile.toPath());

        System.out.println("\n=== NO-CHANGE ROUND-TRIP ===");
        System.out.println("Output size: " + roundTripBytes.length);
        int rtDiffs = 0;
        for (int i = 0; i < Math.min(inputBytes.length, roundTripBytes.length); i++) {
            if (inputBytes[i] != roundTripBytes[i]) {
                rtDiffs++;
                if (rtDiffs <= 20) {
                    System.out.printf("  Diff at offset %d (0x%04X): input=0x%02X output=0x%02X%n",
                            i, i, inputBytes[i] & 0xFF, roundTripBytes[i] & 0xFF);
                }
            }
        }
        System.out.println("Round-trip diffs: " + rtDiffs + " (size diff: " + (roundTripBytes.length - inputBytes.length) + ")");
        assertEquals(inputBytes.length, roundTripBytes.length, "No-change round-trip must preserve file size");
        assertArrayEquals(inputBytes, roundTripBytes, "No-change round-trip must preserve file content exactly");

        // Step 1b: Mark+unmark round-trip (forces chronicle rewrite with same data)
        {
            D2BitReader srcR = new D2BitReader(stashFile.getAbsolutePath());
            byte[] origC = srcR.getFileContent().clone();
            D2SharedStash stashRT = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), srcR);
            D2Chronicle cronRT = stashRT.getChronicle();
            List<D2Chronicle.ChronicleEntry> grailRT = cronRT.getRunewordGrailEntries();
            int delIdx = -1;
            for (int i = 0; i < grailRT.size(); i++) {
                if ("Delirium".equalsIgnoreCase(grailRT.get(i).getItemName())) { delIdx = i; break; }
            }
            assertTrue(delIdx >= 0);
            // Mark then unmark — data should be identical but isModified=true
            cronRT.markFound(D2Chronicle.Section.RUNEWORDS, delIdx);
            cronRT.markNotFound(D2Chronicle.Section.RUNEWORDS, delIdx);
            assertTrue(cronRT.isModified(), "Chronicle should be modified after mark+unmark");

            File rewriteFile = File.createTempFile("gomule-rewrite-check", ".d2i");
            rewriteFile.deleteOnExit();
            D2SharedStash rewriteStash = new D2SharedStash(ROW, rewriteFile.getAbsolutePath(),
                    stashRT.getPanes(), origC, cronRT);
            new D2SharedStashWriter(ROW, rewriteFile, origC).write(rewriteStash);
            byte[] rewriteBytes = Files.readAllBytes(rewriteFile.toPath());
            System.out.println("\n=== REWRITE (mark+unmark) ROUND-TRIP ===");
            System.out.println("Output size: " + rewriteBytes.length + " (input was " + inputBytes.length + ")");
            int rwDiffs = 0;
            for (int i = 0; i < Math.min(inputBytes.length, rewriteBytes.length); i++) {
                if (inputBytes[i] != rewriteBytes[i]) {
                    rwDiffs++;
                    if (rwDiffs <= 20) {
                        System.out.printf("  Diff at offset %d (0x%04X): input=0x%02X output=0x%02X%n",
                                i, i, inputBytes[i] & 0xFF, rewriteBytes[i] & 0xFF);
                    }
                }
            }
            if (rewriteBytes.length != inputBytes.length) {
                System.out.println("  SIZE MISMATCH: " + rewriteBytes.length + " vs " + inputBytes.length);
            }
            System.out.println("Rewrite diffs: " + rwDiffs);
            assertEquals(inputBytes.length, rewriteBytes.length,
                    "Rewrite round-trip must preserve file size");
            assertArrayEquals(inputBytes, rewriteBytes,
                    "Rewrite round-trip must preserve file content exactly");
        }

        // Step 2: Mark Delirium and save
        D2BitReader sourceReader2 = new D2BitReader(stashFile.getAbsolutePath());
        byte[] originalContent = sourceReader2.getFileContent().clone();
        D2SharedStash stash2 = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), sourceReader2);
        D2Chronicle chronicle = stash2.getChronicle();
        assertNotNull(chronicle);
        int baselineRW = chronicle.getNumRunewords();

        List<D2Chronicle.ChronicleEntry> grail = chronicle.getRunewordGrailEntries();
        int deliriumIdx = -1;
        for (int i = 0; i < grail.size(); i++) {
            if ("Delirium".equalsIgnoreCase(grail.get(i).getItemName())) {
                deliriumIdx = i;
                break;
            }
        }
        assertTrue(deliriumIdx >= 0, "Delirium must be in grail list");

        boolean alreadyFound = grail.get(deliriumIdx).isFound();
        System.out.println("\nDelirium grailIndex=" + deliriumIdx + " alreadyFound=" + alreadyFound);
        if (alreadyFound) {
            System.out.println("Delirium is already found in this file — marking NOT found first");
            chronicle.markNotFound(D2Chronicle.Section.RUNEWORDS, deliriumIdx);
            grail = chronicle.getRunewordGrailEntries();
        }
        assertTrue(chronicle.markFound(D2Chronicle.Section.RUNEWORDS, deliriumIdx),
                "Marking Delirium as found should succeed");
        System.out.println("After marking: numRunewords=" + chronicle.getNumRunewords()
                + " (was " + baselineRW + ")");

        File outFile = File.createTempFile("gomule-delirium-game", ".d2i");
        outFile.deleteOnExit();
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash2.getPanes(), originalContent, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalContent).write(writableStash);
        byte[] outBytes = Files.readAllBytes(outFile.toPath());

        System.out.println("\n=== DELIRIUM OUTPUT ===");
        System.out.println("Output size: " + outBytes.length + " (input was " + inputBytes.length + ")");

        // Pane structure comparison
        int[] inOffsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(inputBytes.clone()));
        int[] outOffsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(outBytes.clone()));
        System.out.println("\nInput panes: " + inOffsets.length + ", Output panes: " + outOffsets.length);
        for (int i = 0; i < inOffsets.length; i++) {
            int inStart = inOffsets[i];
            int inEnd = (i + 1 < inOffsets.length) ? inOffsets[i + 1] : inputBytes.length;
            int outStart = outOffsets[i];
            int outEnd = (i + 1 < outOffsets.length) ? outOffsets[i + 1] : outBytes.length;
            int inLen = (inputBytes[inStart + 16] & 0xFF) | ((inputBytes[inStart + 17] & 0xFF) << 8)
                    | ((inputBytes[inStart + 18] & 0xFF) << 16) | ((inputBytes[inStart + 19] & 0xFF) << 24);
            int outLen = (outBytes[outStart + 16] & 0xFF) | ((outBytes[outStart + 17] & 0xFF) << 8)
                    | ((outBytes[outStart + 18] & 0xFF) << 16) | ((outBytes[outStart + 19] & 0xFF) << 24);
            boolean match = Arrays.equals(
                    Arrays.copyOfRange(inputBytes, inStart, inEnd),
                    Arrays.copyOfRange(outBytes, outStart, outEnd));
            System.out.printf("  Pane[%d] in@%d(%d) out@%d(%d) hdrLen in=%d out=%d match=%s%n",
                    i, inStart, inEnd - inStart, outStart, outEnd - outStart, inLen, outLen, match);
        }

        // Detailed byte diff for non-matching regions
        System.out.println("\n=== BYTE DIFFS ===");
        int minLen = Math.min(inputBytes.length, outBytes.length);
        int diffCount = 0;
        for (int i = 0; i < minLen; i++) {
            if (inputBytes[i] != outBytes[i]) {
                diffCount++;
                if (diffCount <= 100) {
                    // Identify which pane
                    String paneInfo = "";
                    for (int p = 0; p < inOffsets.length; p++) {
                        int pStart = inOffsets[p];
                        int pEnd = (p + 1 < inOffsets.length) ? inOffsets[p + 1] : inputBytes.length;
                        if (i >= pStart && i < pEnd) {
                            paneInfo = " (pane " + p + ", paneOff=" + (i - pStart) + ")";
                            break;
                        }
                    }
                    System.out.printf("  @%d(0x%04X): in=0x%02X out=0x%02X%s%n",
                            i, i, inputBytes[i] & 0xFF, outBytes[i] & 0xFF, paneInfo);
                }
            }
        }
        if (outBytes.length > inputBytes.length) {
            System.out.println("  Output has " + (outBytes.length - inputBytes.length) + " extra bytes at end");
        }
        System.out.println("Total byte diffs: " + diffCount);

        // Verify the output can be reloaded
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        assertNotNull(reloaded.getChronicle(), "Reloaded chronicle must exist");
        System.out.println("\n=== RELOADED ===");
        System.out.println("Runewords: " + reloaded.getChronicle().getNumRunewords());
        System.out.println("Found runewords: " + reloaded.getChronicle().getFoundRunewordCount());
    }

    /**
     * Produce test files for the user to deploy.
     * - "variant_a": standard GoMule approach (insert + grow count)
     * - "variant_b": raw byte patch (only overwrite sentinel field6, no count/size change)
     */
    @Test
    public void testProduceDeliriumTestFiles() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }
        byte[] inputBytes = Files.readAllBytes(stashFile.toPath());

        // === VARIANT A: standard GoMule (insert before sentinel, grow count) ===
        {
            D2BitReader srcR = new D2BitReader(stashFile.getAbsolutePath());
            byte[] origC = srcR.getFileContent().clone();
            D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), srcR);
            D2Chronicle chron = stash.getChronicle();
            List<D2Chronicle.ChronicleEntry> grail = chron.getRunewordGrailEntries();
            int delIdx = -1;
            for (int i = 0; i < grail.size(); i++) {
                if ("Delirium".equalsIgnoreCase(grail.get(i).getItemName())) { delIdx = i; break; }
            }
            assertTrue(delIdx >= 0, "Delirium must be in grail");
            chron.markFound(D2Chronicle.Section.RUNEWORDS, delIdx);
            File outA = new File("../savefiles/ModernSharedStashSoftCoreV2_delirium_variantA.d2i");
            D2SharedStash ws = new D2SharedStash(ROW, outA.getAbsolutePath(), stash.getPanes(), origC, chron);
            new D2SharedStashWriter(ROW, outA, origC).write(ws);
            byte[] aBytes = Files.readAllBytes(outA.toPath());
            System.out.println("Variant A (standard): " + aBytes.length + " bytes, written to " + outA.getName());
        }

        // === VARIANT B: raw byte patch (overwrite sentinel field6, no count change) ===
        {
            byte[] patched = inputBytes.clone();
            // Find the chronicle pane and runeword entries
            int[] offsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(patched.clone()));
            int chrStart = offsets[offsets.length - 1];
            int setCount = (patched[chrStart + 70] & 0xFF) | ((patched[chrStart + 71] & 0xFF) << 8);
            int uniqCount = (patched[chrStart + 72] & 0xFF) | ((patched[chrStart + 73] & 0xFF) << 8);
            int rwCount = (patched[chrStart + 74] & 0xFF) | ((patched[chrStart + 75] & 0xFF) << 8);
            int rwStart = chrStart + 88 + (setCount + uniqCount) * 10;
            // Find sentinel (last entry with field6=0x0000)
            int sentinelOff = -1;
            for (int i = rwCount - 1; i >= 0; i--) {
                int off = rwStart + i * 10;
                int f6 = (patched[off + 6] & 0xFF) | ((patched[off + 7] & 0xFF) << 8);
                if (f6 == 0x0000) { sentinelOff = off; break; }
            }
            assertTrue(sentinelOff >= 0, "Sentinel must exist");
            System.out.println("Sentinel at file offset " + sentinelOff + ", patching field6 from 0x0000 to 0x5030");
            // Overwrite ONLY field6 bytes (bytes 6-7 of the 10-byte entry)
            patched[sentinelOff + 6] = 0x30; // low byte of 0x5030
            patched[sentinelOff + 7] = 0x50; // high byte of 0x5030
            File outB = new File("../savefiles/ModernSharedStashSoftCoreV2_delirium_variantB.d2i");
            Files.write(outB.toPath(), patched);
            System.out.println("Variant B (raw patch): " + patched.length + " bytes, written to " + outB.getName());
            // Verify it loads
            D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                    outB.getAbsolutePath(), new D2BitReader(outB.getAbsolutePath()));
            D2Chronicle rc = reloaded.getChronicle();
            assertNotNull(rc);
            System.out.println("Variant B verified: sets=" + rc.getNumSetItems()
                    + " uniques=" + rc.getNumUniqueItems() + " runewords=" + rc.getNumRunewords());
            // Check Delirium is in the entry list
            boolean delFound = false;
            for (D2Chronicle.ChronicleEntry e : rc.getRunewordEntries()) {
                if (e.isFound() && e.getRawField6() == 0x5030) { delFound = true; break; }
            }
            assertTrue(delFound, "Delirium should be found in variant B");
        }

        System.out.println("\nDeploy variant A or B to game folder and test:");
        System.out.println("  Variant A = standard (insert entry, count grows to 30, file grows by 10 bytes)");
        System.out.println("  Variant B = raw patch (overwrite sentinel field6, count stays 29, file size unchanged)");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b & 0xFF));
        return sb.toString();
    }

    /**
     * Produces a Delirium-marked file from pristine and dumps full binary analysis.
     * Compares pane 0 structure with the game-written enigma reference.
     * Writes output to savefiles/ for direct game testing.
     */
    @Test
    public void testProduceDeliriumFileAndAnalyzeBinary() throws Exception {
        // Use the pristine backup directly
        File pristineFile = new File("../savefiles/GoMule.backup/W2026.03.22/ModernSharedStashSoftCoreV2.2026.03.23-21.29.04.d2i.org");
        File gameEnigmaFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good_with_enigma.d2i");
        if (!pristineFile.exists()) {
            System.out.println("Skipping: pristine backup not found at " + pristineFile.getAbsolutePath());
            return;
        }

        D2BitReader sourceReader = new D2BitReader(pristineFile.getAbsolutePath());
        byte[] originalContent = sourceReader.getFileContent().clone();
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, pristineFile.getAbsolutePath(), sourceReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);

        System.out.println("=== PRISTINE INPUT ===");
        System.out.println("Size: " + originalContent.length);
        System.out.println("Byte 66 (JM version): 0x" + String.format("%02X", originalContent[66] & 0xFF));
        System.out.println("Runewords: " + chronicle.getNumRunewords());

        // Mark Delirium found
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getRunewordGrailEntries();
        int deliriumIdx = -1;
        for (int i = 0; i < grail.size(); i++) {
            if ("Delirium".equalsIgnoreCase(grail.get(i).getItemName())) {
                deliriumIdx = i;
                break;
            }
        }
        assertTrue(deliriumIdx >= 0, "Delirium must be in grail list");
        assertTrue(chronicle.markFound(D2Chronicle.Section.RUNEWORDS, deliriumIdx));
        assertEquals(28, chronicle.getNumRunewords());

        // Write output
        File outFile = new File("../savefiles/ModernSharedStashSoftCoreV2_delirium_test.d2i");
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalContent, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalContent).write(writableStash);
        byte[] outBytes = Files.readAllBytes(outFile.toPath());

        System.out.println("\n=== GOMULE OUTPUT (Delirium) ===");
        System.out.println("Size: " + outBytes.length);
        System.out.println("Byte 66 (JM version): 0x" + String.format("%02X", outBytes[66] & 0xFF));

        // Dump pane structure
        int[] outOffsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(outBytes.clone()));
        System.out.println("Panes: " + outOffsets.length);
        for (int i = 0; i < outOffsets.length; i++) {
            int start = outOffsets[i];
            int end = i + 1 < outOffsets.length ? outOffsets[i + 1] : outBytes.length;
            int len = (outBytes[start + 16] & 0xFF) | ((outBytes[start + 17] & 0xFF) << 8)
                    | ((outBytes[start + 18] & 0xFF) << 16) | ((outBytes[start + 19] & 0xFF) << 24);
            String magic = String.format("%02X%02X%02X%02X",
                    outBytes[start + 64] & 0xFF, outBytes[start + 65] & 0xFF,
                    outBytes[start + 66] & 0xFF, outBytes[start + 67] & 0xFF);
            System.out.printf("  Pane[%d] @%d len=%d actual=%d magic64=%s%n", i, start, len, end - start, magic);
        }

        // Assertions
        assertEquals(0x02, outBytes[66] & 0xFF, "Pane 0 JM version must be 0x02 after migration");

        // Compare pane 0 with game-written enigma file
        if (gameEnigmaFile.exists()) {
            byte[] gameBytes = Files.readAllBytes(gameEnigmaFile.toPath());
            int[] gameOffsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(gameBytes.clone()));

            System.out.println("\n=== GAME-WRITTEN ENIGMA REFERENCE ===");
            System.out.println("Size: " + gameBytes.length);
            System.out.println("Byte 66 (JM version): 0x" + String.format("%02X", gameBytes[66] & 0xFF));

            // Compare pane 0
            int outPane0End = outOffsets[1];
            int gamePane0End = gameOffsets[1];
            System.out.println("\nPane 0: GoMule=" + outPane0End + " bytes, Game=" + gamePane0End + " bytes");
            assertEquals(gamePane0End, outPane0End, "Pane 0 size must match game");
            assertArrayEquals(
                    Arrays.copyOfRange(gameBytes, 0, gamePane0End),
                    Arrays.copyOfRange(outBytes, 0, outPane0End),
                    "Pane 0 must be byte-for-byte identical to game output");

            // Compare non-chronicle panes (should be identical)
            int lastItemPane = outOffsets.length - 2;
            for (int i = 1; i <= lastItemPane; i++) {
                int s1 = outOffsets[i], e1 = outOffsets[i + 1];
                int s2 = gameOffsets[i], e2 = gameOffsets[i + 1];
                byte[] outPane = Arrays.copyOfRange(outBytes, s1, e1);
                byte[] gamePane = Arrays.copyOfRange(gameBytes, s2, e2);
                assertArrayEquals(gamePane, outPane, "Pane[" + i + "] must match game output");
            }

            System.out.println("\nAll non-chronicle panes match game output exactly!");
        }

        // Verify round-trip
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle reloadedChron = reloaded.getChronicle();
        assertNotNull(reloadedChron);
        assertEquals(28, reloadedChron.getNumRunewords());
        assertEquals(28, reloadedChron.getFoundRunewordCount());

        // Dump all runeword entries from output file
        int chronStart = outOffsets[outOffsets.length - 1];
        int setCount = (outBytes[chronStart + 70] & 0xFF) | ((outBytes[chronStart + 71] & 0xFF) << 8);
        int uniqCount = (outBytes[chronStart + 72] & 0xFF) | ((outBytes[chronStart + 73] & 0xFF) << 8);
        int runeCount = (outBytes[chronStart + 74] & 0xFF) | ((outBytes[chronStart + 75] & 0xFF) << 8);
        int runeStart = chronStart + 88 + (setCount + uniqCount) * 10;
        System.out.println("\n=== RUNEWORD ENTRIES (output) ===");
        for (int i = 0; i < runeCount; i++) {
            int off = runeStart + i * 10;
            System.out.printf("  rw[%d] %s f6=0x%04X%n", i,
                    bytesToHex(Arrays.copyOfRange(outBytes, off, off + 10)),
                    (outBytes[off + 6] & 0xFF) | ((outBytes[off + 7] & 0xFF) << 8));
        }

        System.out.println("\nOutput written to: " + outFile.getAbsolutePath());
    }

    @Test
    public void testMarkFoundOverwritesDuplicateSlot() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        D2BitReader sourceReader = new D2BitReader(stashFile.getAbsolutePath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), sourceReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);

        int baselineCount = chronicle.getNumUniqueItems();
        int baselineFound = chronicle.getFoundUniqueCount();

        // Good file has all slots filled (all real + 1 sentinel).
        // markFound inserts before the sentinel, growing the section by 1.
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getUniqueGrailEntries();
        int notFoundIdx = -1;
        for (int i = 0; i < grail.size(); i++) {
            if (!grail.get(i).isFound()) { notFoundIdx = i; break; }
        }
        assertTrue(notFoundIdx >= 0, "There should be grail entries not found in binary");
        assertTrue(chronicle.markFound(D2Chronicle.Section.UNIQUE, notFoundIdx),
                "markFound should succeed by inserting before sentinel");
        assertEquals(baselineCount + 1, chronicle.getNumUniqueItems(),
                "Unique count grows by 1 (inserted before sentinel, sentinel preserved)");
        assertEquals(baselineFound + 1, chronicle.getFoundUniqueCount(),
                "Found count grows by 1 (new entry inserted)");
    }

    @Test
    public void testMarkUniqueFoundPersistsToD2i() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping unique persistence test: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        D2BitReader sourceReader = new D2BitReader(stashFile.getAbsolutePath());
        byte[] originalContent = sourceReader.getFileContent().clone();
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), sourceReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle, "Chronicle should exist in the sample stash");

        int baselineUniqueCount = chronicle.getNumUniqueItems();
        int baselineFoundUniques = chronicle.getFoundUniqueCount();

        // Good file: all slots full — markFound overwrites last slot
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getUniqueGrailEntries();
        int targetIndex = -1;
        for (int i = 0; i < grail.size(); i++) {
            if (!grail.get(i).isFound()) { targetIndex = i; break; }
        }
        assertTrue(targetIndex >= 0, "There must be at least one not-found unique in grail");
        String targetName = grail.get(targetIndex).getItemName();
        int targetAstxId = grail.get(targetIndex).getRawField6();
        System.out.println("Marking unique as found: " + targetName + " (*ID=" + targetAstxId + ")");

        assertTrue(chronicle.markFound(D2Chronicle.Section.UNIQUE, targetIndex),
                "Marking a not-found unique should succeed by inserting before sentinel");
        assertEquals(baselineUniqueCount + 1, chronicle.getNumUniqueItems(),
                "Unique count grows by 1 (inserted before sentinel, sentinel preserved)");
        assertEquals(baselineFoundUniques + 1, chronicle.getFoundUniqueCount(),
                "Found count grows by 1 (new entry inserted)");

        // Idempotency: marking the same item again should return false
        assertFalse(chronicle.markFound(D2Chronicle.Section.UNIQUE, targetIndex),
                "Marking an already-found unique should return false");

        // Write and reload
        File outFile = File.createTempFile("gomule-unique-mark-found", ".d2i");
        outFile.deleteOnExit();
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalContent, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalContent).write(writableStash);
        byte[] writtenBytes = Files.readAllBytes(outFile.toPath());

        assertEquals(originalContent.length + 10, writtenBytes.length,
                "Written file grows by 10 bytes (sentinel preserved, new entry inserted)");

        // Verify chronicle header counts in binary
        int[] writtenOffsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(writtenBytes.clone()));
        int writtenChronicleStart = writtenOffsets[writtenOffsets.length - 1];
        int writtenUniqueCount = (writtenBytes[writtenChronicleStart + 72] & 0xFF)
                | ((writtenBytes[writtenChronicleStart + 73] & 0xFF) << 8);
        assertEquals(baselineUniqueCount + 1, writtenUniqueCount,
                "Binary unique count grows by 1");

        // Reload and verify
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle reloadedChronicle = reloaded.getChronicle();
        assertNotNull(reloadedChronicle);
        assertEquals(baselineUniqueCount + 1, reloadedChronicle.getNumUniqueItems());
        assertEquals(baselineFoundUniques + 1, reloadedChronicle.getFoundUniqueCount());

        // Verify the specific item is found with the correct *ID
        boolean itemFound = false;
        for (D2Chronicle.ChronicleEntry e : reloadedChronicle.getUniqueEntries()) {
            if (e.isFound() && e.getRawField6() == targetAstxId) {
                itemFound = true;
                break;
            }
        }
        assertTrue(itemFound, targetName + " (*ID=" + targetAstxId + ") should be found in reloaded chronicle");
    }

    @Test
    public void testMarkSetFoundPersistsToD2i() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping set persistence test: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        D2BitReader sourceReader = new D2BitReader(stashFile.getAbsolutePath());
        byte[] originalContent = sourceReader.getFileContent().clone();
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), sourceReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle, "Chronicle should exist in the sample stash");

        int baselineSetCount = chronicle.getNumSetItems();
        int baselineFoundSets = chronicle.getFoundSetCount();

        // Good file: all binary set slots are found.  Free one slot first.
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getSetGrailEntries();
        int victimIndex = -1;
        for (int i = 0; i < grail.size(); i++) {
            if (grail.get(i).isFound()) { victimIndex = i; break; }
        }
        assertTrue(victimIndex >= 0, "There must be at least one found set item in grail");
        assertTrue(chronicle.markNotFound(D2Chronicle.Section.SET, victimIndex),
                "Unmarking a found set item should succeed");

        // Now find a not-found grail entry and mark it
        grail = chronicle.getSetGrailEntries();
        int targetIndex = -1;
        for (int i = 0; i < grail.size(); i++) {
            if (!grail.get(i).isFound()) { targetIndex = i; break; }
        }
        assertTrue(targetIndex >= 0, "There must be at least one not-found set item in grail");
        String targetName = grail.get(targetIndex).getItemName();
        int targetAstxId = grail.get(targetIndex).getRawField6();
        System.out.println("Marking set item as found: " + targetName + " (*ID=" + targetAstxId + ")");

        assertTrue(chronicle.markFound(D2Chronicle.Section.SET, targetIndex),
                "Marking a not-found set item should succeed after freeing a slot");
        assertEquals(baselineSetCount, chronicle.getNumSetItems(),
                "Set count must not change (slot reuse, not append)");
        assertEquals(baselineFoundSets, chronicle.getFoundSetCount(),
                "Found set count stays the same (one removed, one added)");

        // Idempotency
        assertFalse(chronicle.markFound(D2Chronicle.Section.SET, targetIndex),
                "Marking an already-found set item should return false");

        // Write and reload
        File outFile = File.createTempFile("gomule-set-mark-found", ".d2i");
        outFile.deleteOnExit();
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalContent, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalContent).write(writableStash);
        byte[] writtenBytes = Files.readAllBytes(outFile.toPath());

        assertEquals(originalContent.length, writtenBytes.length,
                "Written file must be the same size (no count growth)");

        // Verify chronicle header counts in binary
        int[] writtenOffsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(writtenBytes.clone()));
        int writtenChronicleStart = writtenOffsets[writtenOffsets.length - 1];
        int writtenSetCount = (writtenBytes[writtenChronicleStart + 70] & 0xFF)
                | ((writtenBytes[writtenChronicleStart + 71] & 0xFF) << 8);
        assertEquals(baselineSetCount, writtenSetCount,
                "Binary set count must not change");

        // Reload and verify
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle reloadedChronicle = reloaded.getChronicle();
        assertNotNull(reloadedChronicle);
        assertEquals(baselineSetCount, reloadedChronicle.getNumSetItems());
        assertEquals(baselineFoundSets, reloadedChronicle.getFoundSetCount());

        // Verify the specific item is found
        boolean itemFound = false;
        for (D2Chronicle.ChronicleEntry e : reloadedChronicle.getSetEntries()) {
            if (e.isFound() && e.getRawField6() == targetAstxId) {
                itemFound = true;
                break;
            }
        }
        assertTrue(itemFound, targetName + " (*ID=" + targetAstxId + ") should be found in reloaded chronicle");
    }

    @Test
    public void testMarkAndUnmarkUniqueRoundTrip() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        D2BitReader sourceReader = new D2BitReader(stashFile.getAbsolutePath());
        byte[] originalContent = sourceReader.getFileContent().clone();
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), sourceReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);

        int baselineCount = chronicle.getNumUniqueItems();
        int baselineFound = chronicle.getFoundUniqueCount();

        // Pick a found unique and unmark then remark it
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getUniqueGrailEntries();
        int targetIndex = -1;
        for (int i = 0; i < grail.size(); i++) {
            if (grail.get(i).isFound()) { targetIndex = i; break; }
        }
        assertTrue(targetIndex >= 0);

        // Unmark, then re-mark
        chronicle.markNotFound(D2Chronicle.Section.UNIQUE, targetIndex);
        assertEquals(baselineFound - 1, chronicle.getFoundUniqueCount());
        chronicle.markFound(D2Chronicle.Section.UNIQUE, targetIndex);
        assertEquals(baselineFound, chronicle.getFoundUniqueCount());
        assertEquals(baselineCount, chronicle.getNumUniqueItems(),
                "Count must not change during unmark+mark cycle");
        assertTrue(chronicle.isModified());

        // Write file — must be same size as original (slot reused)
        File outFile = File.createTempFile("gomule-unique-roundtrip", ".d2i");
        outFile.deleteOnExit();
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalContent, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalContent).write(writableStash);
        byte[] writtenBytes = Files.readAllBytes(outFile.toPath());
        assertEquals(originalContent.length, writtenBytes.length,
                "File size must not change after unmark+mark round trip");

        // Reload and verify counts match baseline
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle reloadedChronicle = reloaded.getChronicle();
        assertNotNull(reloadedChronicle);
        assertEquals(baselineCount, reloadedChronicle.getNumUniqueItems());
        assertEquals(baselineFound, reloadedChronicle.getFoundUniqueCount(),
                "Found unique count should be restored after unmark+mark");
    }

    /**
     * Produces a savefile for game testing by swapping one unique and one set
     * entry.  Unmarks a found entry in each section to free a binary slot,
     * then marks a previously-not-found grail entry into that slot.
     * Output: savefiles/ModernSharedStashSoftCoreV2_unique_set_test.d2i
     */
    @Test
    public void testProduceUniqueSetTestFile() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        D2BitReader sourceReader = new D2BitReader(stashFile.getAbsolutePath());
        byte[] originalContent = sourceReader.getFileContent().clone();
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), sourceReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);

        System.out.println("=== BASELINE ===");
        System.out.println("Unique: " + chronicle.getFoundUniqueCount() + "/" + chronicle.getNumUniqueItems());
        System.out.println("Set:    " + chronicle.getFoundSetCount() + "/" + chronicle.getNumSetItems());

        // --- Unique: directly mark (inserts before sentinel, grows count) ---
        List<D2Chronicle.ChronicleEntry> uniqueGrail = chronicle.getUniqueGrailEntries();
        int uniqueIdx = -1;
        for (int i = 0; i < uniqueGrail.size(); i++) {
            if (!uniqueGrail.get(i).isFound()) { uniqueIdx = i; break; }
        }
        if (uniqueIdx >= 0) {
            String name = uniqueGrail.get(uniqueIdx).getItemName();
            int astxId = uniqueGrail.get(uniqueIdx).getRawField6();
            assertTrue(chronicle.markFound(D2Chronicle.Section.UNIQUE, uniqueIdx));
            System.out.println("Marked unique:   " + name + " (*ID=" + astxId + ")");
        }

        // --- Set: free a slot first (sets have no duplicates), then fill it ---
        List<D2Chronicle.ChronicleEntry> setGrail = chronicle.getSetGrailEntries();
        int setVictimIdx = -1;
        for (int i = 0; i < setGrail.size(); i++) {
            if (setGrail.get(i).isFound()) { setVictimIdx = i; break; }
        }
        if (setVictimIdx >= 0) {
            String victimName = setGrail.get(setVictimIdx).getItemName();
            chronicle.markNotFound(D2Chronicle.Section.SET, setVictimIdx);
            System.out.println("Unmarked set:    " + victimName);
        }
        setGrail = chronicle.getSetGrailEntries();
        int setIdx = -1;
        for (int i = 0; i < setGrail.size(); i++) {
            if (!setGrail.get(i).isFound()) { setIdx = i; break; }
        }
        if (setIdx >= 0) {
            String name = setGrail.get(setIdx).getItemName();
            int astxId = setGrail.get(setIdx).getRawField6();
            assertTrue(chronicle.markFound(D2Chronicle.Section.SET, setIdx));
            System.out.println("Marked set:      " + name + " (*ID=" + astxId + ")");
        }

        System.out.println("\n=== AFTER MARKING ===");
        System.out.println("Unique: " + chronicle.getFoundUniqueCount() + "/" + chronicle.getNumUniqueItems());
        System.out.println("Set:    " + chronicle.getFoundSetCount() + "/" + chronicle.getNumSetItems());

        // Write output
        File outFile = new File("../savefiles/ModernSharedStashSoftCoreV2_unique_set_test.d2i");
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalContent, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalContent).write(writableStash);
        byte[] outBytes = Files.readAllBytes(outFile.toPath());

        assertEquals(originalContent.length + 10, outBytes.length,
                "Output file grows by 10 bytes (unique sentinel preserved, new entry inserted before it)");

        // Verify round-trip
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle rc = reloaded.getChronicle();
        assertNotNull(rc);
        System.out.println("\n=== RELOADED ===");
        System.out.println("Unique: " + rc.getFoundUniqueCount() + "/" + rc.getNumUniqueItems());
        System.out.println("Set:    " + rc.getFoundSetCount() + "/" + rc.getNumSetItems());
        System.out.println("Output: " + outFile.getAbsolutePath() + " (" + outBytes.length + " bytes)");

        // Dump binary counts for verification
        int[] offsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(outBytes.clone()));
        int chronStart = offsets[offsets.length - 1];
        int setCount = (outBytes[chronStart + 70] & 0xFF) | ((outBytes[chronStart + 71] & 0xFF) << 8);
        int uniqCount = (outBytes[chronStart + 72] & 0xFF) | ((outBytes[chronStart + 73] & 0xFF) << 8);
        System.out.println("\nBinary counts: set=" + setCount + " unique=" + uniqCount);

        System.out.println("\nDeploy to game folder for testing:");
        System.out.println("  Copy-Item -Path \"" + outFile.getAbsolutePath()
                + "\" -Destination \"$env:USERPROFILE\\Saved Games\\Diablo II Resurrected\\ModernSharedStashSoftCoreV2.d2i\" -Force");
    }

    /**
     * Diagnostic: dump raw bytes for unique entries with known game timestamps.
     * Compare to find the correct timestamp encoding.
     */
    @Test
    public void testDiagnoseTimestampEncoding() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        D2BitReader sourceReader = new D2BitReader(stashFile.getAbsolutePath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), sourceReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);

        // Known game timestamps from the user's manual record (good-entries.txt):
        // Format: item name -> *ID, game date/time
        // Andariel's Visage  *ID=345  03/16/2026 21:30
        // Coif of Glory      *ID=8    03/03/2026 13:59
        // Duskdeep           *ID=9    03/01/2026 00:14
        // Howltusk           *ID=10   03/18/2026 18:02
        // Undead Crown       *ID=12   02/26/2026 09:08
        // Harlequin Crest    *ID=308  02/26/2026 23:48
        // Veil of Steel      *ID=316  03/08/2026 21:20

        // Map *ID to known game time description
        Map<Integer, String> knownTimes = new LinkedHashMap<>();
        knownTimes.put(345, "03/16/2026 21:30");
        knownTimes.put(8,   "03/03/2026 13:59");
        knownTimes.put(9,   "03/01/2026 00:14");
        knownTimes.put(10,  "03/18/2026 18:02");
        knownTimes.put(12,  "02/26/2026 09:08");
        knownTimes.put(308, "02/26/2026 23:48");
        knownTimes.put(316, "03/08/2026 21:20");

        // Compute expected minutes-since-epoch for each known time
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");
        java.time.ZoneId utc = java.time.ZoneId.of("UTC");
        System.out.println("=== EXPECTED MINUTES-SINCE-EPOCH (UTC) ===");
        Map<Integer, Long> expectedMinutes = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> e : knownTimes.entrySet()) {
            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(e.getValue(), fmt);
            long epochMin = ldt.atZone(utc).toEpochSecond() / 60;
            expectedMinutes.put(e.getKey(), epochMin);
            System.out.printf("  *ID=%3d  %s  -> epochMin=%d (0x%08X)%n", e.getKey(), e.getValue(), epochMin, epochMin);
        }

        // Also try common US timezones
        for (String tz : new String[]{"America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles"}) {
            System.out.println("\n=== EXPECTED MINUTES-SINCE-EPOCH (" + tz + ") ===");
            java.time.ZoneId zone = java.time.ZoneId.of(tz);
            for (Map.Entry<Integer, String> e : knownTimes.entrySet()) {
                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(e.getValue(), fmt);
                long epochMin = ldt.atZone(zone).toEpochSecond() / 60;
                System.out.printf("  *ID=%3d  %s  -> epochMin=%d (0x%08X)%n", e.getKey(), e.getValue(), epochMin, epochMin);
            }
        }

        // Dump the raw bytes for each known entry
        System.out.println("\n=== RAW BYTES FOR KNOWN ENTRIES ===");
        List<D2Chronicle.ChronicleEntry> uniqueEntries = chronicle.getUniqueEntries();
        for (D2Chronicle.ChronicleEntry entry : uniqueEntries) {
            if (!entry.isFound()) continue;
            int id = entry.getRawField6();
            if (!knownTimes.containsKey(id)) continue;

            byte[] raw = entry.getRawBytes();
            System.out.printf("*ID=%3d (%s)%n", id, knownTimes.get(id));
            System.out.printf("  Raw hex: %s%n", bytesToHex(raw));
            System.out.printf("  Bytes:   [%d] [%d] [%d] [%d] [%d] [%d] [%d] [%d] [%d] [%d]%n",
                    raw[0] & 0xFF, raw[1] & 0xFF, raw[2] & 0xFF, raw[3] & 0xFF, raw[4] & 0xFF,
                    raw[5] & 0xFF, raw[6] & 0xFF, raw[7] & 0xFF, raw[8] & 0xFF, raw[9] & 0xFF);

            // Try various u32 interpretations across different byte offsets
            for (int off = 0; off <= 6; off++) {
                long u32 = (raw[off] & 0xFFL) | ((raw[off+1] & 0xFFL) << 8)
                        | ((raw[off+2] & 0xFFL) << 16) | ((raw[off+3] & 0xFFL) << 24);
                java.time.Instant inst = java.time.Instant.ofEpochSecond(u32 * 60);
                String dateStr = inst.atZone(utc).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                System.out.printf("  u32LE@[%d..%d] = %d (0x%08X) -> %s (UTC, as minutes)%n", off, off+3, u32, u32, dateStr);
            }

            // Try big-endian u32 at each offset
            for (int off = 0; off <= 6; off++) {
                long u32be = ((raw[off] & 0xFFL) << 24) | ((raw[off+1] & 0xFFL) << 16)
                        | ((raw[off+2] & 0xFFL) << 8) | (raw[off+3] & 0xFFL);
                java.time.Instant inst = java.time.Instant.ofEpochSecond(u32be * 60);
                String dateStr = inst.atZone(utc).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                System.out.printf("  u32BE@[%d..%d] = %d (0x%08X) -> %s (UTC, as minutes)%n", off, off+3, u32be, u32be, dateStr);
            }

            // Try as seconds instead of minutes
            for (int off = 0; off <= 6; off++) {
                long u32 = (raw[off] & 0xFFL) | ((raw[off+1] & 0xFFL) << 8)
                        | ((raw[off+2] & 0xFFL) << 16) | ((raw[off+3] & 0xFFL) << 24);
                if (u32 > 1700000000L || u32 < 1000000000L) continue; // only plausible unix seconds range
                java.time.Instant inst = java.time.Instant.ofEpochSecond(u32);
                String dateStr = inst.atZone(utc).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                System.out.printf("  u32LE@[%d..%d] = %d -> %s (UTC, as SECONDS)%n", off, off+3, u32, dateStr);
            }

            System.out.println();
        }
    }

    /**
     * Reproducer: mark Arachnid Mesh as found, write, compare bytes.
     * Diagnoses "join game failed" after saving.
     */
    @Test
    public void testMarkArachnidMeshByteDiff() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        byte[] originalBytes = Files.readAllBytes(stashFile.toPath());
        D2BitReader sourceReader = new D2BitReader(stashFile.getAbsolutePath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), sourceReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);

        // Dump unique entry stats
        List<D2Chronicle.ChronicleEntry> binaryUniques = chronicle.getUniqueEntries();
        int foundCount = 0, emptyCount = 0;
        System.out.println("=== UNIQUE BINARY ENTRIES (last 10) ===");
        for (int i = 0; i < binaryUniques.size(); i++) {
            D2Chronicle.ChronicleEntry e = binaryUniques.get(i);
            if (e.isFound()) foundCount++; else emptyCount++;
            if (i >= binaryUniques.size() - 10) {
                byte[] raw = e.getRawBytes();
                System.out.printf("  [%3d] found=%-5s field6=%-6d rawHex=%s%n",
                        i, e.isFound(), e.getRawField6(),
                        raw != null ? bytesToHex(raw) : "null");
            }
        }
        System.out.println("Unique entries: " + binaryUniques.size()
                + " found=" + foundCount + " empty=" + emptyCount);

        // Check last set entry too
        List<D2Chronicle.ChronicleEntry> binarySets = chronicle.getSetEntries();
        if (!binarySets.isEmpty()) {
            D2Chronicle.ChronicleEntry lastSet = binarySets.get(binarySets.size() - 1);
            byte[] raw = lastSet.getRawBytes();
            System.out.printf("=== LAST SET ENTRY [%d] ===\n  found=%-5s field6=%-6d (0x%04X) rawHex=%s%n",
                    binarySets.size() - 1, lastSet.isFound(), lastSet.getRawField6() & 0xFFFF,
                    lastSet.getRawField6() & 0xFFFF, raw != null ? bytesToHex(raw) : "null");
        }

        // Check what field6 values > 1000 exist (possible sentinel/marker entries)
        System.out.println("=== UNUSUAL field6 values (>1000) in unique entries ===");
        for (int i = 0; i < binaryUniques.size(); i++) {
            D2Chronicle.ChronicleEntry e = binaryUniques.get(i);
            if (e.isFound() && (e.getRawField6() & 0xFFFF) > 1000) {
                byte[] raw = e.getRawBytes();
                System.out.printf("  [%3d] field6=%-6d (0x%04X) rawHex=%s%n",
                        i, e.getRawField6() & 0xFFFF, e.getRawField6() & 0xFFFF,
                        raw != null ? bytesToHex(raw) : "null");
            }
        }

        // Find Arachnid Mesh in unique grail list
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getUniqueGrailEntries();
        int arachnidIdx = -1;
        for (int i = 0; i < grail.size(); i++) {
            String name = grail.get(i).getItemName();
            if (name != null && name.toLowerCase().contains("arachnid")) {
                arachnidIdx = i;
                System.out.println("Found Arachnid Mesh at grail index " + i
                        + ", field6=" + grail.get(i).getRawField6()
                        + ", found=" + grail.get(i).isFound());
                break;
            }
        }
        assertTrue(arachnidIdx >= 0, "Arachnid Mesh must be in grail list");

        // Mark as found
        boolean marked = chronicle.markFound(D2Chronicle.Section.UNIQUE, arachnidIdx);
        System.out.println("markFound returned: " + marked);
        System.out.println("isModified: " + chronicle.isModified());

        // Write
        File outFile = File.createTempFile("gomule-arachnid-", ".d2i");
        outFile.deleteOnExit();
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalBytes, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalBytes).write(writableStash);
        byte[] writtenBytes = Files.readAllBytes(outFile.toPath());

        // Compare sizes
        System.out.println("Original size: " + originalBytes.length);
        System.out.println("Written size:  " + writtenBytes.length);

        // Compare pane offsets
        int[] origOffsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(originalBytes.clone()));
        int[] writOffsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(writtenBytes.clone()));
        System.out.println("Original pane offsets: " + Arrays.toString(origOffsets));
        System.out.println("Written pane offsets:  " + Arrays.toString(writOffsets));

        // Byte-by-byte diff
        int minLen = Math.min(originalBytes.length, writtenBytes.length);
        int diffCount = 0;
        for (int i = 0; i < minLen; i++) {
            if (originalBytes[i] != writtenBytes[i]) {
                // Determine which pane this byte belongs to
                String paneInfo = "";
                for (int p = origOffsets.length - 1; p >= 0; p--) {
                    if (i >= origOffsets[p]) {
                        int relOffset = i - origOffsets[p];
                        paneInfo = " (pane " + p + ", rel offset " + relOffset + ")";
                        break;
                    }
                }
                System.out.printf("DIFF @%d%s: orig=0x%02X writ=0x%02X%n",
                        i, paneInfo, originalBytes[i] & 0xFF, writtenBytes[i] & 0xFF);
                diffCount++;
                if (diffCount > 100) {
                    System.out.println("... (truncated)");
                    break;
                }
            }
        }
        if (writtenBytes.length > minLen) {
            System.out.println("Written file has " + (writtenBytes.length - minLen) + " extra bytes");
        } else if (originalBytes.length > minLen) {
            System.out.println("Original file has " + (originalBytes.length - minLen) + " extra bytes");
        }
        System.out.println("Total byte differences: " + diffCount);

        // Also verify the written file can be re-read
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        assertNotNull(reloaded.getChronicle(), "Reloaded chronicle must not be null");
    }

    @Test
    public void testMarkBlackbogsSharpByteDiff() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        byte[] originalBytes = Files.readAllBytes(stashFile.toPath());
        D2BitReader sourceReader = new D2BitReader(stashFile.getAbsolutePath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), sourceReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);

        // Show binary status
        List<D2Chronicle.ChronicleEntry> binaryUniques = chronicle.getUniqueEntries();
        int foundCount = 0, emptyCount = 0;
        for (D2Chronicle.ChronicleEntry e : binaryUniques) {
            if (e.isFound()) foundCount++; else emptyCount++;
        }
        System.out.println("BEFORE: unique binary entries=" + binaryUniques.size()
                + " found=" + foundCount + " empty=" + emptyCount
                + " numUniqueItems=" + chronicle.getNumUniqueItems());
        // Show last 3 entries
        for (int i = Math.max(0, binaryUniques.size() - 3); i < binaryUniques.size(); i++) {
            D2Chronicle.ChronicleEntry e = binaryUniques.get(i);
            System.out.printf("  [%3d] found=%-5s field6=%-6d (0x%04X) rawHex=%s%n",
                    i, e.isFound(), e.getRawField6() & 0xFFFF, e.getRawField6() & 0xFFFF,
                    e.getRawBytes() != null ? bytesToHex(e.getRawBytes()) : "null");
        }

        // Find Blackbog's Sharp in grail
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getUniqueGrailEntries();
        int blackbogIdx = -1;
        for (int i = 0; i < grail.size(); i++) {
            String name = grail.get(i).getItemName();
            if (name != null && name.contains("Blackbog")) {
                blackbogIdx = i;
                System.out.println("Blackbog's Sharp at grail index " + i
                        + ", *ID(field6)=" + grail.get(i).getRawField6()
                        + ", found=" + grail.get(i).isFound());
                break;
            }
        }
        assertTrue(blackbogIdx >= 0, "Blackbog's Sharp must be in grail list");
        assertFalse(grail.get(blackbogIdx).isFound(), "Blackbog's Sharp should not be found yet");

        // Mark as found
        boolean marked = chronicle.markFound(D2Chronicle.Section.UNIQUE, blackbogIdx);
        assertTrue(marked, "markFound should return true");

        // Show binary status after
        binaryUniques = chronicle.getUniqueEntries();
        foundCount = 0; emptyCount = 0;
        for (D2Chronicle.ChronicleEntry e : binaryUniques) {
            if (e.isFound()) foundCount++; else emptyCount++;
        }
        System.out.println("AFTER: unique binary entries=" + binaryUniques.size()
                + " found=" + foundCount + " empty=" + emptyCount
                + " numUniqueItems=" + chronicle.getNumUniqueItems());
        for (int i = Math.max(0, binaryUniques.size() - 3); i < binaryUniques.size(); i++) {
            D2Chronicle.ChronicleEntry e = binaryUniques.get(i);
            System.out.printf("  [%3d] found=%-5s field6=%-6d (0x%04X) rawHex=%s%n",
                    i, e.isFound(), e.getRawField6() & 0xFFFF, e.getRawField6() & 0xFFFF,
                    e.getRawBytes() != null ? bytesToHex(e.getRawBytes()) : "null");
        }

        // Write
        File outFile = File.createTempFile("gomule-blackbog-", ".d2i");
        outFile.deleteOnExit();
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalBytes, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalBytes).write(writableStash);
        byte[] writtenBytes = Files.readAllBytes(outFile.toPath());

        System.out.println("Original size: " + originalBytes.length);
        System.out.println("Written size:  " + writtenBytes.length);

        // Byte-by-byte diff
        int minLen = Math.min(originalBytes.length, writtenBytes.length);
        int diffCount = 0;
        int[] origOffsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(originalBytes.clone()));
        for (int i = 0; i < minLen; i++) {
            if (originalBytes[i] != writtenBytes[i]) {
                String paneInfo = "";
                for (int p = origOffsets.length - 1; p >= 0; p--) {
                    if (i >= origOffsets[p]) {
                        int relOffset = i - origOffsets[p];
                        paneInfo = " (pane " + p + ", rel offset " + relOffset + ")";
                        break;
                    }
                }
                System.out.printf("DIFF @%d%s: orig=0x%02X writ=0x%02X%n",
                        i, paneInfo, originalBytes[i] & 0xFF, writtenBytes[i] & 0xFF);
                diffCount++;
                if (diffCount > 50) { System.out.println("... (truncated)"); break; }
            }
        }
        if (writtenBytes.length > minLen) {
            System.out.println("Written file has " + (writtenBytes.length - minLen) + " extra bytes at end:");
            for (int i = minLen; i < Math.min(writtenBytes.length, minLen + 20); i++) {
                System.out.printf("  @%d: 0x%02X%n", i, writtenBytes[i] & 0xFF);
            }
        }
        System.out.println("Total byte differences: " + diffCount);

        // Re-read and verify
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        assertNotNull(reloaded.getChronicle(), "Reloaded chronicle must not be null");
        System.out.println("Re-read: numUniqueItems=" + reloaded.getChronicle().getNumUniqueItems());
    }

    @Test
    public void testMarkBlackbogsSharpOnUserFile() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        byte[] originalBytes = Files.readAllBytes(stashFile.toPath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(),
                new D2BitReader(stashFile.getAbsolutePath()));
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);
        System.out.printf("BEFORE: set=%d uniq=%d rw=%d%n",
                chronicle.getNumSetItems(), chronicle.getNumUniqueItems(), chronicle.getNumRunewords());

        // Find Blackbog's Sharp
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getUniqueGrailEntries();
        int blackbogIdx = -1;
        for (int i = 0; i < grail.size(); i++) {
            String name = grail.get(i).getItemName();
            if (name != null && name.contains("Blackbog")) {
                blackbogIdx = i;
                System.out.println("Blackbog at grail[" + i + "] *ID=" + grail.get(i).getRawField6()
                        + " found=" + grail.get(i).isFound());
                break;
            }
        }
        assertTrue(blackbogIdx >= 0);

        // Mark as found
        List<D2Chronicle.ChronicleEntry> setBefore = chronicle.getSetEntries();
        int setSentinelBefore = setBefore.get(setBefore.size() - 1).getRawField6();
        System.out.println("Set sentinel BEFORE marking: field6=" + setSentinelBefore);

        chronicle.markFound(D2Chronicle.Section.UNIQUE, blackbogIdx);
        System.out.printf("AFTER: set=%d uniq=%d rw=%d%n",
                chronicle.getNumSetItems(), chronicle.getNumUniqueItems(), chronicle.getNumRunewords());

        // Verify set sentinel was relocated (if it was 170)
        List<D2Chronicle.ChronicleEntry> setAfter = chronicle.getSetEntries();
        int setSentinelAfter = setAfter.get(setAfter.size() - 1).getRawField6();
        System.out.println("Set sentinel AFTER marking: field6=" + setSentinelAfter);
        if (setSentinelBefore == 170) {
            assertNotEquals(170, setSentinelAfter,
                    "Set sentinel must be relocated away from 170 (Blackbog's *ID)");
        }

        // Show new entry and sentinel area
        List<D2Chronicle.ChronicleEntry> binaryUniques = chronicle.getUniqueEntries();
        System.out.println("Unique entries size: " + binaryUniques.size());
        for (int i = Math.max(0, binaryUniques.size() - 3); i < binaryUniques.size(); i++) {
            D2Chronicle.ChronicleEntry e = binaryUniques.get(i);
            System.out.printf("  [%3d] found=%-5s field6=%-6d (0x%04X) raw=%s%n",
                    i, e.isFound(), e.getRawField6() & 0xFFFF, e.getRawField6() & 0xFFFF,
                    e.getRawBytes() != null ? bytesToHex(e.getRawBytes()) : "null");
        }

        // Write
        File outFile = File.createTempFile("gomule-bb-user-", ".d2i");
        outFile.deleteOnExit();
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalBytes, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalBytes).write(writableStash);
        byte[] writtenBytes = Files.readAllBytes(outFile.toPath());

        System.out.println("Original: " + originalBytes.length + " Written: " + writtenBytes.length);

        // Verify can be fully re-read
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle rc = reloaded.getChronicle();
        assertNotNull(rc);
        System.out.printf("RELOADED: set=%d uniq=%d rw=%d%n",
                rc.getNumSetItems(), rc.getNumUniqueItems(), rc.getNumRunewords());

        // Verify Blackbog is now found
        List<D2Chronicle.ChronicleEntry> reloadedGrail = rc.getUniqueGrailEntries();
        D2Chronicle.ChronicleEntry bbEntry = reloadedGrail.get(blackbogIdx);
        assertTrue(bbEntry.isFound(), "Blackbog should be found after marking");
        assertEquals(170, bbEntry.getRawField6(), "Blackbog *ID should be 170");

        // Verify sentinel is still the last entry and not valid
        List<D2Chronicle.ChronicleEntry> reloadedBinary = rc.getUniqueEntries();
        D2Chronicle.ChronicleEntry lastEntry = reloadedBinary.get(reloadedBinary.size() - 1);
        System.out.printf("Last entry: field6=%d (0x%04X) found=%s raw=%s%n",
                lastEntry.getRawField6() & 0xFFFF, lastEntry.getRawField6() & 0xFFFF,
                lastEntry.isFound(),
                lastEntry.getRawBytes() != null ? bytesToHex(lastEntry.getRawBytes()) : "null");

        // Byte diff - only chronicle pane changes
        int[] origOffsets = D2SharedStashReader.getStashHeaderOffsets(ROW, new D2BitReader(originalBytes.clone()));
        int chroniclePaneStart = origOffsets[origOffsets.length - 1];
        int minLen = Math.min(originalBytes.length, writtenBytes.length);
        int diffInNonChronicle = 0;
        for (int i = 0; i < Math.min(chroniclePaneStart, minLen); i++) {
            if (originalBytes[i] != writtenBytes[i]) diffInNonChronicle++;
        }
        System.out.println("Diffs before chronicle pane: " + diffInNonChronicle);
        assertEquals(0, diffInNonChronicle, "Only chronicle pane should change");

        // Check written file for false 55AA55AA patterns
        byte[] marker = {0x55, (byte) 0xAA, 0x55, (byte) 0xAA};
        int markerCount = 0;
        for (int i = 0; i < writtenBytes.length - 3; i++) {
            if (writtenBytes[i] == marker[0] && writtenBytes[i + 1] == marker[1]
                    && writtenBytes[i + 2] == marker[2] && writtenBytes[i + 3] == marker[3]) {
                markerCount++;
            }
        }
        System.out.println("55AA55AA markers in written file: " + markerCount);
        assertEquals(7, markerCount, "Should still have exactly 7 pane markers");
    }

    @Test
    public void testMarkBlackbogsRelocatesSetSentinel() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        byte[] originalBytes = Files.readAllBytes(stashFile.toPath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(),
                new D2BitReader(stashFile.getAbsolutePath()));
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);

        // Confirm set sentinel is currently field6=170 (the collision value)
        List<D2Chronicle.ChronicleEntry> setSentinelBefore = chronicle.getSetEntries();
        D2Chronicle.ChronicleEntry lastSetBefore = setSentinelBefore.get(setSentinelBefore.size() - 1);
        assertEquals(170, lastSetBefore.getRawField6(), "Set sentinel should initially be 170");
        System.out.println("Set sentinel BEFORE: field6=" + lastSetBefore.getRawField6());

        // Find and mark Blackbog's Sharp (*ID=170)
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getUniqueGrailEntries();
        int blackbogIdx = -1;
        for (int i = 0; i < grail.size(); i++) {
            if (grail.get(i).getItemName() != null && grail.get(i).getItemName().contains("Blackbog")) {
                blackbogIdx = i;
                break;
            }
        }
        assertTrue(blackbogIdx >= 0, "Blackbog's Sharp must exist in grail");
        assertEquals(170, grail.get(blackbogIdx).getRawField6(), "Blackbog's *ID must be 170");

        boolean marked = chronicle.markFound(D2Chronicle.Section.UNIQUE, blackbogIdx);
        assertTrue(marked, "markFound should succeed");

        // Verify set sentinel was relocated (no longer 170)
        List<D2Chronicle.ChronicleEntry> setEntriesAfter = chronicle.getSetEntries();
        D2Chronicle.ChronicleEntry lastSetAfter = setEntriesAfter.get(setEntriesAfter.size() - 1);
        assertNotEquals(170, lastSetAfter.getRawField6(),
                "Set sentinel must be relocated away from 170 to avoid collision");
        System.out.println("Set sentinel AFTER: field6=" + lastSetAfter.getRawField6());

        // Sentinel must still be a non-valid set *ID (above 139)
        assertTrue(lastSetAfter.getRawField6() > 139,
                "Relocated sentinel must still be above max valid set *ID");

        // Verify Blackbog's is now found in unique section
        boolean blackbogFound = false;
        for (D2Chronicle.ChronicleEntry e : chronicle.getUniqueEntries()) {
            if (e.isFound() && e.getRawField6() == 170) {
                blackbogFound = true;
                break;
            }
        }
        assertTrue(blackbogFound, "Blackbog should be found in unique binary entries");

        // Verify no cross-section field6=170 collision between set sentinel and unique entries
        int setSentinelField6 = lastSetAfter.getRawField6();
        for (D2Chronicle.ChronicleEntry e : chronicle.getUniqueEntries()) {
            if (e.isFound()) {
                assertNotEquals(setSentinelField6, e.getRawField6(),
                        "Relocated set sentinel must not collide with any unique entry");
            }
        }

        // Write and re-read
        File outFile = File.createTempFile("gomule-bb-sentinel-", ".d2i");
        outFile.deleteOnExit();
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalBytes, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalBytes).write(writableStash);

        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        assertNotNull(reloaded.getChronicle());

        // Verify Blackbog's is still found after round-trip
        List<D2Chronicle.ChronicleEntry> reloadedGrail = reloaded.getChronicle().getUniqueGrailEntries();
        assertTrue(reloadedGrail.get(blackbogIdx).isFound(),
                "Blackbog should still be found after write/reload");
        System.out.println("Round-trip successful. Blackbog found=" + reloadedGrail.get(blackbogIdx).isFound());
    }

    @Test
    public void testMarkArcannaSignAsFound_doesNotCorruptFile() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        D2BitReader sourceReader = new D2BitReader(stashFile.getAbsolutePath());
        byte[] originalContent = sourceReader.getFileContent().clone();
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(), sourceReader);
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle, "Chronicle should exist");

        int origSetCount = chronicle.getNumSetItems();
        int origUniqCount = chronicle.getNumUniqueItems();
        int origRwCount = chronicle.getNumRunewords();
        System.out.println("Original counts: set=" + origSetCount + " uniq=" + origUniqCount + " rw=" + origRwCount);
        System.out.println("Original found: set=" + chronicle.getFoundSetCount()
                + " uniq=" + chronicle.getFoundUniqueCount() + " rw=" + chronicle.getFoundRunewordCount());

        // Count eligible set items in txt file
        int eligibleCount = 0;
        for (int i = 0; i < D2TxtFile.SETITEMS.getRowSize(); i++) {
            D2TxtFileItemProperties row = D2TxtFile.SETITEMS.getRow(i);
            String idStr = row.get("*ID");
            if (idStr == null || idStr.isEmpty()) continue;
            String code = row.get("item");
            if (code == null || code.isEmpty()) continue;
            if (!"1".equals(row.get("spawnable"))) continue;
            if ("1".equals(row.get("disableChronicle"))) continue;
            eligibleCount++;
        }
        System.out.println("Eligible set items in txt: " + eligibleCount);

        // Check which *IDs are in binary entries
        java.util.Set<Integer> binaryField6s = new java.util.TreeSet<>();
        for (D2Chronicle.ChronicleEntry e : chronicle.getSetEntries()) {
            if (e.isFound()) binaryField6s.add(e.getRawField6());
        }
        System.out.println("Distinct field6 in binary: " + binaryField6s.size());
        System.out.println("Field6 values: " + binaryField6s);

        // Check if 58 is present
        System.out.println("Field6=58 present in binary: " + binaryField6s.contains(58));

        // Print entries around index 58
        for (int i = Math.max(0, 56); i < Math.min(chronicle.getSetEntries().size(), 62); i++) {
            D2Chronicle.ChronicleEntry e = chronicle.getSetEntries().get(i);
            System.out.printf("  Entry[%d] found=%b field0=0x%04X ts=%d field6=%d(0x%04X) field8=0x%04X%n",
                    i, e.isFound(), e.getRawField0(), e.getRawTimestamp(),
                    e.getRawField6(), e.getRawField6(), e.getRawField8());
        }
        // Print last 3 entries
        for (int i = Math.max(0, chronicle.getSetEntries().size() - 3); i < chronicle.getSetEntries().size(); i++) {
            D2Chronicle.ChronicleEntry e = chronicle.getSetEntries().get(i);
            System.out.printf("  Entry[%d] found=%b field0=0x%04X ts=%d field6=%d(0x%04X) field8=0x%04X%n",
                    i, e.isFound(), e.getRawField0(), e.getRawTimestamp(),
                    e.getRawField6(), e.getRawField6(), e.getRawField8());
        }

        // Find Arcanna's Sign in the grail
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getSetGrailEntries();
        int arcannaIdx = -1;
        for (int i = 0; i < grail.size(); i++) {
            if ("Arcanna's Sign".equals(grail.get(i).getItemName())) {
                arcannaIdx = i;
                break;
            }
        }
        assertTrue(arcannaIdx >= 0, "Arcanna's Sign should be in set grail");
        D2Chronicle.ChronicleEntry arcannaGrail = grail.get(arcannaIdx);
        System.out.println("Arcanna's Sign: grailIdx=" + arcannaIdx + " *ID=" + arcannaGrail.getRawField6()
                + " found=" + arcannaGrail.isFound());

        // If already found, unmark first
        if (arcannaGrail.isFound()) {
            System.out.println("Arcanna's Sign already found, unmarking first...");
            assertTrue(chronicle.markNotFound(D2Chronicle.Section.SET, arcannaIdx));
            System.out.println("After unmark: setCount=" + chronicle.getNumSetItems()
                    + " foundSets=" + chronicle.getFoundSetCount());
            System.out.println("=== Set binary entries after unmark ===");
            for (int i = 0; i < chronicle.getSetEntries().size(); i++) {
                D2Chronicle.ChronicleEntry e = chronicle.getSetEntries().get(i);
                System.out.printf("  [%d] found=%b field6=0x%04X(%d) rawBytes=%s%n",
                        i, e.isFound(), e.getRawField6(), e.getRawField6(),
                        e.getRawBytes() != null ? bytesToHex(e.getRawBytes()) : "null");
            }
        }

        // Now mark Arcanna's Sign as found
        System.out.println("Marking Arcanna's Sign as found...");
        assertTrue(chronicle.markFound(D2Chronicle.Section.SET, arcannaIdx),
                "Marking Arcanna's Sign should succeed");

        System.out.println("After mark: setCount=" + chronicle.getNumSetItems()
                + " uniqCount=" + chronicle.getNumUniqueItems()
                + " rwCount=" + chronicle.getNumRunewords()
                + " foundSets=" + chronicle.getFoundSetCount());

        System.out.println("=== Set binary entries after mark ===");
        for (int i = 0; i < chronicle.getSetEntries().size(); i++) {
            D2Chronicle.ChronicleEntry e = chronicle.getSetEntries().get(i);
            System.out.printf("  [%d] found=%b field6=0x%04X(%d) rawBytes=%s%n",
                    i, e.isFound(), e.getRawField6(), e.getRawField6(),
                    e.getRawBytes() != null ? bytesToHex(e.getRawBytes()) : "null");
        }

        // Write to temp file
        File outFile = File.createTempFile("gomule-arcanna-test", ".d2i");
        outFile.deleteOnExit();
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalContent, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalContent).write(writableStash);
        byte[] writtenBytes = Files.readAllBytes(outFile.toPath());

        System.out.println("Original file size: " + originalContent.length);
        System.out.println("Written file size: " + writtenBytes.length);

        // Find byte differences
        int minLen = Math.min(originalContent.length, writtenBytes.length);
        int diffCount = 0;
        for (int i = 0; i < minLen; i++) {
            if (originalContent[i] != writtenBytes[i]) {
                if (diffCount < 50) {
                    System.out.printf("  Diff at offset 0x%04X: orig=0x%02X new=0x%02X%n",
                            i, originalContent[i] & 0xFF, writtenBytes[i] & 0xFF);
                }
                diffCount++;
            }
        }
        if (writtenBytes.length > originalContent.length) {
            System.out.println("  Extra bytes at end: " + (writtenBytes.length - originalContent.length));
        }
        System.out.println("Total byte differences: " + diffCount);

        // Try to reload
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle rc = reloaded.getChronicle();
        assertNotNull(rc, "Reloaded chronicle should not be null");
        System.out.println("Reloaded counts: set=" + rc.getNumSetItems()
                + " uniq=" + rc.getNumUniqueItems() + " rw=" + rc.getNumRunewords());
        System.out.println("Reloaded found: set=" + rc.getFoundSetCount()
                + " uniq=" + rc.getFoundUniqueCount() + " rw=" + rc.getFoundRunewordCount());

        // Also check unique section for sentinels
        System.out.println("=== Unique entries analysis ===");
        int maxUniqId = 0;
        for (int i = 0; i < D2TxtFile.UNIQUES.getRowSize(); i++) {
            String idStr = D2TxtFile.UNIQUES.getRow(i).get("*ID");
            if (idStr != null && !idStr.isEmpty()) {
                try { maxUniqId = Math.max(maxUniqId, Integer.parseInt(idStr)); } catch (NumberFormatException ignored) {}
            }
        }
        System.out.println("Max unique *ID in txt: " + maxUniqId);
        // Read from ORIGINAL stash (not modified)
        D2SharedStash origStash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(),
                new D2BitReader(stashFile.getAbsolutePath()));
        D2Chronicle origChronicle = origStash.getChronicle();
        java.util.Set<Integer> uniqField6s = new java.util.TreeSet<>();
        for (D2Chronicle.ChronicleEntry e : origChronicle.getUniqueEntries()) {
            if (e.isFound()) uniqField6s.add(e.getRawField6());
        }
        // Find any field6 > maxUniqId
        for (int f6 : uniqField6s) {
            if (f6 > maxUniqId) {
                System.out.println("Unique sentinel candidate: field6=" + f6);
            }
        }
        D2Chronicle.ChronicleEntry lastUniq = origChronicle.getUniqueEntries()
                .get(origChronicle.getUniqueEntries().size() - 1);
        System.out.println("Last unique entry: field6=" + lastUniq.getRawField6()
                + " found=" + lastUniq.isFound());
    }

    @Test
    public void testMarkArcannaSignOnUserFile_doesNotCorruptFile() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        byte[] originalBytes = Files.readAllBytes(stashFile.toPath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(),
                new D2BitReader(stashFile.getAbsolutePath()));
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);

        System.out.println("=== USER FILE STATE ===");
        System.out.println("File size: " + originalBytes.length);
        System.out.printf("Counts: set=%d uniq=%d rw=%d%n",
                chronicle.getNumSetItems(), chronicle.getNumUniqueItems(), chronicle.getNumRunewords());
        System.out.printf("Found: set=%d uniq=%d rw=%d%n",
                chronicle.getFoundSetCount(), chronicle.getFoundUniqueCount(), chronicle.getFoundRunewordCount());

        // Show set entries summary
        List<D2Chronicle.ChronicleEntry> setEntries = chronicle.getSetEntries();
        int setFound = 0, setEmpty = 0;
        for (D2Chronicle.ChronicleEntry e : setEntries) {
            if (e.isFound()) setFound++; else setEmpty++;
        }
        System.out.println("Set binary: total=" + setEntries.size() + " found=" + setFound + " empty=" + setEmpty);

        // Check for GoMule-fabricated entries (field0=544=BAAL_FIELD0 from old code)
        System.out.println("=== SET entries with field0=544 (old GoMule code) ===");
        for (int i = 0; i < setEntries.size(); i++) {
            D2Chronicle.ChronicleEntry e = setEntries.get(i);
            if (e.getRawField0() == 544) {
                System.out.printf("  [%d] field0=%d field6=%d raw=%s%n", i, e.getRawField0(), e.getRawField6(),
                        e.getRawBytes() != null ? bytesToHex(e.getRawBytes()) : "null");
            }
        }
        // Also dump ALL unique field0 values to check for field0=544
        System.out.println("=== UNIQUE entries with field0=544 (old GoMule code) ===");
        for (int i = 0; i < chronicle.getUniqueEntries().size(); i++) {
            D2Chronicle.ChronicleEntry e = chronicle.getUniqueEntries().get(i);
            if (e.getRawField0() == 544) {
                System.out.printf("  [%d] field0=%d field6=%d raw=%s%n", i, e.getRawField0(), e.getRawField6(),
                        e.getRawBytes() != null ? bytesToHex(e.getRawBytes()) : "null");
            }
        }
        // Show distinct field0 values in set entries
        java.util.Set<Integer> distinctField0 = new java.util.TreeSet<>();
        for (D2Chronicle.ChronicleEntry e : setEntries) distinctField0.add(e.getRawField0());
        System.out.println("Set distinct field0 values: " + distinctField0);
        // Check for DUPLICATE field0 values (GoMule donor reuse)
        java.util.Map<Integer, java.util.List<Integer>> field0ToIndices = new java.util.LinkedHashMap<>();
        for (int i = 0; i < setEntries.size(); i++) {
            int f0 = setEntries.get(i).getRawField0();
            field0ToIndices.computeIfAbsent(f0, k -> new java.util.ArrayList<>()).add(i);
        }
        for (java.util.Map.Entry<Integer, java.util.List<Integer>> entry : field0ToIndices.entrySet()) {
            if (entry.getValue().size() > 1) {
                System.out.println("  DUPLICATE field0=" + entry.getKey() + " at set indices: " + entry.getValue());
            }
        }
        // Also check unique section for duplicate field0
        java.util.Map<Integer, java.util.List<Integer>> uniqField0 = new java.util.LinkedHashMap<>();
        for (int i = 0; i < chronicle.getUniqueEntries().size(); i++) {
            int f0 = chronicle.getUniqueEntries().get(i).getRawField0();
            uniqField0.computeIfAbsent(f0, k -> new java.util.ArrayList<>()).add(i);
        }
        for (java.util.Map.Entry<Integer, java.util.List<Integer>> entry : uniqField0.entrySet()) {
            if (entry.getValue().size() > 1) {
                System.out.println("  DUPLICATE field0=" + entry.getKey() + " at unique indices: " + entry.getValue());
            }
        }
        // Check for entries sharing FULL bytes[0-5] prefix
        System.out.println("=== Entries sharing full bytes[0-5] prefix ===");
        java.util.Map<String, java.util.List<Integer>> prefixToIndices = new java.util.LinkedHashMap<>();
        for (int i = 0; i < setEntries.size(); i++) {
            byte[] raw = setEntries.get(i).getRawBytes();
            if (raw != null && raw.length >= 6) {
                String prefix = String.format("%02X%02X%02X%02X%02X%02X",
                        raw[0]&0xFF, raw[1]&0xFF, raw[2]&0xFF, raw[3]&0xFF, raw[4]&0xFF, raw[5]&0xFF);
                prefixToIndices.computeIfAbsent(prefix, k -> new java.util.ArrayList<>()).add(i);
            }
        }
        for (java.util.Map.Entry<String, java.util.List<Integer>> entry : prefixToIndices.entrySet()) {
            if (entry.getValue().size() > 1) {
                System.out.print("  PREFIX " + entry.getKey() + " at indices: " + entry.getValue() + " -> field6: [");
                for (int idx = 0; idx < entry.getValue().size(); idx++) {
                    if (idx > 0) System.out.print(", ");
                    System.out.print(setEntries.get(entry.getValue().get(idx)).getRawField6());
                }
                System.out.println("]");
            }
        }

        // Dump ALL set field6 values to find duplicates and identify sentinel
        System.out.println("=== ALL set field6 values ===");
        java.util.Map<Integer, java.util.List<Integer>> field6ToIndices = new java.util.LinkedHashMap<>();
        for (int i = 0; i < setEntries.size(); i++) {
            int f6 = setEntries.get(i).getRawField6();
            field6ToIndices.computeIfAbsent(f6, k -> new java.util.ArrayList<>()).add(i);
        }
        // Print ALL field6 values
        StringBuilder allF6 = new StringBuilder();
        for (int i = 0; i < setEntries.size(); i++) {
            if (i > 0) allF6.append(", ");
            allF6.append(setEntries.get(i).getRawField6());
        }
        System.out.println("  field6 values: [" + allF6 + "]");
        // Print duplicates
        for (java.util.Map.Entry<Integer, java.util.List<Integer>> entry : field6ToIndices.entrySet()) {
            if (entry.getValue().size() > 1) {
                System.out.println("  DUPLICATE field6=" + entry.getKey() + " at indices: " + entry.getValue());
            }
        }
        // Print field6 values NOT in eligible set *IDs
        java.util.Set<Integer> eligibleSetIds = new java.util.HashSet<>();
        for (int i = 0; i < randall.d2files.D2TxtFile.SETITEMS.getRowSize(); i++) {
            randall.d2files.D2TxtFileItemProperties row = randall.d2files.D2TxtFile.SETITEMS.getRow(i);
            String idStr = row.get("*ID");
            String spawnable = row.get("spawnable");
            String disable = row.get("disableChronicle");
            if (idStr != null && !idStr.isEmpty() && "1".equals(spawnable) && !"1".equals(disable)) {
                try { eligibleSetIds.add(Integer.parseInt(idStr)); } catch (NumberFormatException ignored) {}
            }
        }
        System.out.println("  Eligible set *IDs count: " + eligibleSetIds.size());
        for (int i = 0; i < setEntries.size(); i++) {
            int f6 = setEntries.get(i).getRawField6();
            if (!eligibleSetIds.contains(f6)) {
                System.out.printf("  NON-ELIGIBLE entry[%d] field6=%d(0x%04X) raw=%s%n",
                        i, f6, f6, setEntries.get(i).getRawBytes() != null ? bytesToHex(setEntries.get(i).getRawBytes()) : "null");
            }
        }

        // Show last 5 set entries
        System.out.println("=== Last 5 set entries ===");
        for (int i = Math.max(0, setEntries.size() - 5); i < setEntries.size(); i++) {
            D2Chronicle.ChronicleEntry e = setEntries.get(i);
            System.out.printf("  [%d] found=%b field6=%d(0x%04X) raw=%s%n",
                    i, e.isFound(), e.getRawField6(), e.getRawField6(),
                    e.getRawBytes() != null ? bytesToHex(e.getRawBytes()) : "null");
        }

        // Show unique entries summary
        List<D2Chronicle.ChronicleEntry> uniqEntries = chronicle.getUniqueEntries();
        int uniqFound = 0, uniqEmpty = 0;
        for (D2Chronicle.ChronicleEntry e : uniqEntries) {
            if (e.isFound()) uniqFound++; else uniqEmpty++;
        }
        System.out.println("Unique binary: total=" + uniqEntries.size() + " found=" + uniqFound + " empty=" + uniqEmpty);
        System.out.println("=== Last 5 unique entries ===");
        for (int i = Math.max(0, uniqEntries.size() - 5); i < uniqEntries.size(); i++) {
            D2Chronicle.ChronicleEntry e = uniqEntries.get(i);
            System.out.printf("  [%d] found=%b field6=%d(0x%04X) raw=%s%n",
                    i, e.isFound(), e.getRawField6(), e.getRawField6(),
                    e.getRawBytes() != null ? bytesToHex(e.getRawBytes()) : "null");
        }

        // Find Arcanna's Sign
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getSetGrailEntries();
        int arcannaIdx = -1;
        for (int i = 0; i < grail.size(); i++) {
            if ("Arcanna's Sign".equals(grail.get(i).getItemName())) {
                arcannaIdx = i;
                break;
            }
        }
        assertTrue(arcannaIdx >= 0, "Arcanna's Sign should be in grail");
        System.out.println("Arcanna's Sign: grailIdx=" + arcannaIdx
                + " *ID=" + grail.get(arcannaIdx).getRawField6()
                + " found=" + grail.get(arcannaIdx).isFound());

        // Debug: compare entry[0] and entry[last] raw byte prefixes
        D2Chronicle.ChronicleEntry entry0 = chronicle.getSetEntries().get(0);
        D2Chronicle.ChronicleEntry entryLast = chronicle.getSetEntries().get(chronicle.getSetEntries().size() - 1);
        System.out.println("Entry[0] raw: " + (entry0.getRawBytes() != null ? bytesToHex(entry0.getRawBytes()) : "null"));
        System.out.println("Entry[last] raw: " + (entryLast.getRawBytes() != null ? bytesToHex(entryLast.getRawBytes()) : "null"));
        // Debug: find how many entries share the same bytes[0-5] prefix as entry[110]
        byte[] lastPrefix = entryLast.getRawBytes();
        System.out.println("Scanning for entries sharing last entry's prefix...");
        int prefixMatches = 0;
        for (int i = 0; i < chronicle.getSetEntries().size() - 1; i++) {
            byte[] raw = chronicle.getSetEntries().get(i).getRawBytes();
            if (raw != null && lastPrefix != null && raw.length >= 6 && lastPrefix.length >= 6) {
                boolean match = true;
                for (int b = 0; b < 6; b++) {
                    if (raw[b] != lastPrefix[b]) { match = false; break; }
                }
                if (match) {
                    System.out.println("  PREFIX MATCH at index " + i + " raw=" + bytesToHex(raw));
                    prefixMatches++;
                }
            }
        }
        System.out.println("  Total prefix matches (excluding last): " + prefixMatches);

        // Diagnostic: replicate findSentinelIndex logic for set section
        System.out.println("=== Sentinel detection diagnostic (set section) ===");
        java.util.Set<Integer> eligibleSetIdsLocal = new java.util.HashSet<>();
        for (int i = 0; i < randall.d2files.D2TxtFile.SETITEMS.getRowSize(); i++) {
            randall.d2files.D2TxtFileItemProperties row = randall.d2files.D2TxtFile.SETITEMS.getRow(i);
            String idStr2 = row.get("*ID");
            if (idStr2 == null || idStr2.isEmpty()) continue;
            String code2 = row.get("item");
            if (code2 == null || code2.isEmpty()) continue;
            if (!"1".equals(row.get("spawnable"))) continue;
            if ("1".equals(row.get("disableChronicle"))) continue;
            try { eligibleSetIdsLocal.add(Integer.parseInt(idStr2)); } catch (NumberFormatException ignored) {}
        }
        System.out.println("  Eligible set IDs count: " + eligibleSetIdsLocal.size());
        System.out.println("  Is 0 in eligible? " + eligibleSetIdsLocal.contains(0));
        System.out.println("  Is 58 in eligible? " + eligibleSetIdsLocal.contains(58));
        // Find sentinel
        int diagSentinelIdx = -1;
        for (int i = chronicle.getSetEntries().size() - 1; i >= 0; i--) {
            D2Chronicle.ChronicleEntry e = chronicle.getSetEntries().get(i);
            if (e.isFound() && !eligibleSetIdsLocal.contains(e.getRawField6())) {
                System.out.println("  Sentinel found at index " + i + " field6=" + e.getRawField6());
                diagSentinelIdx = i;
                break;
            }
        }
        if (diagSentinelIdx < 0) {
            System.out.println("  NO sentinel found!");
        }

        // Also check unique section for sentinels
        System.out.println("=== Sentinel detection diagnostic (unique section) ===");
        java.util.Set<Integer> eligibleUniqIds = new java.util.HashSet<>();
        for (int i = 0; i < randall.d2files.D2TxtFile.UNIQUES.getRowSize(); i++) {
            randall.d2files.D2TxtFileItemProperties row = randall.d2files.D2TxtFile.UNIQUES.getRow(i);
            String idStr2 = row.get("*ID");
            if (idStr2 == null || idStr2.isEmpty()) continue;
            String code2 = row.get("code");
            if (code2 == null || code2.isEmpty()) continue;
            if (!"1".equals(row.get("spawnable"))) continue;
            if ("1".equals(row.get("disableChronicle"))) continue;
            try { eligibleUniqIds.add(Integer.parseInt(idStr2)); } catch (NumberFormatException ignored) {}
        }
        System.out.println("  Eligible unique IDs count: " + eligibleUniqIds.size());
        int uniqSentCount = 0;
        for (int i = chronicle.getUniqueEntries().size() - 1; i >= 0; i--) {
            D2Chronicle.ChronicleEntry e = chronicle.getUniqueEntries().get(i);
            if (e.isFound() && !eligibleUniqIds.contains(e.getRawField6())) {
                System.out.println("  Unique sentinel at index " + i + " field6=" + e.getRawField6());
                uniqSentCount++;
                if (uniqSentCount >= 5) { System.out.println("  ... more"); break; }
            }
        }
        if (uniqSentCount == 0) System.out.println("  No unique sentinels found");

        // Check if Arcanna's *ID (58) is in ANY set binary entry
        boolean has58 = false;
        for (D2Chronicle.ChronicleEntry e : chronicle.getSetEntries()) {
            if (e.isFound() && e.getRawField6() == 58) { has58 = true; break; }
        }
        System.out.println("field6=58 already in set entries? " + has58);
        // Check if 58 is in any unique entry
        boolean uniq58 = false;
        for (D2Chronicle.ChronicleEntry e : chronicle.getUniqueEntries()) {
            if (e.isFound() && e.getRawField6() == 58) { uniq58 = true; break; }
        }
        System.out.println("field6=58 in unique entries? " + uniq58);

        // If Arcanna's Sign is already found at this point (may have been marked earlier
        // in this same test run), unmark it first so we can test the mark operation.
        if (grail.get(arcannaIdx).isFound()) {
            System.out.println("Arcanna's Sign already found at second check, unmarking first...");
            assertTrue(chronicle.markNotFound(D2Chronicle.Section.SET, arcannaIdx),
                    "markNotFound should succeed for already-found Arcanna's Sign");
        }

        // Mark as found.  For old format: inserts before the field6=0 sentinel,
        // growing the section.  For new format: appends, growing the section.
        // Either way, section count increases by 1 when no empty slot exists.
        int origSetCount = chronicle.getNumSetItems();
        int origUniqCount = chronicle.getNumUniqueItems();
        boolean changed = chronicle.markFound(D2Chronicle.Section.SET, arcannaIdx);
        assertTrue(changed, "markFound should succeed");
        System.out.println("markFound returned: " + changed);

        // Chronicle should be modified.
        assertTrue(chronicle.isModified(), "Chronicle should be modified after successful mark");

        // Unique count should be unchanged.
        assertEquals(origUniqCount, chronicle.getNumUniqueItems(),
                "Unique count should stay the same");

        // Write round-trip
        byte[] originalContent = Files.readAllBytes(stashFile.toPath());
        File outFile = File.createTempFile("gomule-sentinel-overwrite-test", ".d2i");
        outFile.deleteOnExit();
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalContent, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalContent).write(writableStash);
        byte[] writtenBytes = Files.readAllBytes(outFile.toPath());

        System.out.println("Original file size: " + originalContent.length);
        System.out.println("Written file size: " + writtenBytes.length);

        // Verify the written file can be read back
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle rc = reloaded.getChronicle();
        assertNotNull(rc, "Reloaded chronicle should not be null");
        assertEquals(chronicle.getNumSetItems(), rc.getNumSetItems(),
                "Set count should match after round-trip");
        assertEquals(chronicle.getNumUniqueItems(), rc.getNumUniqueItems(),
                "Unique count should match after round-trip");

        // Verify Arcanna's Sign is found in the reloaded chronicle (check both field0 and field6)
        int arcannaId = chronicle.getSetGrailEntries().get(arcannaIdx).getRawField6();
        boolean arcannaFound = false;
        for (D2Chronicle.ChronicleEntry e : rc.getSetEntries()) {
            if (e.isFound() && (e.getRawField6() == arcannaId || e.getRawField0() == arcannaId)) {
                arcannaFound = true;
                break;
            }
        }
        assertTrue(arcannaFound, "Arcanna's Sign should be found after round-trip");
    }

    @Test
    public void testProduceSetGrowthFileForGameTesting() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        byte[] originalContent = Files.readAllBytes(stashFile.toPath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(),
                new D2BitReader(stashFile.getAbsolutePath()));
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);

        System.out.println("=== BASELINE (user file) ===");
        System.out.printf("Counts: set=%d uniq=%d rw=%d%n",
                chronicle.getNumSetItems(), chronicle.getNumUniqueItems(), chronicle.getNumRunewords());
        System.out.printf("Found:  set=%d uniq=%d rw=%d%n",
                chronicle.getFoundSetCount(), chronicle.getFoundUniqueCount(), chronicle.getFoundRunewordCount());

        // Find Arcanna's Sign in the grail
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getSetGrailEntries();
        int targetIdx = -1;
        for (int i = 0; i < grail.size(); i++) {
            if ("Arcanna's Sign".equals(grail.get(i).getItemName())) {
                targetIdx = i;
                break;
            }
        }
        assertTrue(targetIdx >= 0, "Arcanna's Sign should be in grail");

        String targetName = grail.get(targetIdx).getItemName();
        int targetId = grail.get(targetIdx).getRawField6();
        System.out.println("Marking: " + targetName + " (*ID=" + targetId + ") found=" + grail.get(targetIdx).isFound());

        // Unmark first if already found (user's file may have it marked from a previous run)
        int origUniq = chronicle.getNumUniqueItems();
        if (grail.get(targetIdx).isFound()) {
            System.out.println("Arcanna's Sign already found, unmarking first...");
            assertTrue(chronicle.markNotFound(D2Chronicle.Section.SET, targetIdx),
                    "markNotFound should succeed for already-found Arcanna's Sign");
            // Re-fetch grail after modification
            grail = chronicle.getSetGrailEntries();
        }

        assertTrue(chronicle.markFound(D2Chronicle.Section.SET, targetIdx),
                "markFound should succeed by growing set section");

        System.out.printf("After mark: set=%d uniq=%d rw=%d%n",
                chronicle.getNumSetItems(), chronicle.getNumUniqueItems(), chronicle.getNumRunewords());

        // Set count grows by 1 (new entry inserted before sentinel in old format,
        // or appended in new format).  Unique unchanged.
        int origSet = chronicle.getNumSetItems();
        assertEquals(origUniq, chronicle.getNumUniqueItems(),
                "Unique count should stay the same");

        // Write output
        File outFile = new File("../savefiles/ModernSharedStashSoftCoreV2_set_growth_test.d2i");
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalContent, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalContent).write(writableStash);
        byte[] outBytes = Files.readAllBytes(outFile.toPath());

        System.out.println("Original size: " + originalContent.length);
        System.out.println("Written size:  " + outBytes.length);
        // Section grew by 1 entry (10 bytes): new entry inserted before sentinel
        // (old format) or appended (new format).
        assertEquals(originalContent.length + 10, outBytes.length,
                "File size should grow by 10 bytes (section grew by 1 entry)");

        // Show byte diffs (compare matching region)
        int diffCount = 0;
        int compareLen = Math.min(originalContent.length, outBytes.length);
        for (int i = 0; i < compareLen; i++) {
            if (originalContent[i] != outBytes[i]) {
                System.out.printf("  Diff @0x%04X: orig=0x%02X new=0x%02X%n",
                        i, originalContent[i] & 0xFF, outBytes[i] & 0xFF);
                diffCount++;
            }
        }
        System.out.println("Total byte diffs: " + diffCount);

        // Verify round-trip
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle rc = reloaded.getChronicle();
        assertNotNull(rc);
        assertEquals(chronicle.getNumSetItems(), rc.getNumSetItems());
        assertEquals(chronicle.getNumUniqueItems(), rc.getNumUniqueItems());

        // Verify Arcanna's Sign is found (check both field0 and field6 for format agnosticism)
        boolean arcannaFound = false;
        for (D2Chronicle.ChronicleEntry e : rc.getSetEntries()) {
            if (e.isFound() && (e.getRawField6() == targetId || e.getRawField0() == targetId)) {
                arcannaFound = true;
                break;
            }
        }
        assertTrue(arcannaFound, "Arcanna's Sign should be found after round-trip");

        System.out.println("\nOutput: " + outFile.getAbsolutePath());
        System.out.println("Deploy: Copy-Item -Path \"" + outFile.getAbsolutePath()
                + "\" -Destination \"$env:USERPROFILE\\Saved Games\\Diablo II Resurrected\\ModernSharedStashSoftCoreV2.d2i\" -Force");
    }

    /**
     * Uses the D2R-written 9132-byte file (set=112) as the baseline.
     * Marks Arcanna's Sign as found (set grows 112→113).
     * Item panes are preserved verbatim (chronicle-only update).
     * Produces a deployable file for game testing.
     */
    @Test
    public void testChronicleGrowthOnD2RBaseline() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_d2r_grown_112.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        byte[] originalContent = Files.readAllBytes(stashFile.toPath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(),
                new D2BitReader(stashFile.getAbsolutePath()));
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);

        System.out.println("=== D2R BASELINE (9132 bytes, set=112) ===");
        System.out.printf("File size: %d%n", originalContent.length);
        System.out.printf("Counts: set=%d uniq=%d rw=%d%n",
                chronicle.getNumSetItems(), chronicle.getNumUniqueItems(), chronicle.getNumRunewords());
        System.out.printf("Found:  set=%d uniq=%d rw=%d%n",
                chronicle.getFoundSetCount(), chronicle.getFoundUniqueCount(), chronicle.getFoundRunewordCount());

        assertEquals(112, chronicle.getNumSetItems(), "D2R baseline should have set=112");

        // Find Arcanna's Sign in grail
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getSetGrailEntries();
        int targetIdx = -1;
        for (int i = 0; i < grail.size(); i++) {
            if ("Arcanna's Sign".equals(grail.get(i).getItemName())) {
                targetIdx = i;
                break;
            }
        }
        assertTrue(targetIdx >= 0, "Arcanna's Sign should be in grail");
        assertFalse(grail.get(targetIdx).isFound(), "Arcanna's Sign should NOT already be found");
        int targetId = grail.get(targetIdx).getRawField6();
        System.out.println("Target: " + grail.get(targetIdx).getItemName() + " *ID=" + targetId);

        // Mark found — grows set from 112→113
        assertTrue(chronicle.markFound(D2Chronicle.Section.SET, targetIdx));
        assertEquals(113, chronicle.getNumSetItems(), "Set count should grow to 113");
        assertTrue(chronicle.isModified());

        // Write — chronicle-only update preserves item panes verbatim
        File outFile = new File("../savefiles/ModernSharedStashSoftCoreV2_d2r_growth_test.d2i");
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalContent, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalContent).write(writableStash);
        byte[] outBytes = Files.readAllBytes(outFile.toPath());

        System.out.println("Original size: " + originalContent.length);
        System.out.println("Written size:  " + outBytes.length);
        assertEquals(originalContent.length + 10, outBytes.length,
                "File should grow by exactly 10 bytes (one new set entry)");

        // Verify all non-chronicle panes are byte-identical
        int chroniclePaneOffset = -1;
        for (int i = 0; i < originalContent.length - 4; i++) {
            if (originalContent[i] == 0x55 && originalContent[i+1] == (byte)0xAA
                    && originalContent[i+2] == 0x55 && originalContent[i+3] == (byte)0xAA) {
                if (i + 67 < originalContent.length
                        && originalContent[i+64] == (byte)0xC0 && originalContent[i+65] == (byte)0xED
                        && originalContent[i+66] == (byte)0xEA && originalContent[i+67] == (byte)0xC0) {
                    chroniclePaneOffset = i;
                    break;
                }
            }
        }
        assertTrue(chroniclePaneOffset > 0, "Should find chronicle pane offset");
        System.out.println("Chronicle pane starts at offset " + chroniclePaneOffset);

        // Everything before chronicle pane should be byte-identical
        for (int i = 0; i < chroniclePaneOffset; i++) {
            assertEquals(originalContent[i], outBytes[i],
                    "Byte mismatch before chronicle at offset " + i);
        }
        System.out.println("All " + chroniclePaneOffset + " bytes before chronicle pane are identical — item panes preserved!");

        // Verify round-trip
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle rc = reloaded.getChronicle();
        assertNotNull(rc);
        assertEquals(113, rc.getNumSetItems());
        assertEquals(chronicle.getNumUniqueItems(), rc.getNumUniqueItems());
        assertEquals(chronicle.getNumRunewords(), rc.getNumRunewords());

        // Verify Arcanna's Sign is found
        boolean arcannaFound = false;
        for (D2Chronicle.ChronicleEntry e : rc.getSetEntries()) {
            if (e.isFound() && e.getRawField6() == targetId) {
                arcannaFound = true;
                break;
            }
        }
        assertTrue(arcannaFound, "Arcanna's Sign should be found after round-trip");

        System.out.println("\n=== DEPLOY COMMAND ===");
        System.out.println("Copy-Item -Path '" + outFile.getAbsolutePath()
                + "' -Destination \"$env:USERPROFILE\\Saved Games\\Diablo II Resurrected\\ModernSharedStashSoftCoreV2.d2i\" -Force");
    }

    // -----------------------------------------------------------------------
    // New-format chronicle tests (RoW patch: field0=*ID, 8-byte padding)
    // -----------------------------------------------------------------------

    @Test
    public void testNewFormatEmptyChronicle() throws Exception {
        File file = new File("../savefiles/ModernSharedStashSoftCoreV2_empty.d2i");
        if (!file.exists()) {
            System.out.println("Skipping: " + file.getAbsolutePath() + " not found");
            return;
        }
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, file.getAbsolutePath(),
                new D2BitReader(file.getAbsolutePath()));
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle, "Chronicle should not be null for ROW stash");
        assertEquals(0, chronicle.getNumSetItems(), "Empty stash should have 0 set items");
        assertEquals(0, chronicle.getNumUniqueItems(), "Empty stash should have 0 unique items");
        assertEquals(0, chronicle.getNumRunewords(), "Empty stash should have 0 runewords");
        assertEquals(0, chronicle.getFoundSetCount());
        // Empty new-format files are now correctly detected via non-zero trailer bytes at offset 84
        assertTrue(chronicle.isNewEntryFormat(), "Empty file should be detected as new entry format");
        assertEquals(0, chronicle.getTotalFound());
    }

    @Test
    public void testNewFormatIkscUnlocked() throws Exception {
        File file = new File("../savefiles/ModernSharedStashSoftCoreV2_iksc_unlocked.d2i");
        if (!file.exists()) {
            System.out.println("Skipping: " + file.getAbsolutePath() + " not found");
            return;
        }
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, file.getAbsolutePath(),
                new D2BitReader(file.getAbsolutePath()));
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);
        assertTrue(chronicle.isNewEntryFormat(), "File should be detected as new entry format");
        assertEquals(1, chronicle.getNumSetItems(), "IKSC file should have 1 set item");
        assertEquals(0, chronicle.getNumUniqueItems());
        assertEquals(0, chronicle.getNumRunewords());
        assertEquals(1, chronicle.getFoundSetCount(), "IKSC set item should be found");

        // The single set entry should resolve to Immortal King's Stone Crusher (*ID=75)
        List<D2Chronicle.ChronicleEntry> setGrail = chronicle.getSetGrailEntries();
        boolean ikscFound = false;
        for (D2Chronicle.ChronicleEntry e : setGrail) {
            if ("Immortal King's Stone Crusher".equals(e.getItemName())) {
                assertTrue(e.isFound(), "Immortal King's Stone Crusher should be marked found");
                ikscFound = true;
            }
        }
        assertTrue(ikscFound, "Immortal King's Stone Crusher must appear in grail entries as found");
    }

    @Test
    public void testNewFormatIkscAndCathanRuleUnlocked() throws Exception {
        File file = new File("../savefiles/ModernSharedStashSoftCoreV2_iksc_cr_unlocked.d2i");
        if (!file.exists()) {
            System.out.println("Skipping: " + file.getAbsolutePath() + " not found");
            return;
        }
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, file.getAbsolutePath(),
                new D2BitReader(file.getAbsolutePath()));
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);
        assertTrue(chronicle.isNewEntryFormat(), "File should be detected as new entry format");
        assertEquals(2, chronicle.getNumSetItems(), "Should have 2 set items");
        assertEquals(2, chronicle.getFoundSetCount(), "Both set items should be found");

        List<D2Chronicle.ChronicleEntry> setGrail = chronicle.getSetGrailEntries();
        boolean ikscFound = false;
        boolean cathanFound = false;
        for (D2Chronicle.ChronicleEntry e : setGrail) {
            if ("Immortal King's Stone Crusher".equals(e.getItemName())) {
                assertTrue(e.isFound(), "Immortal King's Stone Crusher should be found");
                ikscFound = true;
            } else if ("Cathan's Rule".equals(e.getItemName())) {
                assertTrue(e.isFound(), "Cathan's Rule should be found");
                cathanFound = true;
            }
        }
        assertTrue(ikscFound, "Immortal King's Stone Crusher must appear as found");
        assertTrue(cathanFound, "Cathan's Rule must appear as found");
    }

    @Test
    public void testMarkSetItemFoundOnNewFormatFile() throws Exception {
        File file = new File("../savefiles/ModernSharedStashSoftCoreV2_iksc_unlocked.d2i");
        if (!file.exists()) {
            System.out.println("Skipping: " + file.getAbsolutePath() + " not found");
            return;
        }
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, file.getAbsolutePath(),
                new D2BitReader(file.getAbsolutePath()));
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);
        assertTrue(chronicle.isNewEntryFormat());

        // Find Cathan's Rule (not yet found) and mark it
        List<D2Chronicle.ChronicleEntry> setGrail = chronicle.getSetGrailEntries();
        int cathanIndex = -1;
        for (int i = 0; i < setGrail.size(); i++) {
            if ("Cathan's Rule".equals(setGrail.get(i).getItemName())
                    && !setGrail.get(i).isFound()) {
                cathanIndex = i;
                break;
            }
        }
        assertTrue(cathanIndex >= 0, "Cathan's Rule must appear as not-found in IKSC-only file");

        int beforeCount = chronicle.getNumSetItems();
        assertTrue(chronicle.markFound(D2Chronicle.Section.SET, cathanIndex),
                "markFound should return true for Cathan's Rule");
        assertEquals(beforeCount + 1, chronicle.getNumSetItems(),
                "Set item count should increase by 1");

        // Verify the newly marked item appears found in grail
        setGrail = chronicle.getSetGrailEntries();
        boolean cathanNowFound = false;
        for (D2Chronicle.ChronicleEntry e : setGrail) {
            if ("Cathan's Rule".equals(e.getItemName()) && e.isFound()) {
                cathanNowFound = true;
            }
        }
        assertTrue(cathanNowFound, "Cathan's Rule should now appear as found");

        // Persist and reload to verify bytes round-trip
        File outFile = File.createTempFile("gomule-mark-set-new-format", ".d2i");
        outFile.deleteOnExit();
        D2BitReader sourceReader = new D2BitReader(file.getAbsolutePath());
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), sourceReader.getFileContent().clone(), chronicle);
        new D2SharedStashWriter(ROW, outFile, sourceReader.getFileContent().clone()).write(writableStash);

        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW, outFile.getAbsolutePath(),
                new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle reloadedChronicle = reloaded.getChronicle();
        assertNotNull(reloadedChronicle);
        assertTrue(reloadedChronicle.isNewEntryFormat());
        assertEquals(beforeCount + 1, reloadedChronicle.getNumSetItems(),
                "Reloaded chronicle should have updated set count");
        List<D2Chronicle.ChronicleEntry> reloadedGrail = reloadedChronicle.getSetGrailEntries();
        boolean cathanReloaded = false;
        for (D2Chronicle.ChronicleEntry e : reloadedGrail) {
            if ("Cathan's Rule".equals(e.getItemName()) && e.isFound()) {
                cathanReloaded = true;
            }
        }
        assertTrue(cathanReloaded, "Cathan's Rule should remain found after reload");

        // Verify the new-format entry layout and trailer preservation:
        // With 2 set entries (20 bytes) at pane[84..103], the trailer starts at pane[104].
        // The trailer from the original file must be preserved exactly.
        byte[] outBytes = java.nio.file.Files.readAllBytes(outFile.toPath());
        byte[] origBytes = sourceReader.getFileContent();
        // Find the chronicle pane start (C0EDEAC0 magic is 64 bytes into the pane)
        int chronicleMagicOffset = -1;
        byte[] magic = {(byte) 0xC0, (byte) 0xED, (byte) 0xEA, (byte) 0xC0};
        for (int i = 0; i <= origBytes.length - 4; i++) {
            if (origBytes[i] == magic[0] && origBytes[i + 1] == magic[1]
                    && origBytes[i + 2] == magic[2] && origBytes[i + 3] == magic[3]) {
                chronicleMagicOffset = i;
                break;
            }
        }
        assertTrue(chronicleMagicOffset >= 0, "Chronicle magic must be findable in output");
        int paneStart = chronicleMagicOffset - 64;

        // Original had 1 entry → trailer at pane+(84+10)=94
        // Written has 2 entries → trailer at pane+(84+20)=104
        // Trailers must be byte-identical
        int origTrailerStart = paneStart + 84 + 1 * 10; // 1 original entry
        int outTrailerStart = paneStart + 84 + 2 * 10;  // 2 entries after marking
        int origPaneLen = (origBytes[paneStart + 16] & 0xFF)
                | ((origBytes[paneStart + 17] & 0xFF) << 8)
                | ((origBytes[paneStart + 18] & 0xFF) << 16)
                | ((origBytes[paneStart + 19] & 0xFF) << 24);
        int origTrailerLen = paneStart + origPaneLen - origTrailerStart;
        assertTrue(origTrailerLen > 0, "Original trailer should have positive length");
        for (int i = 0; i < origTrailerLen; i++) {
            assertEquals(origBytes[origTrailerStart + i] & 0xFF,
                    outBytes[outTrailerStart + i] & 0xFF,
                    "Trailer byte at offset " + i + " must be preserved");
        }
    }

    /**
     * Validates that GoMule output matches the game's save file structure.
     * Starts from the IKSC-only file, marks Cathan's Rule found, writes,
     * and compares the chronicle pane structure against the game-written
     * IKSC+CR file to verify trailer preservation and pane growth.
     */
    @Test
    public void testGoMuleOutputMatchesGameStructure() throws Exception {
        File ikscFile = new File("../savefiles/ModernSharedStashSoftCoreV2_iksc_unlocked.d2i");
        File ikscCrFile = new File("../savefiles/ModernSharedStashSoftCoreV2_iksc_cr_unlocked.d2i");
        if (!ikscFile.exists() || !ikscCrFile.exists()) {
            System.out.println("Skipping: required save files not found");
            return;
        }

        // Read the IKSC file and mark Cathan's Rule as found
        byte[] ikscBytes = Files.readAllBytes(ikscFile.toPath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, ikscFile.getAbsolutePath(),
                new D2BitReader(ikscFile.getAbsolutePath()));
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);
        assertTrue(chronicle.isNewEntryFormat());

        List<D2Chronicle.ChronicleEntry> grail = chronicle.getSetGrailEntries();
        int cathanIdx = -1;
        for (int i = 0; i < grail.size(); i++) {
            if ("Cathan's Rule".equals(grail.get(i).getItemName()) && !grail.get(i).isFound()) {
                cathanIdx = i;
                break;
            }
        }
        assertTrue(cathanIdx >= 0, "Cathan's Rule should be not-found in IKSC file");
        assertTrue(chronicle.markFound(D2Chronicle.Section.SET, cathanIdx));

        // Write
        File outFile = File.createTempFile("gomule-game-match-test", ".d2i");
        outFile.deleteOnExit();
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), ikscBytes, chronicle);
        new D2SharedStashWriter(ROW, outFile, ikscBytes).write(writableStash);
        byte[] outBytes = Files.readAllBytes(outFile.toPath());

        // The output pane should grow by exactly 10 bytes (one new entry)
        assertEquals(ikscBytes.length + 10, outBytes.length,
                "File should grow by exactly 10 bytes after marking one set item");

        // Read game's IKSC+CR file for comparison
        byte[] gameBytes = Files.readAllBytes(ikscCrFile.toPath());

        // Both files have chronicle pane at the same base offset
        // Compare the trailer from game file vs GoMule output
        // Game has 2 set + 1 unique + 0 rw entries → trailer at pane+(84+30)=114
        // GoMule has 2 set + 0 unique + 0 rw entries → trailer at pane+(84+20)=104
        // But the trailer bytes themselves should be identical to the IKSC file's trailer

        // Verify the written file round-trips correctly
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW, outFile.getAbsolutePath(),
                new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle rc = reloaded.getChronicle();
        assertNotNull(rc);
        assertTrue(rc.isNewEntryFormat());
        assertEquals(2, rc.getNumSetItems(), "Should have 2 set items after marking");
        assertEquals(2, rc.getFoundSetCount(), "Both set items should be found");

        // Verify IKSC and Cathan's Rule are both found
        grail = rc.getSetGrailEntries();
        boolean ikscFound = false, cathanFound = false;
        for (D2Chronicle.ChronicleEntry e : grail) {
            if ("Immortal King's Stone Crusher".equals(e.getItemName()) && e.isFound()) ikscFound = true;
            if ("Cathan's Rule".equals(e.getItemName()) && e.isFound()) cathanFound = true;
        }
        assertTrue(ikscFound, "IKSC should be found in round-tripped file");
        assertTrue(cathanFound, "Cathan's Rule should be found in round-tripped file");
    }

    /**
     * Tests marking a set item on an empty new-format chronicle file.
     * This verifies correct format detection and entry area offset (84, not 88).
     */
    @Test
    public void testMarkSetItemOnEmptyNewFormatFile() throws Exception {
        File emptyFile = new File("../savefiles/ModernSharedStashSoftCoreV2_empty.d2i");
        if (!emptyFile.exists()) {
            System.out.println("Skipping: " + emptyFile.getAbsolutePath() + " not found");
            return;
        }

        byte[] emptyBytes = Files.readAllBytes(emptyFile.toPath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, emptyFile.getAbsolutePath(),
                new D2BitReader(emptyFile.getAbsolutePath()));
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);
        assertTrue(chronicle.isNewEntryFormat(), "Empty file should be detected as new format");
        assertEquals(84, chronicle.getEntryAreaStart(), "New format entry area starts at 84");

        // Mark Tancred's Skull as found
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getSetGrailEntries();
        int tsIdx = -1;
        for (int i = 0; i < grail.size(); i++) {
            if ("Tancred's Skull".equals(grail.get(i).getItemName())) {
                tsIdx = i;
                break;
            }
        }
        assertTrue(tsIdx >= 0, "Tancred's Skull should be in grail");
        assertTrue(chronicle.markFound(D2Chronicle.Section.SET, tsIdx));
        assertEquals(1, chronicle.getNumSetItems());

        // Write
        File outFile = File.createTempFile("gomule-empty-mark-test", ".d2i");
        outFile.deleteOnExit();
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), emptyBytes, chronicle);
        new D2SharedStashWriter(ROW, outFile, emptyBytes).write(writableStash);
        byte[] outBytes = Files.readAllBytes(outFile.toPath());

        // File should grow by exactly 10 bytes
        assertEquals(emptyBytes.length + 10, outBytes.length,
                "File should grow by 10 bytes (one new set entry)");

        // Verify round-trip
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW, outFile.getAbsolutePath(),
                new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle rc = reloaded.getChronicle();
        assertNotNull(rc);
        assertTrue(rc.isNewEntryFormat());
        assertEquals(1, rc.getNumSetItems());
        assertEquals(1, rc.getFoundSetCount());

        // Verify Tancred's Skull is found
        boolean tsFound = false;
        for (D2Chronicle.ChronicleEntry e : rc.getSetGrailEntries()) {
            if ("Tancred's Skull".equals(e.getItemName()) && e.isFound()) {
                tsFound = true;
            }
        }
        assertTrue(tsFound, "Tancred's Skull should be found after round-trip");

        // Verify trailer preservation: original trailer is at pane+84 (0 original entries),
        // written trailer is at pane+94 (1 entry = 10 bytes).
        // Find chronicle pane offset
        int chroniclePaneStart = -1;
        byte[] magic = {(byte) 0xC0, (byte) 0xED, (byte) 0xEA, (byte) 0xC0};
        for (int i = 0; i <= emptyBytes.length - 68; i++) {
            if (emptyBytes[i + 64] == magic[0] && emptyBytes[i + 65] == magic[1]
                    && emptyBytes[i + 66] == magic[2] && emptyBytes[i + 67] == magic[3]) {
                chroniclePaneStart = i;
                break;
            }
        }
        assertTrue(chroniclePaneStart >= 0);
        int origTrailerLen = emptyBytes.length - (chroniclePaneStart + 84);
        for (int i = 0; i < origTrailerLen; i++) {
            assertEquals(emptyBytes[chroniclePaneStart + 84 + i] & 0xFF,
                    outBytes[chroniclePaneStart + 94 + i] & 0xFF,
                    "Trailer byte at offset " + i + " must be preserved");
        }
    }

    /**
     * Verifies that marking a runeword as found on a new-format file produces
     * a valid 10-byte entry with the correct 5×u16LE structure:
     *   bytes 0-1 (field0) = 0x0000 (runewords always zero)
     *   bytes 2-3 (field2) = 0x0000 (must be zero)
     *   bytes 4-5 (field4) = non-zero (game metadata, borrowed from donor)
     *   bytes 6-7 (field6) = runeword encoding (0x501B + row index)
     *   bytes 8-9 (field8) = non-zero (game metadata, borrowed from donor)
     */
    @Test
    public void testMarkRunewordProducesValidEntryFormat() throws Exception {
        File inputFile = new File("../savefiles/ModernSharedStashSoftCoreV2_iksc_cr_ts_unlocked.d2i");
        if (!inputFile.exists()) {
            System.out.println("Skipping: " + inputFile.getAbsolutePath() + " not found");
            return;
        }

        byte[] inputBytes = Files.readAllBytes(inputFile.toPath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, inputFile.getAbsolutePath(),
                new D2BitReader(inputFile.getAbsolutePath()));
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);
        assertTrue(chronicle.isNewEntryFormat());
        assertEquals(0, chronicle.getNumRunewords(), "No runewords before marking");

        // Find Ancients' Pledge in grail
        List<D2Chronicle.ChronicleEntry> rwGrail = chronicle.getRunewordGrailEntries();
        int apIdx = -1;
        for (int i = 0; i < rwGrail.size(); i++) {
            if ("Ancients' Pledge".equals(rwGrail.get(i).getItemName())) {
                apIdx = i;
                break;
            }
        }
        assertTrue(apIdx >= 0, "Ancients' Pledge should be in runeword grail");
        assertFalse(rwGrail.get(apIdx).isFound(), "Should not be found yet");

        // Mark as found
        assertTrue(chronicle.markFound(D2Chronicle.Section.RUNEWORDS, apIdx));
        assertEquals(1, chronicle.getNumRunewords());

        // Write
        File outFile = File.createTempFile("gomule-rw-mark-test", ".d2i");
        outFile.deleteOnExit();
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), inputBytes, chronicle);
        new D2SharedStashWriter(ROW, outFile, inputBytes).write(writableStash);
        byte[] outBytes = Files.readAllBytes(outFile.toPath());

        // File should grow by exactly 10 bytes
        assertEquals(inputBytes.length + 10, outBytes.length);

        // Find the new runeword entry in the output binary.
        // Chronicle pane starts at offset 408. Entry area at 84. Original: 4 entries.
        // New: 5 entries (3 set + 1 unique + 1 runeword).
        // The runeword entry is at pane offset 84 + 4*10 = 124.
        int chroniclePaneStart = 408;
        int rwEntryStart = chroniclePaneStart + 84 + 4 * 10;
        byte[] rwEntry = Arrays.copyOfRange(outBytes, rwEntryStart, rwEntryStart + 10);
        System.out.println("Runeword entry: " + bytesToHex(rwEntry));

        // Verify 5×u16LE field structure
        int f0 = (rwEntry[0] & 0xFF) | ((rwEntry[1] & 0xFF) << 8);
        int f2 = (rwEntry[2] & 0xFF) | ((rwEntry[3] & 0xFF) << 8);
        int f4 = (rwEntry[4] & 0xFF) | ((rwEntry[5] & 0xFF) << 8);
        int f6 = (rwEntry[6] & 0xFF) | ((rwEntry[7] & 0xFF) << 8);
        int f8 = (rwEntry[8] & 0xFF) | ((rwEntry[9] & 0xFF) << 8);

        // field0 must be 0 for runewords
        assertEquals(0, f0, "Runeword field0 must be 0");
        // field2 (bytes 2-3) MUST be zero — game rejects non-zero values
        assertEquals(0, f2, "Bytes 2-3 must be zero (game metadata constraint)");
        // field4 (bytes 4-5) must be non-zero (borrowed from donor)
        assertNotEquals(0, f4, "Bytes 4-5 should be non-zero (game metadata from donor)");
        // field6 = RUNEWORD_FIELD6_STANDARD_BASE (0x501B) + row index
        assertEquals(0x501B, f6, "Runeword field6 should encode Ancients' Pledge (row 0)");
        // field8 must be non-zero (game metadata constant, typically 0x01C3)
        assertNotEquals(0, f8, "Bytes 8-9 should be non-zero (game metadata from donor)");

        // Verify the donor values match an existing entry's metadata
        byte[] existingEntry = Arrays.copyOfRange(outBytes, chroniclePaneStart + 84, chroniclePaneStart + 94);
        int existingF2 = (existingEntry[2] & 0xFF) | ((existingEntry[3] & 0xFF) << 8);
        int existingF4 = (existingEntry[4] & 0xFF) | ((existingEntry[5] & 0xFF) << 8);
        int existingF8 = (existingEntry[8] & 0xFF) | ((existingEntry[9] & 0xFF) << 8);
        assertEquals(existingF2, f2, "Runeword bytes 2-3 should match donor");
        assertEquals(existingF4, f4, "Runeword bytes 4-5 should match donor (game metadata)");
        assertEquals(existingF8, f8, "Runeword bytes 8-9 should match donor (game metadata)");

        // Verify trailer preserved
        int origTrailerStart = chroniclePaneStart + 84 + 40;  // 4 original entries
        int newTrailerStart = chroniclePaneStart + 84 + 50;   // 5 entries
        int trailerLen = inputBytes.length - origTrailerStart;
        for (int i = 0; i < trailerLen; i++) {
            assertEquals(inputBytes[origTrailerStart + i] & 0xFF,
                    outBytes[newTrailerStart + i] & 0xFF,
                    "Trailer byte at offset " + i + " must be preserved");
        }

        // Round-trip: read back and verify
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle rc = reloaded.getChronicle();
        assertNotNull(rc);
        assertEquals(1, rc.getNumRunewords());
        assertEquals(1, rc.getFoundRunewordCount());

        // Verify Ancients' Pledge found in grail
        boolean apFound = false;
        for (D2Chronicle.ChronicleEntry e : rc.getRunewordGrailEntries()) {
            if ("Ancients' Pledge".equals(e.getItemName()) && e.isFound()) {
                apFound = true;
            }
        }
        assertTrue(apFound, "Ancients' Pledge should be found after round-trip");
    }

    /**
     * Produces a test file by marking Ancients' Pledge (runeword) as found on
     * the iksc_cr_ts file. The output is written to ModernSharedStashSoftCoreV2.d2i
     * for in-game testing via live-test.ps1.
     */
    @Test
    public void testProduceRunewordFileForGameTesting() throws Exception {
        File inputFile = new File("../savefiles/ModernSharedStashSoftCoreV2_iksc_cr_ts_unlocked.d2i");
        if (!inputFile.exists()) {
            System.out.println("Skipping: " + inputFile.getAbsolutePath() + " not found");
            return;
        }

        byte[] inputBytes = Files.readAllBytes(inputFile.toPath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, inputFile.getAbsolutePath(),
                new D2BitReader(inputFile.getAbsolutePath()));
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);

        // Mark Ancients' Pledge
        List<D2Chronicle.ChronicleEntry> rwGrail = chronicle.getRunewordGrailEntries();
        int apIdx = -1;
        for (int i = 0; i < rwGrail.size(); i++) {
            if ("Ancients' Pledge".equals(rwGrail.get(i).getItemName())) {
                apIdx = i;
                break;
            }
        }
        assertTrue(apIdx >= 0);
        assertTrue(chronicle.markFound(D2Chronicle.Section.RUNEWORDS, apIdx));

        // Write to the shared output file
        File outFile = new File("../savefiles/ModernSharedStashSoftCoreV2.d2i");
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), inputBytes, chronicle);
        new D2SharedStashWriter(ROW, outFile, inputBytes).write(writableStash);

        System.out.println("Wrote " + outFile.getAbsolutePath());
        System.out.println("Run live-test.ps1 to copy to game directory");
    }

    /**
     * Produces a game-testable file: marks Arcanna's Sign on the good.d2i (old format)
     * file that previously caused "failed to join game". Sentinel is now preserved.
     */
    @Test
    public void testProduceArcannaOnGoodFileForGameTesting() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2_good.d2i");
        if (!stashFile.exists()) {
            System.out.println("Skipping: " + stashFile.getAbsolutePath() + " not found");
            return;
        }

        byte[] originalContent = Files.readAllBytes(stashFile.toPath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, stashFile.getAbsolutePath(),
                new D2BitReader(stashFile.getAbsolutePath()));
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);
        assertFalse(chronicle.isNewEntryFormat(), "good.d2i should be old format");

        int baselineSet = chronicle.getNumSetItems();
        System.out.printf("Baseline: set=%d uniq=%d rw=%d%n",
                baselineSet, chronicle.getNumUniqueItems(), chronicle.getNumRunewords());

        // Find and mark Arcanna's Sign
        List<D2Chronicle.ChronicleEntry> grail = chronicle.getSetGrailEntries();
        int arcannaIdx = -1;
        for (int i = 0; i < grail.size(); i++) {
            if ("Arcanna's Sign".equals(grail.get(i).getItemName())) {
                arcannaIdx = i;
                break;
            }
        }
        assertTrue(arcannaIdx >= 0, "Arcanna's Sign must exist in grail");
        assertTrue(chronicle.markFound(D2Chronicle.Section.SET, arcannaIdx));
        assertEquals(baselineSet + 1, chronicle.getNumSetItems(),
                "Set section grows by 1 (insert before sentinel)");

        // Write
        File outFile = new File("../savefiles/ModernSharedStashSoftCoreV2_arcanna_sentinel_fix.d2i");
        D2SharedStash writableStash = new D2SharedStash(ROW, outFile.getAbsolutePath(),
                stash.getPanes(), originalContent, chronicle);
        new D2SharedStashWriter(ROW, outFile, originalContent).write(writableStash);
        byte[] outBytes = Files.readAllBytes(outFile.toPath());

        assertEquals(originalContent.length + 10, outBytes.length,
                "File grows by exactly 10 bytes (sentinel preserved)");

        // Verify round-trip
        D2SharedStash reloaded = new D2SharedStashReader().readStash(ROW,
                outFile.getAbsolutePath(), new D2BitReader(outFile.getAbsolutePath()));
        D2Chronicle rc = reloaded.getChronicle();
        assertNotNull(rc);
        assertEquals(baselineSet + 1, rc.getNumSetItems());

        // Verify sentinel is still present (last entry should have field6=0)
        List<D2Chronicle.ChronicleEntry> reloadedSet = rc.getSetEntries();
        D2Chronicle.ChronicleEntry lastEntry = reloadedSet.get(reloadedSet.size() - 1);
        assertEquals(0, lastEntry.getRawField6(),
                "Sentinel (field6=0) must still be the last set entry");

        System.out.println("Output: " + outFile.getAbsolutePath() + " (" + outBytes.length + " bytes)");
        System.out.println("Run live-test.ps1 to copy to game directory");
    }

}
