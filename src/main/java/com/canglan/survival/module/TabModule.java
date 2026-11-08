package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import com.canglan.survival.core.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 模块 02 · TAB 列表
 * 自定义 TAB 页眉、页脚、玩家列表名称（带权限组前缀、延迟、在线人数等）。
 */
public class TabModule extends Module {

    private final YamlStore store;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public TabModule(CanglanSurvival plugin) {
        super(plugin, "tab", "TAB 列表",
                "自定义 TAB（玩家列表）的页眉、页脚与每个玩家的显示名称，支持在线人数、延迟、金币等变量。",
                true, "tab");
        this.store = new YamlStore(plugin, "tab.yml");
    }

    @Override
    protected void onEnable() {
        // 每 2 秒刷新一次 TAB
        every(40L, this::updateAll);
        plugin.getServer().getScheduler().runTask(plugin, this::updateAll);
    }

    @Override
    protected void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setPlayerListHeaderFooter("", "");
            player.setPlayerListName(null);
        }
    }

    @Override
    public void reload() {
        store.load();
        updateAll();
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    private void update(Player player) {
        if (store.yaml().getBoolean("hidden." + player.getUniqueId(), false)) {
            player.setPlayerListHeaderFooter("", "");
            player.setPlayerListName(null);
            return;
        }
        String header = apply(player, joinLines(plugin.getConfig().getStringList("tab.header")));
        String footer = apply(player, joinLines(plugin.getConfig().getStringList("tab.footer")));
        player.setPlayerListHeaderFooter(Msg.color(header), Msg.color(footer));
        String format = plugin.getConfig().getString("tab.player-list-format", "&7[{group}] &f{player}");
        player.setPlayerListName(Msg.color(apply(player, format)));
    }

    private String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        return String.join("\n", lines);
    }

    private String apply(Player player, String text) {
        if (text == null) {
            return "";
        }
        double[] tps = Bukkit.getTPS();
        String group = plugin.perms().groupName(player.getUniqueId());
        String prefix = plugin.perms().prefixOf(player.getUniqueId());
        return text
                .replace("{player}", player.getName())
                .replace("{display}", player.getDisplayName())
                .replace("{server}", plugin.serverName())
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("{ping}", String.valueOf(player.getPing()))
                .replace("{tps}", String.format("%.1f", Math.min(20.0D, tps[0])))
                .replace("{money}", Util.money(plugin.economy().balance(player.getUniqueId())))
                .replace("{world}", player.getWorld().getName())
                .replace("{group}", group)
                .replace("{prefix}", prefix)
                .replace("{time}", timeFormat.format(new Date()))
                .replace("{date}", dateFormat.format(new Date()));
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info") || args[0].equals("信息")) {
            Msg.header(sender, "&b&lTAB 列表");
            Msg.raw(sender, "&7当前页眉：");
            for (String line : plugin.getConfig().getStringList("tab.header")) {
                Msg.raw(sender, "&8| &f" + line);
            }
            Msg.raw(sender, "&7当前页脚：");
            for (String line : plugin.getConfig().getStringList("tab.footer")) {
                Msg.raw(sender, "&8| &f" + line);
            }
            Msg.raw(sender, "&7可用变量：&f{player} {online} {max} {ping} {tps} {money} {world} {group} {prefix} {time} {date}");
            Msg.raw(sender, "&7玩家开关：&f/tab 关闭 &7| &f/tab 开启 &7| 管理员：&f/tab 重载");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "off", "关闭", "hide" -> {
                Player player = asPlayer(sender);
                if (player == null) {
                    return true;
                }
                store.yaml().set("hidden." + player.getUniqueId(), true);
                store.save();
                update(player);
                Msg.send(sender, "&a已关闭你自己的 TAB 页眉/页脚显示。");
            }
            case "on", "开启", "show" -> {
                Player player = asPlayer(sender);
                if (player == null) {
                    return true;
                }
                store.yaml().set("hidden." + player.getUniqueId(), false);
                store.save();
                update(player);
                Msg.send(sender, "&a已重新开启你自己的 TAB 显示。");
            }
            case "reload", "重载" -> {
                if (!checkAdmin(sender)) {
                    return true;
                }
                plugin.reloadEverything();
                Msg.send(sender, "&aTAB 配置已重载。");
            }
            default -> Msg.send(sender, "&c用法：/tab [信息|开启|关闭|重载]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String option : List.of("信息", "开启", "关闭", "重载")) {
                if (option.startsWith(args[0])) {
                    out.add(option);
                }
            }
            return out;
        }
        return List.of();
    }
}
