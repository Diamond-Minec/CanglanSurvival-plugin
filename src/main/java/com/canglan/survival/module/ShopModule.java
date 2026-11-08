package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Gui;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import com.canglan.survival.core.YamlStore;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 模块 06 · 系统商店
 * 图形化商店：左键买 1 个、Shift+左键买 16 个、右键卖 1 个、Shift+右键卖 16 个。
 */
public class ShopModule extends Module {

    private final YamlStore store;

    public ShopModule(CanglanSurvival plugin) {
        super(plugin, "shop", "系统商店",
                "图形化系统商店，用金币购买或出售物品；价格与商品都写在 shop.yml 里，可自行增删。",
                true, "shop");
        this.store = new YamlStore(plugin, "shop.yml");
    }

    @Override
    protected void onEnable() {
        ensureDefaults();
    }

    @Override
    protected void onDisable() {
    }

    @Override
    public void reload() {
        store.load();
        ensureDefaults();
    }

    private void ensureDefaults() {
        if (store.yaml().getConfigurationSection("items") != null) {
            return;
        }
        String[][] defaults = {
                {"DIAMOND", "1", "120", "60", "&b钻石"},
                {"IRON_INGOT", "1", "20", "8", "&f铁锭"},
                {"GOLDEN_APPLE", "1", "150", "60", "&6金苹果"},
                {"BREAD", "8", "12", "4", "&e面包"},
                {"COOKED_BEEF", "8", "24", "8", "&c牛排"},
                {"TORCH", "16", "10", "3", "&e火把"},
                {"OAK_LOG", "16", "16", "6", "&6橡木原木"},
                {"ENDER_PEARL", "1", "40", "15", "&5末影珍珠"},
                {"ARROW", "16", "12", "4", "&7箭"},
                {"EXPERIENCE_BOTTLE", "8", "60", "20", "&a附魔之瓶"}
        };
        int index = 0;
        for (String[] row : defaults) {
            String id = row[0].toLowerCase(Locale.ROOT) + "_" + index++;
            store.yaml().set("items." + id + ".material", row[0]);
            store.yaml().set("items." + id + ".amount", Integer.parseInt(row[1]));
            store.yaml().set("items." + id + ".buy", Double.parseDouble(row[2]));
            store.yaml().set("items." + id + ".sell", Double.parseDouble(row[3]));
            store.yaml().set("items." + id + ".name", row[4]);
        }
        store.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("open") || args[0].equals("打开")) {
            new ShopGui(player, 1).open(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sell", "出售", "卖" -> sellHand(player);
            case "reload", "重载" -> {
                if (!checkAdmin(sender)) {
                    return true;
                }
                reload();
                Msg.send(sender, "&a商店配置已重载，共 " + products().size() + " 件商品。");
            }
            case "list", "列表" -> {
                Msg.header(sender, "&b&l商店商品列表");
                for (Product product : products()) {
                    Msg.raw(sender, "&f" + product.name + " &8| &7买 &6" + Util.money(product.buy)
                            + " &8| &7卖 &6" + Util.money(product.sell));
                }
            }
            default -> Msg.send(sender, "&c用法：/shop 打开商店，/shop 出售 卖掉手上物品，/shop 列表 查看商品");
        }
        return true;
    }

