package gomule.d2i;

import com.google.common.io.BaseEncoding;
import gomule.item.D2ItemRenderer;
import gomule.model.VersionController.VersionException;
import gomule.util.D2BitReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Collectors;

import static gomule.d2i.D2SharedStashWriterTest.EMPTY_STASH;
import static gomule.item.D2ItemTest.decode;
import static gomule.model.VersionController.Variant.EXPANSION;
import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

public class D2SharedStashReaderTest {

    @BeforeAll
    public static void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    @Test
    public void simpleExpansionStash() throws Exception {
        byte[] simpleStash = BaseEncoding.base16()
                .decode(
                        "55AA55AA0200000069000000F2A416004D00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D01001000A2000564D6900855AA55AA0200000069000000000000004E00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D01001000A2000564F647220055AA55AA0200000069000000000000004400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D0000");
        D2SharedStash stash =
                new D2SharedStashReader().readStash(EXPANSION, "somethingSoftCore.d2i", new D2BitReader(simpleStash));
        assertEquals(1484018, stash.getPane(0).getGold());
        assertEquals(0, stash.getPane(1).getGold());
        assertEquals(0, stash.getPane(2).getGold());
        assertTrue(stash.isSC());
        assertFalse(stash.isHC());
        assertEquals(
                singletonList("Scroll of Town Portal\n" + "Version: Resurrected\n"), getItemDumps(stash.getPane(0)));
        assertEquals(singletonList("Scroll of Identify\n" + "Version: Resurrected\n"), getItemDumps(stash.getPane(1)));
        assertEquals(emptyList(), getItemDumps(stash.getPane(2)));
        assertNull(stash.getChronicle());
        StringWriter out = new StringWriter();
        stash.fullDump(new PrintWriter(out));
        assertEquals(
                "somethingSoftCore.d2i\n" + "\n"
                        + "\n"
                        + "Scroll of Town Portal\n"
                        + "Version: Resurrected\n"
                        + "\n"
                        + "Scroll of Identify\n"
                        + "Version: Resurrected\n"
                        + "Finished: somethingSoftCore.d2i\n\n",
                out.toString().replaceAll("\r", ""));
    }

    @Test
    public void simpleRowStash() throws Exception {
        D2BitReader bitReader = new D2BitReader(loadTestResources("sharedStashFiles", "modern.d2i").getAbsolutePath());
        D2SharedStash stash =
                new D2SharedStashReader().readStash(ROW, "somethingSoftCore.d2i", bitReader);
        assertEquals(1, stash.getPane(0).getGold());
        assertEquals(0, stash.getPane(1).getGold());
        assertEquals(0, stash.getPane(2).getGold());
        assertEquals(0, stash.getPane(3).getGold());
        assertEquals(0, stash.getPane(4).getGold());
        assertTrue(stash.isSC());
        assertFalse(stash.isHC());
        assertEquals(
                singletonList("Super Mana Potion\n" +
                        "Version: Resurrected\n" +
                        "Replenishes Mana 250%\n"), getItemDumps(stash.getPane(0)));
        assertEquals(singletonList("Super Healing Potion\n" +
                "Version: Resurrected\n" +
                "Replenish Life +320\n"), getItemDumps(stash.getPane(1)));

        // Verify chronicle was parsed
        D2Chronicle chronicle = stash.getChronicle();
        assertNotNull(chronicle);
        assertEquals(1, chronicle.getVersion());
        assertEquals(0, chronicle.getNumSetItems());
        assertEquals(0, chronicle.getNumUniqueItems());
        assertEquals(0, chronicle.getNumRunewords());
        assertEquals(0, chronicle.getTotalFound());
        assertEquals(0, chronicle.getTotalItems());

        assertEquals(singletonList("Stamina Potion\n" +
                "Version: Resurrected\n" +
                "Heal Stamina Plus 5000%\n"), getItemDumps(stash.getPane(2)));
        assertEquals(singletonList("Thawing Potion\n" +
                "Version: Resurrected\n" +
                "+10% to Maximum Cold Resist\n" +
                "Cold Resist +50%\n"), getItemDumps(stash.getPane(3)));
        assertEquals(singletonList("Antidote Potion\n" +
                "Version: Resurrected\n" +
                "+10% to Maximum Poison Resist\n" +
                "Poison Resist +50%\n"), getItemDumps(stash.getPane(4)));
        StringWriter out = new StringWriter();
        stash.fullDump(new PrintWriter(out));
        assertEquals(
                "somethingSoftCore.d2i\n" +
                        "\n" +
                        "\n" +
                        "Super Mana Potion\n" +
                        "Version: Resurrected\n" +
                        "Replenishes Mana 250%\n" +
                        "\n" +
                        "Super Healing Potion\n" +
                        "Version: Resurrected\n" +
                        "Replenish Life +320\n" +
                        "\n" +
                        "Stamina Potion\n" +
                        "Version: Resurrected\n" +
                        "Heal Stamina Plus 5000%\n" +
                        "\n" +
                        "Thawing Potion\n" +
                        "Version: Resurrected\n" +
                        "+10% to Maximum Cold Resist\n" +
                        "Cold Resist +50%\n" +
                        "\n" +
                        "Antidote Potion\n" +
                        "Version: Resurrected\n" +
                        "+10% to Maximum Poison Resist\n" +
                        "Poison Resist +50%\n" +
                        "Finished: somethingSoftCore.d2i\n" +
                        "\n",
                out.toString().replaceAll("\r", ""));
    }

