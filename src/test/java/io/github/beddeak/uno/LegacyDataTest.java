package io.github.beddeak.uno;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDataTest {
    @TempDir Path plugins;

    @Test
    void migratesSettingsScoresAndOfflineInventoryBackupsTogether() throws IOException {
        Path legacy = Files.createDirectory(plugins.resolve("LastCard"));
        Files.writeString(legacy.resolve("config.yml"), "arcade:\n  owned: true\n  layout-revision: 2\n");
        Files.writeString(legacy.resolve("scores.yml"), "existing scores");
        Files.createDirectory(legacy.resolve("stash"));
        Files.writeString(legacy.resolve("stash/player.yml"), "inventory backup");
        Files.writeString(legacy.resolve("LastCard-pack.zip"), "old pack");
        Path target = plugins.resolve("UNO");

        LegacyData.migrate(target);
        LegacyData.migrate(target); // Restart must not reset migrated data.

        assertFalse(Files.exists(legacy));
        assertTrue(Files.readString(target.resolve("config.yml")).contains("owned: true"));
        assertEquals("existing scores", Files.readString(target.resolve("scores.yml")));
        assertEquals("inventory backup", Files.readString(target.resolve("stash/player.yml")));
        assertEquals("old pack", Files.readString(target.resolve("UNO-pack.zip")));
    }

    @Test
    void existingUnoConfigurationWinsWithoutOverwritingEitherFolder() throws IOException {
        Path target = Files.createDirectory(plugins.resolve("UNO"));
        Files.writeString(target.resolve("config.yml"), "current UNO settings");
        Path legacy = Files.createDirectory(plugins.resolve("LastCard"));
        Files.writeString(legacy.resolve("config.yml"), "older settings");

        LegacyData.migrate(target);

        assertEquals("current UNO settings", Files.readString(target.resolve("config.yml")));
        assertEquals("older settings", Files.readString(legacy.resolve("config.yml")));
    }

    @Test
    void refusesToMergeIntoNonemptyUnconfiguredFolder() throws IOException {
        Path target = Files.createDirectory(plugins.resolve("UNO"));
        Files.writeString(target.resolve("scores.yml"), "keep these scores");
        Files.createDirectory(plugins.resolve("LastCard"));

        assertThrows(IOException.class, () -> LegacyData.migrate(target));
        assertEquals("keep these scores", Files.readString(target.resolve("scores.yml")));
        assertTrue(Files.exists(plugins.resolve("LastCard")));
    }
}
