package net.aero.visuals.cosmetic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CosmeticSelectionTest {
    @TempDir Path directory;

    @Test
    void ignoresNullSelectionsInSavedFile() throws Exception {
        Path file = directory.resolve("cosmetics.json");
        Files.writeString(file, "{\"cape\":null,\"hat\":12}");
        CosmeticSelection selection = new CosmeticSelection(file);
        selection.load();
        assertEquals(-1, selection.get("cape"));
        assertEquals(Map.of("hat", 12), selection.all());
    }

    @Test
    void remoteTypesAreCanonicalAndLocalTypesArePreserved() {
        CosmeticSelection selection = new CosmeticSelection(directory.resolve("cosmetics.json"));
        selection.set("custom", 77);
        selection.set("cape", 1);
        selection.replaceRemote(Map.of("CAPE", 5, "custom", 9));
        assertEquals(5, selection.get("cape"));
        assertEquals(77, selection.get("custom"));
        CosmeticSelection reloaded = new CosmeticSelection(directory.resolve("cosmetics.json"));
        reloaded.load();
        assertEquals(selection.all(), reloaded.all());
    }
}
