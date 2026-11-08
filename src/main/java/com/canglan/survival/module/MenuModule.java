package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Gui;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块 21 · 钟表菜单
 * 玩家进服自动获得一个「功能菜单」钟表，右键打开图形化菜单：
 * 商店、地标、随机传送、家园、领地、签到、排行榜、统计、公告、在线玩家传送等。
 * 也可以直接输入 /menu 打开。
 */
public class MenuModule extends Module {

    private final NamespacedKey key;
    private final Map<Integer, String> actions = new LinkedHashMap<>();

    public MenuModule(CanglanSurvival plugin) {
        super(plugin, "menu", "钟表菜单",
                "给玩家发一个「功能菜单」钟表，右键打开图形化菜单（商店、家园、领地、签到、排行榜等）；也可输入 /menu 打开。",
                true, "menu");
        this.key = new NamespacedKey(plugin, "menu_clock");
    }

    @Override
    protected void onEnable() {
        listen(new MenuListener());
        // 重载后给在线玩家补发钟表
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getConfig().getBoolean("menu.give-on-join", true)) {
                later(20L, () -> giveClock(player));
            }
        }
    }

    @Override
    protected void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeClock(player);
        }
    }

    @Override
    public void reload() {
    }

    private ItemStack clock() {
        ItemStack stack = new ItemStack(Material.CLOCK);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Msg.color(plugin.getConfig().getString("menu.item-name", "&b&l功能菜单")));
            List<String> lore = plugin.getConfig().getStringList("menu.item-lore");
            if (lore.isEmpty()) {
                lore = List.of("&7右键打开功能菜单", "&7也可以输入 &f/menu", "&8沧蓝工坊");
            }
            List<String> colored = new ArrayList<>();
            for (String line : lore) {
                colored.add(Msg.color(line));
            }
            meta.setLore(colored);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private boolean isMenuClock(ItemStack stack) {
        if (stack == null || stack.getType() != Material.CLOCK) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private void giveClock(Player player) {
        if (player.getInventory().containsAtLeast(clock(), 1)) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isMenuClock(item)) {
                return;
            }
        }
        int prefer = Math.max(0, Math.min(8, plugin.getConfig().getInt("menu.slot", 8)));
        ItemStack existing = player.getInventory().getItem(prefer);
        if (existing == null || existing.getType() == Material.AIR) {
            player.getInventory().setItem(prefer, clock());
        } else {
            player.getInventory().addItem(clock());
        }
        Msg.send(player, "&7已把「&b功能菜单&7」放进你的背包，&f右键 &7即可打开。");
    }

    private void removeClock(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isMenuClock(player.getInventory().getItem(slot))) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length >= 1) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give") || sub.equals("获取") || sub.equals("领取")) {
                giveClock(player);
                return true;
            }
            if (sub.equals("close") || sub.equals("关闭")) {
                player.closeInventory();
                return true;
            }
            if (sub.equals("reload") || sub.equals("重载")) {
                if (!checkAdmin(sender)) {
                    return true;
                }
                plugin.reloadEverything();
                Msg.send(sender, "&a菜单配置已重载。");
                return true;
            }
        }
        openMain(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (args.length == 1) {
            return List.of("获取", "关闭", "重载");
        }
        return List.of();
    }

    private void openMain(Player player) {
        new MainGui(player).open(player);
    }

    private void run(Player player, String command) {
        player.closeInventory();
        Bukkit.dispatchCommand(player, command);
    }

    private void sendInfo(Player player) {
        double tps = Math.min(20.0D, Bukkit.getTPS()[0]);
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
        Msg.header(player, "&b&l服务器信息");
        Msg.raw(player, "&7在线玩家：&f" + Bukkit.getOnlinePlayers().size() + " &7/ &f" + Bukkit.getMaxPlayers());
        Msg.raw(player, "&7服务器 TPS：&f" + String.format("%.2f", tps));
        Msg.raw(player, "&7内存占用：&f" + used + " MB");
        Msg.raw(player, "&7你的金币：&f" + com.canglan.survival.core.Util.money(plugin.economy().balance(player.getUniqueId())));
        Msg.raw(player, "&7你的延迟：&f" + player.getPing() + " ms");
        Msg.raw(player, "&8输入 &f/cl 我的 &8看看你还能用什么功能");
    }

    private final class MenuListener implements Listener {

        @EventHandler(priority = EventPriority.HIGH)
        public void onInteract(PlayerInteractEvent event) {
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            if (!isMenuClock(event.getItem())) {
                return;
            }
            event.setCancelled(true);
            openMain(event.getPlayer());
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onDrop(PlayerDropItemEvent event) {
            if (isMenuClock(event.getItemDrop().getItemStack())) {
                event.setCancelled(true);
                Msg.actionBar(event.getPlayer(), "&c功能菜单不能丢弃");
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onJoin(PlayerJoinEvent event) {
            if (!plugin.getConfig().getBoolean("menu.give-on-join", true)) {
                return;
            }
            Player player = event.getPlayer();
            later(40L, () -> {
                if (player.isOnline()) {
                    giveClock(player);
                }
            });
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onRespawn(PlayerRespawnEvent event) {
            if (!plugin.getConfig().getBoolean("menu.give-on-join", true)) {
                return;
            }
            Player player = event.getPlayer();
            later(20L, () -> {
                if (player.isOnline()) {
                    giveClock(player);
                }
            });
        }
    }

    private final class MainGui extends Gui {

        private final Map<Integer, String> local = new HashMap<>();

        private MainGui(Player player) {
            super(MenuModule.this.plugin, 54, "&b&l功能菜单 &7· &f" + MenuModule.this.plugin.plainServerName());
            border(Material.GRAY_STAINED_GLASS_PANE);
            put(10, Material.EMERALD, "&a&l系统商店",
                    List.of("&7用金币买卖物品", "&8点击打开"), "cmd:shop");
            put(11, Material.COMPASS, "&b&l地标传送",
                    List.of("&7去全服各地标", "&8点击打开地标列表"), "cmd:warps");
            put(12, Material.ENDER_PEARL, "&d&l随机传送",
                    List.of("&7随机传送到安全位置", "&8点击随机传送"), "cmd:rtp");
            put(13, Material.RED_BED, "&c&l我的家园",
                    List.of("&7回家 / 管理家园", "&8点击打开家园列表"), "cmd:homes");
            put(14, Material.GRASS_BLOCK, "&2&l我的领地",
                    List.of("&7查看领地信息、圈地", "&8点击查看领地"), "cmd:claim 信息");
            put(15, Material.PLAYER_HEAD, "&e&l玩家传送",
                    List.of("&7向在线玩家发送传送请求", "&8点击选择玩家"), "players");

            put(20, Material.GOLD_INGOT, "&6&l每日签到",
                    List.of("&7每天签到领金币", "&8点击签到"), "cmd:signin");
            put(21, Material.BOOK, "&f&l排行榜",
                    List.of("&7金币 / 击杀 / 在线时长", "&8点击查看榜单"), "cmd:top");
            put(22, Material.PAPER, "&7&l我的统计",
                    List.of("&7击杀、死亡、在线时长", "&8点击查看"), "cmd:stats");
            put(23, Material.SUNFLOWER, "&6&l我的金币",
                    List.of("&7查看余额与转账", "&8点击查看"), "cmd:money");
            put(24, Material.OAK_SIGN, "&b&l服务器公告",
                    List.of("&7查看当前公告", "&8点击查看"), "cmd:notice 列表");

            put(30, Material.NETHER_STAR, "&b&l服务器信息",
                    List.of("&7TPS、内存、在线人数", "&8点击查看"), "info");
            put(31, Material.REDSTONE_TORCH, "&c&l计分板开关",
                    List.of("&7显示 / 隐藏右侧计分板", "&8点击切换"), "cmd:sb 开关");
            put(32, Material.WRITABLE_BOOK, "&f&l服务器规则",
                    List.of("&7新手必看", "&8点击查看"), "cmd:welcome");

            put(49, Material.BARRIER, "&c&l关闭菜单",
                    List.of("&7点击关闭"), "close");
        }

        private void put(int slot, Material material, String name, List<String> lore, String action) {
            inventory.setItem(slot, item(material, name, lore));
            local.put(slot, action);
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            String action = local.get(event.getRawSlot());
            if (action == null) {
                return;
            }
            if (action.equals("close")) {
                player.closeInventory();
            } else if (action.equals("info")) {
                player.closeInventory();
                sendInfo(player);
            } else if (action.equals("players")) {
                new PlayerGui(player).open(player);
            } else if (action.startsWith("cmd:")) {
                run(player, action.substring(4));
            }
        }
    }

    private final class PlayerGui extends Gui {

        private final Map<Integer, String> local = new HashMap<>();

        private PlayerGui(Player viewer) {
            super(MenuModule.this.plugin, 54, "&b&l玩家传送 &7· &f选择要传送的玩家");
            border(Material.GRAY_STAINED_GLASS_PANE);
            int slot = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getUniqueId().equals(viewer.getUniqueId())) {
                    continue;
                }
                if (slot >= 45) {
                    break;
                }
                while (slot == 49) {
                    slot++;
                }
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                ItemMeta meta = head.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(Msg.color("&e" + online.getName()));
                    String distance = online.getWorld().equals(viewer.getWorld())
                            ? Math.round(online.getLocation().distance(viewer.getLocation())) + " 格"
                            : "不同世界";
                    List<String> lore = new ArrayList<>();
                    lore.add(Msg.color("&7所在世界：&f" + online.getWorld().getName()));
                    lore.add(Msg.color("&7距离你：&f" + distance));
                    lore.add(Msg.color("&8点击发送传送请求 /tpa"));
                    meta.setLore(lore);
                    head.setItemMeta(meta);
                }
                inventory.setItem(slot, head);
                local.put(slot, online.getName());
                slot++;
            }
            if (local.isEmpty()) {
                inventory.setItem(22, item(Material.PAPER, "&7当前没有其他玩家在线"));
            }
            inventory.setItem(49, item(Material.ARROW, "&a&l返回主菜单", List.of("&7点击返回")));
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            if (event.getRawSlot() == 49) {
                new MainGui(player).open(player);
                return;
            }
            String name = local.get(event.getRawSlot());
            if (name == null) {
                return;
            }
            run(player, "tpa " + name);
        }
    }
}