    private void sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            Msg.send(player, "&c你手上没有物品。");
            return;
        }
        Product product = find(hand.getType());
        if (product == null || product.sell <= 0) {
            Msg.send(player, "&c这个物品商店不收购。");
            return;
        }
        int count = hand.getAmount();
        double total = product.sell * count;
        player.getInventory().setItemInMainHand(null);
        plugin.economy().add(player.getUniqueId(), total);
        Msg.send(player, "&a已出售 &f" + count + " 个 " + product.name + "&a，获得 &6" + Util.money(total) + " 金币。");
    }

    private List<Product> products() {
        List<Product> list = new ArrayList<>();
        ConfigurationSection section = store.yaml().getConfigurationSection("items");
        if (section == null) {
            return list;
        }
        for (String id : section.getKeys(false)) {
            String materialName = section.getString(id + ".material", "STONE");
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                continue;
            }
            int amount = Math.max(1, section.getInt(id + ".amount", 1));
            double buy = section.getDouble(id + ".buy", 0);
            double sell = section.getDouble(id + ".sell", 0);
            String name = section.getString(id + ".name", material.name());
            list.add(new Product(id, material, amount, buy, sell, name));
        }
        return list;
    }

    private Product find(Material material) {
        for (Product product : products()) {
            if (product.material == material) {
                return product;
            }
        }
        return null;
    }

    private static final class Product {
        private final String id;
        private final Material material;
        private final int amount;
        private final double buy;
        private final double sell;
        private final String name;

        private Product(String id, Material material, int amount, double buy, double sell, String name) {
            this.id = id;
            this.material = material;
            this.amount = amount;
            this.buy = buy;
            this.sell = sell;
            this.name = name;
        }
    }

    private final class ShopGui extends Gui {
        private final int page;

        private ShopGui(Player player, int page) {
            super(ShopModule.this.plugin, 54, "&b&l系统商店 &7第 " + page + " 页");
            this.page = page;
            List<Product> all = products();
            int perPage = 45;
            int from = (page - 1) * perPage;
            for (int i = 0; i < perPage && from + i < all.size(); i++) {
                Product product = all.get(from + i);
                List<String> lore = new ArrayList<>();
                lore.add("&7购买价：&6" + Util.money(product.buy) + " &7/ 个");
                lore.add("&7出售价：&6" + Util.money(product.sell) + " &7/ 个");
                lore.add("&7每次交易数量：&f" + product.amount);
                lore.add("");
                lore.add("&e左键 &7购买 &f" + product.amount + " &7个");
                lore.add("&eShift+左键 &7购买 &f" + (product.amount * 16) + " &7个");
                lore.add("&e右键 &7出售手上的同款物品");
                inventory.setItem(i, Gui.amount(product.material, product.amount, product.name, lore));
            }
            inventory.setItem(45, Gui.item(Material.ARROW, "&e上一页"));
            inventory.setItem(49, Gui.item(Material.BARRIER, "&c关闭商店"));
            inventory.setItem(53, Gui.item(Material.ARROW, "&e下一页"));
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= inventory.getSize()) {
                return;
            }
            if (slot == 45) {
                if (page > 1) {
                    new ShopGui(player, page - 1).open(player);
                } else {
                    Msg.send(player, "&c已经是第一页了。");
                }
                return;
            }
            if (slot == 49) {
                player.closeInventory();
                return;
            }
            if (slot == 53) {
                List<Product> all = products();
                if (page * 45 < all.size()) {
                    new ShopGui(player, page + 1).open(player);
                } else {
                    Msg.send(player, "&c已经是最后一页了。");
                }
                return;
            }
            ItemStack clicked = inventory.getItem(slot);
            if (clicked == null || clicked.getType() == Material.AIR) {
                return;
            }
            Product product = find(clicked.getType());
            if (product == null) {
                return;
            }
            if (event.isRightClick()) {
                sellFromInventory(player, product);
                return;
            }
            int buyAmount = product.amount * (event.isShiftClick() ? 16 : 1);
            double cost = product.buy * (event.isShiftClick() ? 16 : 1);
            if (!plugin.economy().take(player.getUniqueId(), cost)) {
                Msg.send(player, "&c金币不足，购买 &f" + buyAmount + " &c个需要 &6" + Util.money(cost) + " 金币。");
                return;
            }
            ItemStack stack = new ItemStack(product.material, buyAmount);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
            Msg.send(player, "&a购买成功：&f" + buyAmount + " 个 " + product.name
                    + " &7花费 &6" + Util.money(cost) + " &7金币。");
        }

        private void sellFromInventory(Player player, Product product) {
            if (product.sell <= 0) {
                Msg.send(player, "&c这个物品商店不收购。");
                return;
            }
            int count = 0;
            ItemStack[] contents = player.getInventory().getStorageContents();
            for (ItemStack item : contents) {
                if (item != null && item.getType() == product.material) {
                    count += item.getAmount();
                }
            }
            if (count <= 0) {
                Msg.send(player, "&c你背包里没有 " + product.name + "。");
                return;
            }
            player.getInventory().remove(product.material);
            double total = product.sell * count;
            plugin.economy().add(player.getUniqueId(), total);
            Msg.send(player, "&a已出售 &f" + count + " 个 " + product.name
                    + "&a，获得 &6" + Util.money(total) + " 金币。");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String option : List.of("打开", "出售", "列表", "重载")) {
                if (option.startsWith(args[0])) {
                    out.add(option);
                }
            }
            return out;
        }
        return List.of();
    }
}
