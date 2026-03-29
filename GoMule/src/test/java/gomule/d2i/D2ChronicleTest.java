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
}
