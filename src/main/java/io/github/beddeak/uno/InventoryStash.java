package io.github.beddeak.uno;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class InventoryStash {

    private final File directory;
    private final Logger logger;

    InventoryStash(File dataFolder, Logger logger) {
        this.directory = new File(dataFolder, "stash");
        this.logger = logger;
    }

    void save(Player player) throws IOException {
        File file = file(player.getUniqueId());
        if (file.exists()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", player.getName());
        yaml.set("game-mode", player.getGameMode().name());
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && !item.isEmpty()) {
                yaml.set("items." + slot, Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            }
        }
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("cannot create " + directory);
        }
        yaml.save(file);
    }

    boolean has(UUID player) {
        return file(player).isFile();
    }

    boolean restore(Player player) {
        File file = file(player.getUniqueId());
        if (!file.isFile()) {
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            logger.log(Level.SEVERE, player.getName() + "의 인벤토리 백업을 읽지 못했습니다: " + file, e);
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    inventory.setItem(slot, ItemStack.deserializeBytes(Base64.getDecoder().decode(items.getString(key, ""))));
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, player.getName() + "의 백업 아이템(" + key + ")을 복구하지 못했습니다", e);
                }
            }
        }
        String gameMode = yaml.getString("game-mode");
        if (gameMode != null) {
            try {
                player.setGameMode(GameMode.valueOf(gameMode));
            } catch (IllegalArgumentException e) {
                logger.warning(player.getName() + "의 게임 모드 백업이 잘못됐습니다: " + gameMode);
            }
        }
        if (!file.delete()) {
            logger.warning("인벤토리 백업 파일을 지우지 못했습니다: " + file);
        }
        return true;
    }

    private File file(UUID player) {
        return new File(directory, player + ".yml");
    }
}
