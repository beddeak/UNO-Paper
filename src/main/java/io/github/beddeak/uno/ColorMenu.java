package io.github.beddeak.uno;

import io.github.beddeak.uno.card.CardColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class ColorMenu implements InventoryHolder {

    private final Inventory inventory;

    ColorMenu(CardItems items) {
        inventory = Bukkit.createInventory(this, InventoryType.HOPPER, Component.text("다음 색을 고르세요", NamedTextColor.DARK_PURPLE));
        CardColor[] colors = CardColor.values();
        for (int i = 0; i < colors.length; i++) {
            inventory.setItem(i, items.colorChoice(colors[i]));
        }
    }

    static CardColor colorAt(int slot) {
        CardColor[] colors = CardColor.values();
        return slot >= 0 && slot < colors.length ? colors[slot] : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
