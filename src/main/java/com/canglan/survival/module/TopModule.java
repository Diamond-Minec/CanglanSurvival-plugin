package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 模块 18 · 排行榜
 * 财富、击杀、死亡、在线时长、挖掘、放置等榜单，前 50 名。
 */
public class TopModule extends Module {

    public TopModule(CanglanSurvival plugin) {
        super(plugin, "top", "排行榜",
                "全服排行榜：金币、击杀、死亡、在线时长、挖掘、放置，输入 /top 查看默认榜单。",
                true, "top");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        String type = "money";
        int page = 1;
        if (args.length >= 1) {
            type = normalize(args[0]);
            if (type == null) {
                Msg.send(sender, "&c可用榜单：&f金币 money&7、&f击杀 kills&7、&f死亡 deaths&7、"
                        + "&f在线 playtime&7、&f挖掘 broken&7、&f放置 placed");
                return true;
            }
        }
        if (args.length >= 2) {
            Integer parsed = parseInt(sender, args[1]);
            if (parsed == null) {
                return true;
            }
            page = Math.max(1, parsed);
        }
        show(sender, type, page);
        return true;
    }

    private String normalize(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "money", "金币", "财富", "bal" -> "money";
            case "kills", "击杀" -> "kills";
            case "deaths", "死亡" -> "deaths";
            case "playtime", "在线", "在线时长", "时间" -> "playtime";
            case "broken", "挖掘" -> "broken";
            case "placed", "放置" -> "placed";
            default -> null;
        };
    }

    private String title(String type) {
        return switch (type) {
            case "kills" -> "&b&l击杀排行榜";
            case "deaths" -> "&b&l死亡排行榜";
            case "playtime" -> "&b&l在线时长排行榜";
            case "broken" -> "&b&l挖掘排行榜";
            case "placed" -> "&b&l放置排行榜";
            default -> "&b&l财富排行榜";
        };
    }

    private void show(CommandSender sender, String type, int page) {
        List<String> lines = new ArrayList<>();
        if (type.equals("money")) {
            List<Map.Entry<UUID, Double>> top = plugin.economy().top(50);
            for (int i = 0; i < top.size(); i++) {
                Map.Entry<UUID, Double> entry = top.get(i);
                lines.add("&8" + (i + 1) + ". &f" + nameOf(entry.getKey()) + " &8- &6"
                        + Util.money(entry.getValue()) + " 金币");
            }
        } else {
            List<Map.Entry<UUID, Long>> top = plugin.stats().top(type, 50);
            for (int i = 0; i < top.size(); i++) {
                Map.Entry<UUID, Long> entry = top.get(i);
                String value = type.equals("playtime")
                        ? Util.time(entry.getValue()) : String.valueOf(entry.getValue());
                lines.add("&8" + (i + 1) + ". &f" + nameOf(entry.getKey()) + " &8- &f" + value);
            }
        }
        Msg.header(sender, title(type) + " &7第 " + page + " 页");
        if (lines.isEmpty()) {
            Msg.raw(sender, "&7榜单暂时是空的。");
            return;
        }
        for (String line : Util.page(lines, page, 10)) {
            Msg.raw(sender, line);
        }
        Msg.raw(sender, "&7榜单：&f/top 金币 &7| &f/top 击杀 &7| &f/top 在线 &7| &f/top 挖掘 &7| &f/top 放置");
    }

    private String nameOf(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (args.length == 1) {
            List<String> options = List.of("金币", "击杀", "死亡", "在线", "挖掘", "放置");
            List<String> out = new ArrayList<>();
            for (String option : options) {
                if (option.startsWith(args[0])) {
                    out.add(option);
                }
            }
            return out;
        }
        return List.of();
    }
}
