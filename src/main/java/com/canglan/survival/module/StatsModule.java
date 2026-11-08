package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 模块 19 · 玩家统计
 * 查看自己或他人的击杀、死亡、挖掘、在线时长、金币、权限组等数据。
 */
public class StatsModule extends Module {

    public StatsModule(CanglanSurvival plugin) {
        super(plugin, "stats", "玩家统计",
                "/stats 查看玩家数据：击杀、死亡、挖掘、放置、在线时长、金币、权限组。",
                true, "stats");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        UUID target;
        String name;
        if (args.length >= 1) {
            if (!sender.hasPermission("canglan.stats.others")) {
                Msg.send(sender, "&c你没有查看其他玩家数据的权限。");
                return true;
            }
            Player online = Bukkit.getPlayerExact(args[0]);
            if (online != null) {
                target = online.getUniqueId();
                name = online.getName();
            } else {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
                if (!offline.hasPlayedBefore()) {
                    Msg.send(sender, "&c没有找到玩家：&f" + args[0]);
                    return true;
                }
                target = offline.getUniqueId();
                name = offline.getName();
            }
            if (args.length >= 2 && (args[1].equalsIgnoreCase("reset") || args[1].equals("重置"))) {
                if (!checkAdmin(sender)) {
                    return true;
                }
                plugin.stats().set(target, "kills", 0);
                plugin.stats().set(target, "deaths", 0);
                plugin.stats().set(target, "broken", 0);
                plugin.stats().set(target, "placed", 0);
                Msg.send(sender, "&a已重置 &f" + name + " &a的统计数据。");
                return true;
            }
        } else {
            Player player = asPlayer(sender);
            if (player == null) {
                return true;
            }
            target = player.getUniqueId();
            name = player.getName();
        }
        show(sender, target, name);
        return true;
    }

    private void show(CommandSender sender, UUID uuid, String name) {
        Msg.header(sender, "&b&l" + name + " &7的数据统计");
        Msg.raw(sender, "&8· &7权限组：&f" + plugin.perms().groupName(uuid)
                + " &8| &7金币：&6" + Util.money(plugin.economy().balance(uuid)));
        Msg.raw(sender, "&8· &7击杀：&f" + plugin.stats().get(uuid, "kills")
                + " &8| &7死亡：&f" + plugin.stats().get(uuid, "deaths")
                + " &8| &7K/D：&f" + kd(uuid));
        Msg.raw(sender, "&8· &7挖掘方块：&f" + plugin.stats().get(uuid, "broken")
                + " &8| &7放置方块：&f" + plugin.stats().get(uuid, "placed"));
        Msg.raw(sender, "&8· &7在线时长：&f" + Util.time(plugin.stats().get(uuid, "playtime"))
                + " &8| &7进服次数：&f" + plugin.stats().get(uuid, "joins"));
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getFirstPlayed() > 0) {
            Msg.raw(sender, "&8· &7首次进服：&f"
                    + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(offline.getFirstPlayed())));
        }
        Msg.raw(sender, "&8&m------------------------------------------------");
    }

    private String kd(UUID uuid) {
        long kills = plugin.stats().get(uuid, "kills");
        long deaths = plugin.stats().get(uuid, "deaths");
        if (deaths == 0) {
            return kills == 0 ? "0.00" : String.format("%.2f", (double) kills);
        }
        return String.format("%.2f", (double) kills / (double) deaths);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (args.length == 1) {
            List<String> out = new java.util.ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    out.add(player.getName());
                }
            }
            return out;
        }
        return List.of();
    }
}