    @Test
    public void testWrongVersionStash() {
        String stashBytes = "55AA55AA0200000063000000F2A416004D00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D01001000A2000564D6900855AA55AA0200000069000000000000004E00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D01001000A2000564F647220055AA55AA0200000069000000000000004400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D0000";
        assertEquals("Please change the workspace version before loading this file.\n" +
                "Current Workspace: Resurrected: 3+\n" +
                "File Needs: Resurrected: 2.5+", assertThrows(VersionException.class, () -> new D2SharedStashReader().readStash(EXPANSION, "foo.d2x", new D2BitReader(decode(stashBytes)))).getMessage());
    }

    @Test
    public void testTooManyItemsStash() {
        String stashBytes = "55AA55AA0200000069000000F2A416004D00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D02001000A2000564D6900855AA55AA0200000069000000000000004E00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D01001000A2000564F647220055AA55AA0200000069000000000000004400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D0000";
        assertEquals("Incorrect shared stash length: 88 expected: 77", assertThrows(RuntimeException.class, () -> new D2SharedStashReader().readStash(EXPANSION, "foo.d2x", new D2BitReader(decode(stashBytes)))).getMessage());
    }

    @Test
    public void testTooFewItemsStash() {
        String stashBytes = "55AA55AA0200000069000000F2A416004D00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D00001000A2000564D6900855AA55AA0200000069000000000000004E00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D01001000A2000564F647220055AA55AA0200000069000000000000004400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D0000";
        assertEquals("Incorrect shared stash length: 68 expected: 77", assertThrows(RuntimeException.class, () -> new D2SharedStashReader().readStash(EXPANSION, "foo.d2x", new D2BitReader(decode(stashBytes)))).getMessage());
    }

    @Test
    public void testWrongVariantStash() {
        byte[] stashBytes = BaseEncoding.base16().decode(EMPTY_STASH + EMPTY_STASH + EMPTY_STASH + EMPTY_STASH);
        assertEquals("Please change the workspace variant before loading this file.\n" +
                "Current Workspace: Expansion\n" +
                "File Needs: Unknown", assertThrows(VersionException.class, () -> new D2SharedStashReader().readStash(EXPANSION, "foo.d2x", new D2BitReader(stashBytes))).getMessage());
    }

    private List<String> getItemDumps(D2SharedStash.D2SharedStashPane pane) {
        return pane.getItems().stream()
                .map(it -> D2ItemRenderer.itemDump(it, true).replace("\r", ""))
                .collect(Collectors.toList());
    }
}
