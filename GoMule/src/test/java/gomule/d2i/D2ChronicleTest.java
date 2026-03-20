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
import java.util.ArrayList;
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
        File file = new File("../savefiles/ModernSharedStashSoftCoreV2.d2i");
        if (!file.exists()) {
            System.out.println("Skipping: " + file.getAbsolutePath() + " not found");
            return;
        }
        D2BitReader bitReader = new D2BitReader(file.getAbsolutePath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, file.getAbsolutePath(), bitReader);

        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle, "Chronicle should not be null for ROW stash");
        assertEquals(1, chronicle.getVersion());
        assertEquals(106, chronicle.getNumSetItems());
        assertEquals(226, chronicle.getNumUniqueItems());
        assertEquals(27, chronicle.getNumRunewords());
        assertEquals(359, chronicle.getTotalItems());

        // All slots in this stash are non-zero (complete grail for the tracked range)
        assertEquals(106, chronicle.getFoundSetCount());
        assertEquals(226, chronicle.getFoundUniqueCount());
        assertEquals(27, chronicle.getFoundRunewordCount());
        assertEquals(359, chronicle.getTotalFound());

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
    public void testAnalyzeRunewordsAgainstManualRecord() throws Exception {
        File stashFile = new File("../savefiles/ModernSharedStashSoftCoreV2.d2i");
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

        assertEquals(27, chronicleNames.size());
        assertEquals(27, manualCanonical.size());
    }

    private static List<String> readManualRunewords(File manualFile) throws Exception {
        List<String> result = new ArrayList<>();
        boolean inRunewordSection = false;
        boolean sawSectionHeader = false;
        try (BufferedReader br = new BufferedReader(new FileReader(manualFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.toLowerCase(Locale.ROOT).startsWith("rune words")) {
                    inRunewordSection = true;
                    sawSectionHeader = true;
                    continue;
                }
                if (inRunewordSection
                        && (trimmed.toLowerCase(Locale.ROOT).startsWith("set:")
                        || trimmed.toLowerCase(Locale.ROOT).startsWith("unique:"))) {
                    break;
                }
                if (inRunewordSection && !trimmed.isEmpty()) {
                    result.add(trimmed);
                }
                if (!sawSectionHeader && !trimmed.isEmpty()) {
                    // Support plain one-name-per-line files.
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
}
