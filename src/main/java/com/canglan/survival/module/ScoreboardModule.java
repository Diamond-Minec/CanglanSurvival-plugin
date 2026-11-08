package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import com.canglan.survival.core.YamlStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 模块 04 · 计分板
 * 右侧实时计分板：在线人数、金币、击杀、延迟、TPS、在线时长等。
 */
public class ScoreboardModule extends Module {

    private static final String OBJECTIVE_NAME = "canglan_sidebar";

    private final YamlStore store;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");

    public ScoreboardModule(CanglanSurvival plugin) {
        super(plugin, "scoreboard", "计分板",
                "右侧实时计分板，显示在线人数、金币、击杀/死亡、延迟、TPS、在线时长等信息，玩家可用 /sb 关闭。",
                true, "sb", "scoreboard");
        this.store = new YamlStore(plugin, "scoreboard.yml");
    }

    @Override
    protected void onEnable() {
        every(20L, this::updateAll);
        plugin.getServer().getScheduler().runTask(plugin, this::updateAll);
    }

    @Override
    protected void onDisable() {
        Scoreboard main = Bukkit.getScoreboardManager() != null
                ? Bukkit.getScoreboardManager().getMainScoreboard() : null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (main != null) {
                player.setScoreboard(main);
            }
        }
        boards.clear();
    }

    @Override
    public void reload() {
        store.load();
        updateAll();
    }

    private void updateAll() {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (store.yaml().getBoolean("hidden." + player.getUniqueId(), false)) {
                continue;
            }
            update(player);
        }
    }

    private void update(Player player) {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(),
                k -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        String title = plugin.getConfig().getString("scoreboard.title", "{server}");
        title = title.replace("{server}", plugin.serverName());
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_NAME, "dummy", Msg.component(Msg.color(title)));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            objective.displayName(Msg.component(Msg.color(title)));
        }
        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        if (lines.isEmpty()) {
            lines = List.of("&7在线：&f{online}", "&7金币：&f{money}", "&7击杀：&f{kills}",
                    "&7延迟：&f{ping}ms", "&7TPS：&f{tps}");
        }
        int index = 0;
        for (String raw : lines) {
            if (index >= 15) {
                break;
            }
            String entry = "§" + Integer.toHexString(index);
            Team team = board.getTeam("line_" + index);
            if (team == null) {
                team = board.registerNewTeam("line_" + index);
            }
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
            team.prefix(Msg.component(apply(player, raw)));
            objective.getScore(entry).setScore(lines.size() - index);
            index++;
        }
        player.setScoreboard(board);
    }

    private String apply(Player player, String text) {
        if (text == null) {
            return "";
        }
        double[] tps = Bukkit.getTPS();
        return text
                .replace("{player}", player.getName())
                .replace("{server}", plugin.serverName())
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("{money}", Util.money(plugin.economy().balance(player.getUniqueId())))
                .replace("{kills}", String.valueOf(plugin.stats().get(player.getUniqueId(), "kills")))
                .replace("{deaths}", String.valueOf(plugin.stats().get(player.getUniqueId(), "deaths")))
                .replace("{ping}", String.valueOf(player.getPing()))
                .replace("{tps}", String.format("%.1f", Math.min(20.0D, tps[0])))
                .replace("{playtime}", Util.time(plugin.stats().get(player.getUniqueId(), "playtime")))
                .replace("{world}", player.getWorld().getName())
                .replace("{group}", plugin.perms().groupName(player.getUniqueId()))
                .replace("{time}", timeFormat.format(new Date()))
                .replace("{line}", Msg.color("&8&m----------"));
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info") || args[0].equals("信息")) {
            Player player = asPlayer(sender);
            if (player == null) {
                return true;
            }
            boolean hidden = store.yaml().getBoolean("hidden." + player.getUniqueId(), false);
            Msg.send(sender, "&7你的计分板状态：" + (hidden ? "&c已关闭" : "&a已开启")
                    + " &7（&f/sb 开关&7，管理员：&f/sb 重载&7）");
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("reload") || sub.equals("重载")) {
            if (!checkAdmin(sender)) {
                return true;
            }
            reload();
            Msg.send(sender, "&a计分板配置已重载。");
            return true;
        }
        if (sub.equals("on") || sub.equals("开启") || sub.equals("off") || sub.equals("关闭")
                || sub.equals("toggle") || sub.equals("开关")) {
            Player player = asPlayer(sender);
            if (player == null) {
                return true;
            }
            boolean hidden = store.yaml().getBoolean("hidden." + player.getUniqueId(), false);
            boolean newHidden = sub.equals("关闭") || sub.equals("off") ? true
                    : sub.equals("开启") || sub.equals("on") ? false : !hidden;
            store.yaml().set("hidden." + player.getUniqueId(), newHidden);
            store.save();
            if (newHidden) {
                if (Bukkit.getScoreboardManager() != null) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
                Msg.send(sender, "&a已关闭计分板，输入 &f/sb 开启 &a可恢复。");
            } else {
                update(player);
                Msg.send(sender, "&a已开启计分板。");
            }
            return true;
        }
        Msg.send(sender, "&c用法：/sb [开启|关闭|重载]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String option : List.of("开启", "关闭", "重载")) {
                if (option.startsWith(args[0])) {
                    out.add(option);
                }
            }
            return out;
        }
        return List.of();
    }
}
