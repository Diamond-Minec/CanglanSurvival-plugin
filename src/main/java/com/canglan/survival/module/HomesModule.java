package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import com.canglan.survival.core.YamlStore;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 模块 10 · 家园
 * 玩家可以设置多个家，按权限决定数量上限。
 */
public class HomesModule extends Module {

    private final YamlStore store;

    public HomesModule(CanglanSurvival plugin) {
        super(plugin, "homes", "家园",
                "玩家家园：/sethome 设置家、/home 回家、/delhome 删除家、/homes 查看全部家园，上限由权限决定。",
                true, "home", "sethome", "delhome", "homes");
        this.store = new YamlStore(plugin, "homes.yml");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
        store.save();
    }

    @Override
    public void reload() {
        store.load();
    }

    private int limit(Player player) {
        if (player.hasPermission("canglan.home.unlimited")) {
            return 50;
        }
        for (int i = 50; i >= 1; i--) {
            if (player.hasPermission("canglan.home.limit." + i)) {
                return i;
            }
        }
        return plugin.getConfig().getInt("homes.default-limit", 1);
    }

    private List<String> names(Player player) {
        List<String> list = new ArrayList<>();
        ConfigurationSection section = store.yaml().getConfigurationSection("homes." + player.getUniqueId());
        if (section != null) {
            list.addAll(section.getKeys(false));
        }
        return list;
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        switch (command.toLowerCase(Locale.ROOT)) {
            case "sethome" -> setHome(player, args);
            case "delhome" -> delHome(player, args);
            case "homes" -> list(player);
            default -> home(player, args);
        }
        return true;
    }

    private void setHome(Player player, String[] args) {
        String world = player.getWorld().getName();
        if (plugin.getConfig().getStringList("homes.blocked-worlds").contains(world)) {
            Msg.send(player, "&c这个世界不能设置家园。");
            return;
        }
        String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home";
        List<String> names = names(player);
        if (!names.contains(name) && names.size() >= limit(player)) {
            Msg.send(player, "&c你的家园数量已达上限 &f" + limit(player) + " &c个，可用 &f/delhome <名称> &c删除一个。");
            return;
        }
        store.yaml().set("homes." + player.getUniqueId() + "." + name, Util.locString(player.getLocation()));
        store.save();
        Msg.send(player, "&a家园 &f" + name + " &a已设置在这里（&f" + Util.loc(player.getLocation()) + "&a）。");
    }

    private void home(Player player, String[] args) {
        String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home";
        String raw = store.yaml().getString("homes." + player.getUniqueId() + "." + name);
        if (raw == null) {
            Msg.send(player, "&c你还没有设置叫 &f" + name + " &c的家，先输入 &f/sethome " + name + " &c设置。");
            return;
        }
        Location target = Util.parseLoc(raw);
        if (target == null) {
            Msg.send(player, "&c家园坐标所在的世界已不存在，请联系服主。");
            return;
        }
        if (!Util.safeTeleport(player, target)) {
            Msg.send(player, "&c回家失败，请稍后再试。");
            return;
        }
        Msg.send(player, "&a已回到家园 &f" + name + "&a。");
    }

    private void delHome(Player player, String[] args) {
        String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home";
        if (store.yaml().getString("homes." + player.getUniqueId() + "." + name) == null) {
            Msg.send(player, "&c你没有叫 &f" + name + " &c的家园。");
            return;
        }
        store.yaml().set("homes." + player.getUniqueId() + "." + name, null);
        store.save();
        Msg.send(player, "&a已删除家园 &f" + name + "&a。");
    }

    private void list(Player player) {
        List<String> names = names(player);
        Msg.header(player, "&b&l我的家园 &7" + names.size() + "/" + limit(player));
        if (names.isEmpty()) {
            Msg.raw(player, "&7你还没有家园，站在想设置的位置输入 &f/sethome &7即可。");
            return;
        }
        for (String name : names) {
            Location loc = Util.parseLoc(store.yaml().getString("homes." + player.getUniqueId() + "." + name));
            Msg.raw(player, "&8- &f" + name + " &7" + (loc == null ? "&c坐标异常" : Util.loc(loc)));
        }
        Msg.raw(player, "&7回家：&f/home <名称> &7| 删除：&f/delhome <名称>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length == 1 && (command.equalsIgnoreCase("home") || command.equalsIgnoreCase("delhome"))) {
            List<String> out = new ArrayList<>();
            for (String name : names(player)) {
                if (name.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(name);
                }
            }
            return out;
        }
        return List.of();
    }
}
