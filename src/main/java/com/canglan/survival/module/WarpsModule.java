package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import com.canglan.survival.core.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 模块 08 · 地标传送
 * 全服公用传送点：/warp 传送、/warps 列表、/setwarp 与 /delwarp 管理。
 */
public class WarpsModule extends Module {

    private final YamlStore store;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public WarpsModule(CanglanSurvival plugin) {
        super(plugin, "warps", "地标传送",
                "全服公用传送点 /warp，管理员可在任意位置用 /setwarp 建立地标，玩家输入 /warps 查看列表。",
                true, "warp", "warps", "setwarp", "delwarp");
        this.store = new YamlStore(plugin, "warps.yml");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    @Override
    public void reload() {
        store.load();
    }

    private List<String> names() {
        List<String> list = new ArrayList<>();
        ConfigurationSection section = store.yaml().getConfigurationSection("warps");
        if (section != null) {
            list.addAll(section.getKeys(false));
        }
        return list;
    }

    private Location location(String name) {
        return Util.parseLoc(store.yaml().getString("warps." + name.toLowerCase(Locale.ROOT) + ".loc"));
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        switch (command.toLowerCase(Locale.ROOT)) {
            case "setwarp", "设置地标" -> setWarp(sender, args);
            case "delwarp", "删除地标" -> delWarp(sender, args);
            case "warps", "地标列表" -> list(sender, args);
            default -> {
                if (args.length == 0) {
                    list(sender, args);
                    return true;
                }
                warp(sender, args[0]);
            }
        }
        return true;
    }

    private void warp(CommandSender sender, String rawName) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        String name = rawName.toLowerCase(Locale.ROOT);
        Location target = location(name);
        if (target == null) {
            Msg.send(sender, "&c没有叫 &f" + rawName + " &c的地标，输入 &f/warps &c查看全部地标。");
            return;
        }
        if (plugin.getConfig().getBoolean("warps.per-warp-permission", false)
                && !player.hasPermission("canglan.warp." + name)) {
            Msg.send(sender, "&c你没有传送到地标 &f" + name + " &c的权限。");
            return;
        }
        long cooldown = plugin.getConfig().getLong("warps.cooldown", 5L);
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldown * 1000L) {
            long remain = (cooldown * 1000L - (now - last)) / 1000L + 1;
            Msg.send(sender, "&c传送冷却中，请 &f" + remain + " &c秒后再试。");
            return;
        }
        cooldowns.put(player.getUniqueId(), now);
        if (!Util.safeTeleport(player, target)) {
            Msg.send(sender, "&c传送失败，目标世界可能未加载。");
            return;
        }
        Msg.send(sender, "&a已传送到地标 &f" + name + "&a。");
    }

    private void list(CommandSender sender, String[] args) {
        List<String> names = names();
        int page = 1;
        if (args.length > 0) {
            Integer parsed = parseInt(sender, args[0]);
            if (parsed == null) {
                return;
            }
            page = parsed;
        }
        Msg.header(sender, "&b&l地标列表 &7共 " + names.size() + " 个");
        if (names.isEmpty()) {
            Msg.raw(sender, "&7还没有任何地标。管理员可用 &f/setwarp <名称> &7创建。");
            return;
        }
        List<String> lines = new ArrayList<>();
        for (String name : names) {
            Location loc = location(name);
            lines.add("&8- &f" + name + " &7" + (loc == null ? "&c坐标异常" : Util.loc(loc)));
        }
        for (String line : Util.page(lines, page, 10)) {
            Msg.raw(sender, line);
        }
    }

    private void setWarp(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) {
            return;
        }
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 1) {
            Msg.send(sender, "&c用法：/setwarp <名称>");
            return;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        if (!name.matches("[a-zA-Z0-9_\\u4e00-\\u9fa5]{1,24}")) {
            Msg.send(sender, "&c地标名称只能包含中英文、数字和下划线，长度 1~24。");
            return;
        }
        store.yaml().set("warps." + name + ".loc", Util.locString(player.getLocation()));
        store.yaml().set("warps." + name + ".owner", player.getName());
        store.yaml().set("warps." + name + ".created", System.currentTimeMillis());
        store.save();
        Msg.send(sender, "&a已创建地标 &f" + name + "&a，玩家可以输入 &f/warp " + name + " &a传送。");
    }

    private void delWarp(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) {
            return;
        }
        if (args.length < 1) {
            Msg.send(sender, "&c用法：/delwarp <名称>");
            return;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        if (location(name) == null) {
            Msg.send(sender, "&c没有叫 &f" + args[0] + " &c的地标。");
            return;
        }
        store.yaml().set("warps." + name, null);
        store.save();
        Msg.send(sender, "&a已删除地标 &f" + name + "&a。");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (args.length == 1 && (command.equalsIgnoreCase("warp") || command.equalsIgnoreCase("delwarp"))) {
            List<String> out = new ArrayList<>();
            for (String name : names()) {
                if (name.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(name);
                }
            }
            return out;
        }
        return List.of();
    }
}
