package io.github.beddeak.uno;

import io.github.beddeak.uno.card.DeckMix;
import io.github.beddeak.uno.game.Rules;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record Settings(Rules rules, int turnSeconds, Clap clap, Table table, Pack pack, Play play) {

    public record Clap(boolean enabled, int seconds) {}

    public record Table(double feltSize, float cardScale, double seatRadius, boolean teleportPlayers) {}

    public record Pack(boolean enabled, boolean sendOnJoin, boolean required, int port, String publicHost, String externalUrl) {}

    public record Play(boolean protectPlayers, boolean adventureMode, int dealIntervalTicks, int drawIntervalTicks,
                       int botDelayTicks, int finishSeconds) {}

    public static Settings load(FileConfiguration config) {
        ConfigurationSection rules = section(config, "rules");
        ConfigurationSection table = section(config, "table");
        ConfigurationSection pack = section(config, "resource-pack");
        ConfigurationSection play = section(config, "game");
        return new Settings(
                new Rules(
                        clamp(rules.getInt("hand-size", 7), 1, 20),
                        rules.getBoolean("stacking", true),
                        rules.getBoolean("draw-until-playable", true),
                        clamp(rules.getInt("call-penalty", 2), 0, 10),
                        new DeckMix(
                                clamp(rules.getInt("six-cards", 16), 0, DeckMix.MAX),
                                clamp(rules.getInt("plus2-cards", 16), 0, DeckMix.MAX),
                                clamp(rules.getInt("plus4-cards", 8), 0, DeckMix.MAX),
                                clamp(rules.getInt("plus10-cards", 4), 0, DeckMix.MAX),
                                clamp(rules.getInt("plus99-cards", 1), 0, DeckMix.MAX))),
                clamp(rules.getInt("turn-seconds", 30), 0, 600),
                new Clap(
                        rules.getBoolean("six-clap", true),
                        clamp(rules.getInt("clap-seconds", 5), 1, 30)),
                new Table(
                        clamp(table.getDouble("felt-size", 3.0), 0, 12),
                        (float) clamp(table.getDouble("card-scale", 1.3), 0.3, 4),
                        clamp(table.getDouble("seat-radius", 3.0), 1, 16),
                        table.getBoolean("teleport-players", true)),
                new Pack(
                        pack.getBoolean("enabled", true),
                        pack.getBoolean("send-on-join", true),
                        pack.getBoolean("required", false),
                        clamp(pack.getInt("port", 8163), 1, 65535),
                        pack.getString("public-host", "").trim(),
                        pack.getString("external-url", "").trim()),
                new Play(
                        play.getBoolean("protect-players", true),
                        play.getBoolean("adventure-mode", true),
                        clamp(play.getInt("deal-interval-ticks", 2), 1, 20),
                        clamp(play.getInt("draw-interval-ticks", 16), 1, 60),
                        clamp(play.getInt("bot-delay-ticks", 30), 5, 200),
                        clamp(play.getInt("finish-seconds", 8), 1, 60)));
    }

    private static ConfigurationSection section(FileConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section != null ? section : config.createSection(path);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
