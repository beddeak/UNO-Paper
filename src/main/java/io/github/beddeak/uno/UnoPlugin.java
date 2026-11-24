package io.github.beddeak.uno;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class UnoPlugin extends JavaPlugin {

    private final Set<UUID> lobby = new LinkedHashSet<>();
    private Settings settings;
    private CardItems items;
    private PackService packs;
    private InventoryStash stash;
    private Scores scores;
    private GameSession session;
    private ArcadeArena arcade;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("LastCard") != null) {
            getLogger().severe("이전 플러그인 jar를 제거하고 UNO jar만 남긴 뒤 서버를 다시 시작해 주세요.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            LegacyData.migrate(getDataFolder().toPath());
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "기존 설정·점수·인벤토리 백업을 옮기지 못했습니다", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        settings = Settings.load(getConfig());
        items = new CardItems(this);
        stash = new InventoryStash(getDataFolder(), getLogger());
        scores = new Scores(getDataFolder(), getLogger());
        packs = new PackService(this);
        packs.start(settings.pack());
        arcade = new ArcadeArena(this);
        arcade.prepareLobby();
        getServer().getPluginManager().registerEvents(new ArcadeListener(arcade), this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        UnoCommand command = new UnoCommand(this);
        PluginCommand pluginCommand = getCommand("uno");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRules.PVP, false);
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(TableView.TAG)
                        || entity.getScoreboardTags().contains("lastcard")) {
                    entity.remove();
                }
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (stash.restore(player)) {
                player.setGlowing(false);
            }
        }
    }

    @Override
    public void onDisable() {
        if (session != null) {
            session.close();
            session = null;
        }
        if (packs != null) {
            packs.stop();
        }
    }

    Settings settings() {
        return settings;
    }

    ArcadeArena arcade() {
        return arcade;
    }

    CardItems items() {
        return items;
    }

    PackService packs() {
        return packs;
    }

    InventoryStash stash() {
        return stash;
    }

    Scores scores() {
        return scores;
    }

    GameSession session() {
        return session;
    }

    Set<UUID> lobby() {
        return lobby;
    }

    String startGame(Player starter, List<Player> requested, int bots) {
        if (session != null) {
            return "이미 게임이 진행 중입니다. /uno stop 으로 끝낼 수 있습니다.";
        }
        List<Player> players = new ArrayList<>(requested);
        if (players.isEmpty()) {
            for (UUID id : lobby) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    players.add(player);
                }
            }
        }
        if (players.isEmpty() && bots == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() != GameMode.SPECTATOR) {
                    players.add(player);
                }
            }
        }
        Location center = tableCenter();
        if (center == null) {
            if (starter == null) {
                return "테이블 위치가 없습니다. 게임 안에서 /uno table 로 먼저 정해 주세요.";
            }
            center = pickTable(starter);
            saveTable(center);
        }
        try {
            session = GameSession.start(this, players, bots, center);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "인벤토리 백업 실패로 게임을 시작하지 않았습니다", e);
            return "인벤토리 백업에 실패해서 시작하지 않았습니다. 콘솔을 확인해 주세요.";
        }
        lobby.clear();
        return null;
    }

    boolean stopGame() {
        if (session == null) {
            return false;
        }
        GameSession current = session;
        session = null;
        current.close();
        return true;
    }

    void endSession(GameSession ended) {
        if (session == ended) {
            session = null;
        }
        ended.close();
    }

    Location tableCenter() {
        String worldName = getConfig().getString("table.world", "");
        World world = worldName.isEmpty() ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, getConfig().getDouble("table.x"), getConfig().getDouble("table.y"), getConfig().getDouble("table.z"));
    }

    Location pickTable(Player player) {
        Block target = player.getTargetBlockExact(8);
        if (target != null && !target.isPassable()) {
            BoundingBox box = target.getBoundingBox();
            double top = box.getVolume() > 0 ? box.getMaxY() : target.getY() + 1.0;
            return new Location(target.getWorld(), target.getX() + 0.5, top, target.getZ() + 0.5);
        }
        Location feet = player.getLocation();
        return new Location(feet.getWorld(), feet.getBlockX() + 0.5, feet.getY(), feet.getBlockZ() + 0.5);
    }

    void saveTable(Location center) {
        getConfig().set("table.world", center.getWorld().getName());
        getConfig().set("table.x", center.getX());
        getConfig().set("table.y", center.getY());
        getConfig().set("table.z", center.getZ());
        saveConfig();
    }

    void reloadSettings() {
        reloadConfig();
        settings = Settings.load(getConfig());
        packs.stop();
        packs.start(settings.pack());
    }

    void saveRule(String key, Object value) {
        getConfig().set("rules." + key, value);
        saveConfig();
        settings = Settings.load(getConfig());
    }
}
