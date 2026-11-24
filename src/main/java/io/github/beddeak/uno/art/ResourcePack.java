package io.github.beddeak.uno.art;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ResourcePack {

    public static final String NAMESPACE = "uno";
    public static final int MIN_FORMAT = 97;
    public static final int MAX_FORMAT = 200;

    private static final LocalDateTime ENTRY_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);

    private final byte[] bytes;
    private final String sha1;

    private ResourcePack(byte[] bytes) {
        this.bytes = bytes;
        this.sha1 = sha1Hex(bytes);
    }

    public static ResourcePack build() {
        Map<String, Canvas> textures = CardArt.all();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "pack.mcmeta", """
                    {
                      "pack": {
                        "description": "UNO - 카드 텍스처",
                        "min_format": %d,
                        "max_format": %d
                      }
                    }
                    """.formatted(MIN_FORMAT, MAX_FORMAT).getBytes(StandardCharsets.UTF_8));
            put(zip, "pack.png", textures.get(CardArt.BACK).toPng());
            for (Map.Entry<String, Canvas> entry : textures.entrySet()) {
                String id = entry.getKey();
                put(zip, "assets/" + NAMESPACE + "/items/" + id + ".json", itemDefinition(id).getBytes(StandardCharsets.UTF_8));
                put(zip, "assets/" + NAMESPACE + "/models/item/" + id + ".json", """
                        {
                          "parent": "minecraft:item/generated",
                          "textures": {
                            "layer0": "%s:item/%s"
                          }
                        }
                        """.formatted(NAMESPACE, id).getBytes(StandardCharsets.UTF_8));
                put(zip, "assets/" + NAMESPACE + "/textures/item/" + id + ".png", entry.getValue().toPng());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ResourcePack(out.toByteArray());
    }

    private static String itemDefinition(String id) {
        if (id.equals(CardArt.BACK) || id.startsWith("button_")) {
            return """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "%s:item/%s"
                      }
                    }
                    """.formatted(NAMESPACE, id);
        }
        return """
                {
                  "model": {
                    "type": "minecraft:select",
                    "property": "minecraft:display_context",
                    "cases": [
                      {
                        "when": ["thirdperson_righthand", "thirdperson_lefthand", "head", "ground"],
                        "model": {
                          "type": "minecraft:model",
                          "model": "%1$s:item/%3$s"
                        }
                      }
                    ],
                    "fallback": {
                      "type": "minecraft:model",
                      "model": "%1$s:item/%2$s"
                    }
                  }
                }
                """.formatted(NAMESPACE, id, CardArt.BACK);
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTimeLocal(ENTRY_TIME);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    public static String sha1Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public byte[] bytes() {
        return bytes;
    }

    public String sha1() {
        return sha1;
    }
}
