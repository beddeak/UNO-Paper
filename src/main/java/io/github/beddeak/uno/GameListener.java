package io.github.beddeak.uno;

import io.github.beddeak.uno.CardItems.Button;
import io.github.beddeak.uno.card.Card;
import io.github.beddeak.uno.card.CardColor;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

final class GameListener implements Listener {

    private final UnoPlugin plugin;

    GameListener(UnoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!plugin.items().isGameItem(item)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        GameSession session = plugin.session();
        if (session == null) {
            player.sendActionBar(Component.text("진행 중인 게임이 없습니다.", NamedTextColor.GRAY));
            return;
        }
        Card card = plugin.items().cardOf(item);
        if (card != null) {
            session.playCard(player, card);
            return;
        }
        Button button = plugin.items().buttonOf(item);
        if (button != null) {
            session.pressButton(player, button);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        GameSession session = plugin.session();
        if (session != null && session.isDeck(event.getRightClicked())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                session.pressButton(event.getPlayer(), Button.DRAW);
            }
            return;
        }
        if (plugin.items().isGameItem(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        GameSession session = plugin.session();
        if (session != null && session.isDeck(event.getAttacked())) {
            event.setCancelled(true);
            session.pressButton(event.getPlayer(), Button.DRAW);
        }
    }

    @EventHandler
    public void onSwing(PlayerArmSwingEvent event) {
        GameSession session = plugin.session();
        if (session != null && event.getHand() == EquipmentSlot.HAND) {
            session.swing(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        GameSession session = plugin.session();
        if (session != null && session.isParticipant(player.getUniqueId())) {
            event.setCancelled(true);
            session.call(player);
            return;
        }
        if (plugin.items().isGameItem(event.getMainHandItem()) || plugin.items().isGameItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.items().isGameItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof ColorMenu) {
            event.setCancelled(true);
            if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                CardColor color = ColorMenu.colorAt(event.getRawSlot());
                GameSession session = plugin.session();
                if (color != null && session != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> session.chooseColor(player, color));
                }
            }
            return;
        }
        CardItems items = plugin.items();
        ItemStack current = event.getCurrentItem();
        if (event.getView().getTopInventory().getHolder(false) instanceof HandMenu
                && event.getRawSlot() >= 0 && event.getRawSlot() < HandLayout.MENU_SIZE) {
            event.setCancelled(true);
            Card picked = items.cardOf(current);
            GameSession session = plugin.session();
            if (picked != null && session != null && (event.getClick().isLeftClick() || event.getClick().isRightClick())) {
                Bukkit.getScheduler().runTask(plugin, () -> session.playCard(player, picked));
            }
            return;
        }
        ItemStack hotbar = event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
                ? player.getInventory().getItem(event.getHotbarButton()) : null;
        ItemStack offhand = event.getClick() == ClickType.SWAP_OFFHAND ? player.getInventory().getItemInOffHand() : null;
        if (!items.isGameItem(current) && !items.isGameItem(event.getCursor())
                && !items.isGameItem(hotbar) && !items.isGameItem(offhand)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getClickedInventory() instanceof PlayerInventory)
                || !(event.getClick().isLeftClick() || event.getClick().isRightClick())) {
            return;
        }
        GameSession session = plugin.session();
        Card card = items.cardOf(current);
        Button button = items.buttonOf(current);
        if (session == null || card == null && button == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (card != null) {
                session.playCard(player, card);
            } else {
                session.pressButton(player, button);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ColorMenu
                || event.getView().getTopInventory().getHolder(false) instanceof HandMenu
                || plugin.items().isGameItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof ColorMenu) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        GameSession session = plugin.session();
        if (session != null && session.isChoosing(player)) {
            player.sendActionBar(Component.text("색을 골라야 창이 닫힙니다.", NamedTextColor.LIGHT_PURPLE));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(plugin.items()::isGameItem);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        GameSession session = plugin.session();
        if (session != null) {
            session.onRespawn(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && protects(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && protects(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        GameSession session = plugin.session();
        if (event.getEntity() instanceof Player player && session != null && session.isParticipant(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLoginSpawn(AsyncPlayerSpawnLocationEvent event) {
        UUID player = event.getConnection().getProfile().getId();
        Location previous = event.getSpawnLocation();
        try {
            // World/config/session access stays on the server thread. Returning the location
            // here avoids spawning in the overworld first and teleporting after login.
            Location spawn = Bukkit.getScheduler().callSyncMethod(plugin,
                    () -> plugin.arcade().loginSpawn(player, previous)).get();
            if (spawn != null) event.setSpawnLocation(spawn);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.WARNING, "UNO 경기장 접속 위치 설정이 중단됐습니다", e);
        } catch (ExecutionException e) {
            plugin.getLogger().log(Level.SEVERE, "UNO 경기장 접속 위치를 정하지 못했습니다", e.getCause());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        GameSession session = plugin.session();
        if (session == null || !session.isParticipant(player.getUniqueId())) {
            if (plugin.stash().restore(player)) {
                player.setGlowing(false);
                player.sendMessage(Component.text("[UNO] 게임 전 인벤토리를 돌려드렸습니다.", NamedTextColor.GOLD));
            }
        }
        if (session != null) {
            session.onJoin(player);
        }
        if (session == null || !session.isParticipant(player.getUniqueId())) {
            plugin.arcade().welcome(player);
        }
        if (plugin.settings().pack().sendOnJoin()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.packs().send(player);
                }
            }, 20L);
        }
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        switch (event.getStatus()) {
            case DECLINED -> event.getPlayer().sendMessage(Component.text(
                    "[UNO] 리소스팩을 거절해서 카드가 종이로 보입니다. /uno pack 으로 다시 받을 수 있습니다.", NamedTextColor.YELLOW));
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                event.getPlayer().sendMessage(Component.text(
                        "[UNO] 리소스팩을 받지 못했습니다(" + event.getStatus() + "). 관리자에게 포트 " + plugin.settings().pack().port()
                                + " 열림 여부를 확인해 달라고 하세요.", NamedTextColor.RED));
                plugin.getLogger().warning(event.getPlayer().getName() + " 리소스팩 실패: " + event.getStatus()
                        + " (" + plugin.packs().urlFor(event.getPlayer()) + ")");
            }
            default -> {
            }
        }
    }

    private boolean protects(Player player) {
        GameSession session = plugin.session();
        return plugin.settings().play().protectPlayers() && session != null && session.isParticipant(player.getUniqueId());
    }
}
