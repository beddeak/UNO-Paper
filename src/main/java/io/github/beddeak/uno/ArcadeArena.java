package io.github.beddeak.uno;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Owns only a dedicated world; never places blocks in an existing player world. */
final class ArcadeArena {
    static final String WORLD = "lastcard_arcade";
    private static final String SIGN_TAG = "uno_arcade_sign";
    private final UnoPlugin plugin;
    private World world;

    ArcadeArena(UnoPlugin plugin) {
        this.plugin = plugin;
    }

    boolean ready() {
        return world != null && plugin.getConfig().getBoolean("arcade.built", false);
    }

    boolean contains(World candidate) {
        return world != null && world.equals(candidate);
    }

    boolean isHallTable(Location center) {
        return contains(center.getWorld()) && center.getX() == 0.5 && center.getY() == ArcadeLayout.FLOOR + 2
                && center.getZ() == 0.5;
    }

    void prepareLobby() {
        Location center = ensureBuilt();
        if (plugin.tableCenter() == null) plugin.saveTable(center);
    }

    Location ensureBuilt() {
        if (world == null) {
            boolean owned = plugin.getConfig().getBoolean("arcade.owned", false);
            // Paper 26.3 stores dimensions under the level directory, not server/<world>.
            File folder = Bukkit.getServer().getLevelDirectory()
                    .resolve("dimensions/minecraft").resolve(WORLD).toFile();
            if (!owned && (folder.exists() || Bukkit.getWorld(WORLD) != null)) {
                throw new IllegalStateException(WORLD + " 월드가 이미 있어서 덮어쓰지 않았습니다. 관리자에게 문의해 주세요.");
            }
            if (!folder.exists() && Bukkit.getWorld(WORLD) == null) {
                plugin.getConfig().set("arcade.built", false);
            }
            plugin.getConfig().set("arcade.owned", true);
            plugin.saveConfig();
            world = new WorldCreator(WORLD).generator(new VoidGenerator()).generateStructures(false).createWorld();
            if (world == null) throw new IllegalStateException("아케이드 월드를 만들지 못했습니다.");
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setGameRule(GameRules.ADVANCE_WEATHER, false);
            world.setGameRule(GameRules.SPAWN_MOBS, false);
            world.setGameRule(GameRules.SPAWN_MONSTERS, false);
            world.setGameRule(GameRules.MOB_GRIEFING, false);
            world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
            world.setGameRule(GameRules.TNT_EXPLODES, false);
            world.setTime(6000);
            world.setStorm(false);
            world.setThundering(false);
            world.getWorldBorder().setCenter(0.5, 0.5);
            world.getWorldBorder().setSize(41);
            world.getWorldBorder().setWarningDistance(0);
            // Load all arena chunks before removing old labels after a restart.
            for (int x = -2; x <= 1; x++) {
                for (int z = -2; z <= 1; z++) world.getChunkAt(x, z).getEntities();
            }
            int revision = plugin.getConfig().getBoolean("arcade.built", false)
                    ? plugin.getConfig().getInt("arcade.layout-revision", 1) : 0;
            if (revision < ArcadeLayout.REVISION) {
                if (revision == 2) {
                    // The hall already exists. Upgrade only the physical tabletop once.
                    ArcadeLayout.tabletop().forEach((cell, name) ->
                            world.getBlockAt(cell.x(), cell.y(), cell.z()).setType(Material.valueOf(name), false));
                } else {
                    buildRoom();
                    world.setSpawnLocation(arrival());
                }
                world.save();
                plugin.getConfig().set("arcade.built", true);
                plugin.getConfig().set("arcade.layout-revision", ArcadeLayout.REVISION);
                plugin.saveConfig();
                plugin.getLogger().info("UNO 5인 테이블 적용 (revision " + ArcadeLayout.REVISION + "): " + WORLD);
            }
            removeLabels();
        }
        return new Location(world, 0.5, ArcadeLayout.FLOOR + 2, 0.5);
    }

