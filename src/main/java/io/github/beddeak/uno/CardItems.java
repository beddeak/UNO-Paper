package io.github.beddeak.uno;

import io.github.beddeak.uno.art.ResourcePack;
import io.github.beddeak.uno.card.Card;
import io.github.beddeak.uno.card.CardColor;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public final class CardItems {

    public enum Button { DRAW, TAKE, PASS, CALL, HAND, CLAP }

    private static final int MAX_STACK = 99;

    private final NamespacedKey cardKey;
    private final NamespacedKey buttonKey;
    private static final NamespacedKey LEGACY_CARD_KEY = new NamespacedKey("lastcard", "card");
    private static final NamespacedKey LEGACY_BUTTON_KEY = new NamespacedKey("lastcard", "button");

    public CardItems(Plugin plugin) {
        this.cardKey = new NamespacedKey(plugin, "card");
        this.buttonKey = new NamespacedKey(plugin, "button");
    }

    public static TextColor color(Card card) {
        return card.isWild() ? NamedTextColor.LIGHT_PURPLE : color(card.color());
    }

    public static TextColor color(CardColor color) {
        return TextColor.color(color.rgb());
    }

    public static Component name(Card card) {
        return Component.text(card.koreanName(), color(card));
    }

    public ItemStack card(Card card, int amount, boolean playable) {
        ItemStack item = base(card.id());
        item.setData(DataComponentTypes.MAX_STACK_SIZE, MAX_STACK);
        item.setAmount(Math.max(1, Math.min(MAX_STACK, amount)));
        item.setData(DataComponentTypes.ITEM_NAME, name(card).decoration(TextDecoration.BOLD, playable));
        List<Component> lore = new ArrayList<>();
        if (amount > 1) {
            lore.add(plain(amount + "장 가지고 있습니다", NamedTextColor.WHITE));
        }
        lore.add(plain(playable ? "지금 낼 수 있습니다" : "지금은 낼 수 없습니다",
                playable ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        lore.add(plain("손에 들고 우클릭하거나 인벤토리에서 클릭하면 냅니다", NamedTextColor.GRAY));
        if (card.isWild()) {
            lore.add(plain("낸 뒤 다음 색을 고릅니다", NamedTextColor.GRAY));
        }
        item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        if (playable) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        item.editPersistentDataContainer(pdc -> pdc.set(cardKey, PersistentDataType.STRING, card.id()));
        return item;
    }

    public ItemStack button(Button button, int pending, boolean highlight) {
        ItemStack item = base(switch (button) {
            case DRAW -> "button_draw";
            case TAKE -> "button_take";
            case PASS -> "button_pass";
            case CALL -> "button_call";
            case HAND -> "button_hand";
            case CLAP -> "button_clap";
        });
        Component name = switch (button) {
            case DRAW -> Component.text("카드 뽑기", NamedTextColor.GREEN);
            case TAKE -> Component.text("카드 " + pending + "장 받기", NamedTextColor.RED);
            case PASS -> Component.text("턴 넘기기", NamedTextColor.GRAY);
            case CALL -> Component.text("UNO! [F]", NamedTextColor.GOLD);
            case HAND -> Component.text("손패 전체 보기", NamedTextColor.AQUA);
            case CLAP -> Component.text("박수! (아무 데나 클릭)", NamedTextColor.GOLD);
        };
        List<Component> lore = switch (button) {
            case DRAW -> List.of(plain("우클릭: 덱에서 뽑기", NamedTextColor.GRAY),
                    plain("낼 수 있는 카드가 나올 때까지 뽑을 수 있습니다", NamedTextColor.GRAY),
                    plain("테이블의 덱을 클릭해도 뽑습니다", NamedTextColor.GRAY));
            case HAND -> List.of(plain("우클릭: 가진 카드를 색깔별로 한 화면에 봅니다", NamedTextColor.GRAY),
                    plain("거기서 카드를 클릭하면 냅니다", NamedTextColor.GRAY));
            case CLAP -> List.of(plain("6이 나왔습니다! 좌클릭이든 우클릭이든 빨리 누르세요", NamedTextColor.YELLOW),
                    plain("제일 늦은 사람이 1장 받습니다", NamedTextColor.GRAY));
            case TAKE -> List.of(plain("+2/+4로 받아치지 않고 누적된 카드를 받습니다", NamedTextColor.GRAY));
            case PASS -> List.of(plain("뽑은 카드를 내지 않고 차례를 넘깁니다", NamedTextColor.GRAY));
            case CALL -> List.of(plain("카드가 2장 이하일 때 외치세요 (F키도 됩니다)", NamedTextColor.GRAY),
                    plain("1장 남았는데 안 외친 사람이 있으면 잡습니다", NamedTextColor.GRAY));
        };
        item.setData(DataComponentTypes.ITEM_NAME, name.decoration(TextDecoration.BOLD, highlight));
        item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        if (highlight) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        item.editPersistentDataContainer(pdc -> pdc.set(buttonKey, PersistentDataType.STRING, button.name()));
        return item;
    }

    public ItemStack display(String textureId) {
        return base(textureId);
    }

    public ItemStack colorChoice(CardColor color) {
        ItemStack item = ItemStack.of(switch (color) {
            case RED -> Material.RED_CONCRETE;
            case YELLOW -> Material.YELLOW_CONCRETE;
            case GREEN -> Material.LIME_CONCRETE;
            case BLUE -> Material.BLUE_CONCRETE;
        });
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key(ResourcePack.NAMESPACE, "wild_" + color.id()));
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(color.koreanName(), color(color), TextDecoration.BOLD));
        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(plain("클릭해서 이 색으로 정하기", NamedTextColor.GRAY))));
        return item;
    }

    public Card cardOf(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        String id = item.getPersistentDataContainer().get(cardKey, PersistentDataType.STRING);
        if (id == null) id = item.getPersistentDataContainer().get(LEGACY_CARD_KEY, PersistentDataType.STRING);
        return id == null ? null : Card.fromId(id);
    }

    public Button buttonOf(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        String id = item.getPersistentDataContainer().get(buttonKey, PersistentDataType.STRING);
        if (id == null) id = item.getPersistentDataContainer().get(LEGACY_BUTTON_KEY, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return Button.valueOf(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isGameItem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        return item.getPersistentDataContainer().has(cardKey) || item.getPersistentDataContainer().has(buttonKey)
                || item.getPersistentDataContainer().has(LEGACY_CARD_KEY)
                || item.getPersistentDataContainer().has(LEGACY_BUTTON_KEY);
    }

    private static ItemStack base(String textureId) {
        ItemStack item = ItemStack.of(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key(ResourcePack.NAMESPACE, textureId));
        return item;
    }

    private static Component plain(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
