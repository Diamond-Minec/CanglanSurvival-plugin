package com.canglan.survival.core;

import com.canglan.survival.CanglanSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一 GUI 基类：带中文标题的箱子界面，点击事件回调到子类。
 */
public abstract class Gui implements InventoryHolder {

    protected final CanglanSurvival plugin;
    protected final Inventory inventory;

    protected Gui(CanglanSurvival plugin, int size, String title) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, size, Msg.color(title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public abstract void onClick(Player player, InventoryClickEvent event);

    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Msg.color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) {
                    colored.add(Msg.color(line));
                }
                meta.setLore(colored);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack item(Material material, String name) {
        return item(material, name, null);
    }

    public static ItemStack amount(Material material, int amount, String name, List<String> lore) {
        ItemStack stack = item(material, name, lore);
        stack.setAmount(Math.max(1, Math.min(64, amount)));
        return stack;
    }

    public void border(Material material) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, item(material, " "));
        }
    }
}
