package io.github.beddeak.uno;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class Scores {

    record Entry(UUID player, String name, int wins, int points) {}

    private final File file;
    private final Logger logger;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    Scores(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "scores.yml");
        this.logger = logger;
        load();
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                entries.put(id, new Entry(id, players.getString(key + ".name", "?"),
                        players.getInt(key + ".wins"), players.getInt(key + ".points")));
            } catch (IllegalArgumentException e) {
                logger.warning("scores.yml의 잘못된 항목을 건너뜁니다: " + key);
            }
        }
    }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Entry entry : entries.values()) {
            String path = "players." + entry.player();
            yaml.set(path + ".name", entry.name());
            yaml.set(path + ".wins", entry.wins());
            yaml.set(path + ".points", entry.points());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "점수를 저장하지 못했습니다", e);
        }
    }

    void recordWin(UUID player, String name, int points) {
        Entry old = entries.getOrDefault(player, new Entry(player, name, 0, 0));
        entries.put(player, new Entry(player, name, old.wins() + 1, old.points() + points));
        save();
    }

    void reset() {
        entries.clear();
        save();
    }

    List<Entry> ranking() {
        List<Entry> list = new ArrayList<>(entries.values());
        list.sort(Comparator.comparingInt(Entry::points).reversed().thenComparing(Comparator.comparingInt(Entry::wins).reversed()));
        return list;
    }
}
