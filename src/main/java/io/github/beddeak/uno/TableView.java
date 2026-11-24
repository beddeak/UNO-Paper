package io.github.beddeak.uno;

import io.github.beddeak.uno.art.CardArt;
import io.github.beddeak.uno.card.CardColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class TableView {

    static final String TAG = "uno";

    private static final int PILE_MAX = 7;
    private static final float THIN = 0.35f;
    private static final Display.Brightness FULL_BRIGHT = new Display.Brightness(15, 15);

    private final Plugin plugin;
    private final CardItems items;
    private final Location center;
    private final Location pileSpot;
    private final Location deckSpot;
    private final float scale;
    private final Random random = new Random();
    private final List<Entity> owned = new ArrayList<>();
    private final List<ItemDisplay> pile = new ArrayList<>();
    private BlockDisplay colorMat;
    private TextDisplay info;
    private Interaction deckHitbox;
    private boolean removed;

    TableView(Plugin plugin, CardItems items, Location center, float scale) {
        this.plugin = plugin;
        this.items = items;
        this.center = center.clone();
        this.center.setYaw(0);
        this.center.setPitch(0);
        this.scale = scale;
        this.pileSpot = this.center.clone().add(0.45 * scale, 0, 0);
        this.deckSpot = this.center.clone().add(-0.75 * scale, 0, 0);
    }

    Location center() {
        return center.clone();
    }

    void spawn(double feltSize) {
        World world = center.getWorld();
        if (feltSize > 0) {
            float size = (float) feltSize;
            own(world.spawn(center, BlockDisplay.class, display -> {
                prepare(display);
                display.setBrightness(null);
                display.setBlock(Material.GREEN_WOOL.createBlockData());
                display.setTransformation(new Transformation(new Vector3f(-size / 2, 0.001f, -size / 2),
                        new Quaternionf(), new Vector3f(size, 0.02f, size), new Quaternionf()));
            }));
        }
        float mat = 1.2f * scale;
        colorMat = own(world.spawn(pileSpot, BlockDisplay.class, display -> {
            prepare(display);
            display.setBlock(Material.WHITE_CONCRETE.createBlockData());
            display.setTransformation(new Transformation(new Vector3f(-mat / 2, 0.022f, -mat / 2),
                    new Quaternionf(), new Vector3f(mat, 0.02f, mat), new Quaternionf()));
        }));
        for (int i = 0; i < 3; i++) {
            float yaw = (float) Math.toRadians(i * 4 - 4);
            own(world.spawn(deckSpot.clone().add(0, cardY(i), 0), ItemDisplay.class, display -> {
                prepare(display);
                display.setItemStack(items.display(CardArt.BACK));
                display.setTransformation(flat(yaw, scale));
            }));
        }
        deckHitbox = own(world.spawn(deckSpot, Interaction.class, interaction -> {
            prepare(interaction);
            interaction.setInteractionWidth(0.9f * scale);
            interaction.setInteractionHeight(0.3f);
            interaction.setResponsive(true);
        }));
        info = own(world.spawn(center.clone().add(0, 1.35, 0), TextDisplay.class, text -> {
            prepare(text);
            text.setBillboard(Display.Billboard.CENTER);
            text.setBackgroundColor(Color.fromARGB(0x88, 0, 0, 0));
            text.setAlignment(TextDisplay.TextAlignment.CENTER);
            text.setLineWidth(240);
            text.setShadowed(true);
            text.text(Component.text("UNO", NamedTextColor.GOLD));
        }));
    }

    Mannequin spawnBot(Location seat, Component name) {
        return own(seat.getWorld().spawn(seat, Mannequin.class, mannequin -> {
            mannequin.setPersistent(false);
            mannequin.addScoreboardTag(TAG);
            mannequin.customName(name);
            mannequin.setCustomNameVisible(true);
            mannequin.setImmovable(true);
            mannequin.setInvulnerable(true);
            mannequin.setSilent(true);
            mannequin.getEquipment().setItemInMainHand(items.display(CardArt.BACK));
        }));
    }

    boolean isDeck(Entity entity) {
        return deckHitbox != null && deckHitbox.equals(entity);
    }

    void playCard(String textureId, Location from) {
        if (removed) {
            return;
        }
        if (pile.size() >= PILE_MAX) {
            ItemDisplay oldest = pile.removeFirst();
            oldest.remove();
            owned.remove(oldest);
            for (int i = 0; i < pile.size(); i++) {
                pile.get(i).teleport(pileSpot.clone().add(0, cardY(i), 0));
            }
        }
        Location at = pileSpot.clone().add(0, cardY(pile.size()), 0);
        float yaw = (float) Math.toRadians(random.nextInt(51) - 25);
        Vector3f start = offset(at, from != null ? from : deckSpot.clone().add(0, 0.3, 0));
        ItemDisplay card = own(at.getWorld().spawn(at, ItemDisplay.class, display -> {
            prepare(display);
            display.setItemStack(items.display(textureId));
            display.setTransformation(new Transformation(start,
                    new Quaternionf().rotateY(yaw + (float) Math.PI).rotateX(0.6f),
                    new Vector3f(scale * 0.6f, scale * 0.6f, scale * 0.6f * THIN), new Quaternionf()));
        }));
        pile.add(card);
        animate(card, flat(yaw, scale), 7);
    }

    void recolorTop(String textureId) {
        if (!pile.isEmpty() && pile.getLast().isValid()) {
            pile.getLast().setItemStack(items.display(textureId));
        }
    }

    void flyFromDeck(Location to, int count) {
        for (int i = 0; i < Math.min(count, 6); i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> flyOne(to), i * 2L);
        }
    }

    private void flyOne(Location to) {
        if (removed) {
            return;
        }
        Location at = deckSpot.clone().add(0, cardY(3), 0);
        ItemDisplay card = own(at.getWorld().spawn(at, ItemDisplay.class, display -> {
            prepare(display);
            display.setItemStack(items.display(CardArt.BACK));
            display.setTransformation(flat(0, scale));
        }));
        Vector3f target = offset(at, to);
        animate(card, new Transformation(target, new Quaternionf().rotateY((float) Math.PI).rotateX(0.5f),
                new Vector3f(scale * 0.45f, scale * 0.45f, scale * 0.45f * THIN), new Quaternionf()), 8);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            card.remove();
            owned.remove(card);
        }, 11L);
    }

    void color(CardColor color) {
        if (colorMat == null || !colorMat.isValid()) {
            return;
        }
        Material material = color == null ? Material.LIGHT_GRAY_CONCRETE : switch (color) {
            case RED -> Material.RED_CONCRETE;
            case YELLOW -> Material.YELLOW_CONCRETE;
            case GREEN -> Material.LIME_CONCRETE;
            case BLUE -> Material.BLUE_CONCRETE;
        };
        if (colorMat.getBlock().getMaterial() != material) {
            colorMat.setBlock(material.createBlockData());
        }
    }

    void info(Component text) {
        if (info != null && info.isValid()) {
            info.text(text);
        }
    }

    void remove() {
        removed = true;
        for (Entity entity : owned) {
            entity.remove();
        }
        owned.clear();
        pile.clear();
    }

    private void animate(Display display, Transformation target, int duration) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!display.isValid()) {
                return;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(duration);
            display.setTransformation(target);
        }, 2L);
    }

    private float cardY(int index) {
        return 0.045f + scale * THIN / 32f + index * 0.004f;
    }

    private static Transformation flat(float yaw, float scale) {
        return new Transformation(new Vector3f(), new Quaternionf().rotateY(yaw).rotateX((float) Math.PI / 2),
                new Vector3f(scale, scale, scale * THIN), new Quaternionf());
    }

    private static Vector3f offset(Location from, Location to) {
        return new Vector3f((float) (to.getX() - from.getX()), (float) (to.getY() - from.getY()), (float) (to.getZ() - from.getZ()));
    }

    private void prepare(Entity entity) {
        entity.setPersistent(false);
        entity.addScoreboardTag(TAG);
        if (entity instanceof Display display) {
            display.setBrightness(FULL_BRIGHT);
            display.setViewRange(2.0f);
        }
    }

    private <T extends Entity> T own(T entity) {
        owned.add(entity);
        return entity;
    }
}
