package io.github.beddeak.uno.art;

import io.github.beddeak.uno.card.Card;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackTest {

    @Test
    void everyCardHasModelDefinitionAndTexture() throws IOException {
        ResourcePack pack = ResourcePack.build();
        Map<String, byte[]> entries = unzip(pack.bytes());
        assertTrue(new String(entries.get("pack.mcmeta"), StandardCharsets.UTF_8).contains("\"min_format\": 97"));
        assertTrue(entries.containsKey("pack.png"));
        for (Card card : Card.distinct()) {
            String id = card.id();
            String definition = new String(entries.get("assets/uno/items/" + id + ".json"), StandardCharsets.UTF_8);
            assertTrue(definition.contains("\"property\": \"minecraft:display_context\""), id);
            assertTrue(definition.contains("\"model\": \"uno:item/back\""), id);
            assertTrue(definition.contains("\"model\": \"uno:item/" + id + "\""), id);
            assertTrue(new String(entries.get("assets/uno/models/item/" + id + ".json"), StandardCharsets.UTF_8)
                    .contains("\"layer0\": \"uno:item/" + id + "\""), id);
            byte[] png = entries.get("assets/uno/textures/item/" + id + ".png");
            assertEquals((byte) 0x89, png[0]);
            assertEquals('P', png[1]);
        }
        for (String id : new String[]{"wild_red", "wild_draw_four_blue", "wild_draw_ten", "wild_draw_ten_green", "wild_draw_ninety_nine", "wild_draw_ninety_nine_yellow", "back", "button_draw", "button_take",
                "button_pass", "button_call", "button_hand", "button_clap"}) {
            assertTrue(entries.containsKey("assets/uno/textures/item/" + id + ".png"), id);
        }
        assertFalse(new String(entries.get("assets/uno/items/button_draw.json"), StandardCharsets.UTF_8).contains("select"));
    }

    @Test
    void packIsDeterministicSoClientsCanCacheIt() {
        ResourcePack first = ResourcePack.build();
        ResourcePack second = ResourcePack.build();
        assertArrayEquals(first.bytes(), second.bytes());
        assertEquals(first.sha1(), second.sha1());
        assertEquals(40, first.sha1().length());
    }

    @Test
    void cardsAreDistinctAndHaveTransparentMargins() {
        Map<String, Canvas> textures = CardArt.all();
        Map<String, String> seen = new HashMap<>();
        for (Map.Entry<String, Canvas> entry : textures.entrySet()) {
            Canvas canvas = entry.getValue();
            assertEquals(CardArt.SIZE, canvas.width());
            String key = java.util.Arrays.toString(canvas.pixels());
            assertTrue(seen.putIfAbsent(key, entry.getKey()) == null, entry.getKey() + " duplicates " + seen.get(key));
            if (!entry.getKey().startsWith("button")) {
                assertEquals(0, canvas.get(0, 0) >>> 24);
                assertNotEquals(0, canvas.get(32, 32) >>> 24);
            }
        }
    }

    @Test
    void writesPreviewSheet() throws IOException {
        String dir = System.getProperty("uno.previewDir");
        if (dir == null) {
            return;
        }
        Map<String, Canvas> textures = CardArt.all();
        int columns = 13;
        int scale = 2;
        int cell = CardArt.SIZE * scale;
        int rows = (textures.size() + columns - 1) / columns;
        Canvas sheet = new Canvas(columns * cell, rows * cell);
        for (int i = 0; i < sheet.pixels().length; i++) {
            sheet.pixels()[i] = 0xFF3B4A3F;
        }
        int index = 0;
        for (Canvas texture : textures.values()) {
            sheet.blit(texture, (index % columns) * cell, (index / columns) * cell, scale);
            index++;
        }
        Path out = Path.of(dir, "cards.png");
        Files.createDirectories(out.getParent());
        Files.write(out, sheet.toPng());
        Files.write(Path.of(dir, "UNO-pack.zip"), ResourcePack.build().bytes());
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }
}
