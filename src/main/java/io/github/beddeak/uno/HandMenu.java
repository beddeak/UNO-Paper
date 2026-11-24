package io.github.beddeak.uno;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class HandMenu implements InventoryHolder {

    private final Inventory inventory;

    HandMenu() {
        inventory = Bukkit.createInventory(this, HandLayout.MENU_SIZE,
                Component.text("내 손패 · 클릭해서 내기", NamedTextColor.DARK_GRAY));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