    private void buildRoom() {
        Map<ArcadeLayout.Cell, String> blueprint = new ArcadeLayout().blocks();
        Map<String, Material> palette = new HashMap<>();
        for (String name : blueprint.values()) {
            palette.computeIfAbsent(name, Material::valueOf);
        }
        // Replace only our room volume, including air where old neon props stood.
        for (int x = -ArcadeLayout.RADIUS; x <= ArcadeLayout.RADIUS; x++) {
            for (int z = -ArcadeLayout.RADIUS; z <= ArcadeLayout.RADIUS; z++) {
                for (int y = ArcadeLayout.MIN_Y; y <= ArcadeLayout.MAX_Y; y++) {
                    String name = blueprint.get(new ArcadeLayout.Cell(x, y, z));
                    Material material = name == null ? Material.AIR : palette.get(name);
                    var block = world.getBlockAt(x, y, z);
                    if (block.getType() != material) block.setType(material, false);
                    if (block.getBlockData() instanceof Leaves leaves) {
                        leaves.setPersistent(true);
                        block.setBlockData(leaves, false);
                    }
                }
            }
        }
    }

    String enter(Player player) {
        if (!ready()) return "아직 경기장이 없습니다. OP가 /uno arcade 로 먼저 만들어 주세요.";
        if (participating(player)) return "게임 참가 중에는 이동할 수 없습니다. 게임이 끝난 뒤 이용해 주세요.";
        if (!contains(player.getWorld())) {
            plugin.getConfig().set(returnKey(player), player.getLocation());
            plugin.saveConfig();
        }
        if (!player.teleport(arrival())) return "경기장 이동이 취소되었습니다.";
        welcome(player);
        return null;
    }

    // Called on the server thread while the connection is still selecting its first location.
    Location loginSpawn(UUID player, Location previous) {
        if (!ready() || plugin.session() != null && plugin.session().isParticipant(player)) return null;
        String key = returnKey(player);
        if (!contains(previous.getWorld()) && !plugin.getConfig().contains(key)) {
            plugin.getConfig().set(key, previous.clone());
            plugin.saveConfig();
        }
        return arrival();
    }

    void welcome(Player player) {
        player.sendMessage(Component.text("UNO / MINIGAME HALL", NamedTextColor.DARK_AQUA));
        player.sendMessage(Component.text("/uno join 참가 · /uno test 혼자+봇(OP) · /uno arcade exit 원래 위치로", NamedTextColor.GRAY));
    }

    String exit(Player player) {
        if (!contains(player.getWorld())) return "아케이드 경기장 안에서 사용해 주세요.";
        if (participating(player)) return "게임 참가 중에는 나갈 수 없습니다. 게임 종료 후 이용해 주세요.";
        Location back = plugin.getConfig().getLocation(returnKey(player));
        if (back == null || back.getWorld() == null || contains(back.getWorld())) {
            World fallback = Bukkit.getWorlds().stream().filter(w -> !contains(w)).findFirst().orElse(null);
            if (fallback == null) return "돌아갈 월드를 찾지 못했습니다.";
            back = fallback.getSpawnLocation();
        }
        if (!player.teleport(back)) return "복귀 이동이 취소되었습니다.";
        plugin.getConfig().set(returnKey(player), null);
        plugin.saveConfig();
        return null;
    }

    Location arrival() {
        return new Location(world, 0.5, 65, 10.5, 180, 0);
    }

    private boolean participating(Player player) {
        return plugin.session() != null && plugin.session().isParticipant(player.getUniqueId());
    }

    private static String returnKey(Player player) {
        return returnKey(player.getUniqueId());
    }

    private static String returnKey(UUID player) {
        return "arcade.return." + player;
    }

    private void removeLabels() {
        // Clear both old sign tags on startup, without recreating any floating venue text.
        world.getEntities().stream().filter(e -> e.getScoreboardTags().contains(SIGN_TAG)
                || e.getScoreboardTags().contains("lastcard_arcade_sign")).forEach(e -> e.remove());
    }

    private static final class VoidGenerator extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
        @Override public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, 65, 10.5, 180, 0);
        }
    }
}
