package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import com.canglan.survival.core.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 模块 13 · 玩家举报
 * /report 举报玩家，记录到文件并实时提醒在线管理员，管理员可用 /reports 查看。
 */
public class ReportModule extends Module {

    private final YamlStore store;
    private final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public ReportModule(CanglanSurvival plugin) {
        super(plugin, "report", "玩家举报",
                "/report 向管理员举报违规玩家，举报会存档并提醒在线管理员，管理员用 /reports 查看处理。",
                true, "report", "reports");
        this.store = new YamlStore(plugin, "reports.yml");
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

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        if (command.equalsIgnoreCase("reports") || (args.length > 0
                && (args[0].equalsIgnoreCase("list") || args[0].equals("列表")))) {
            if (!check(sender, "canglan.report.admin")) {
                return true;
            }
            list(sender, args);
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("clear") || args[0].equals("清空"))) {
            if (!check(sender, "canglan.report.admin")) {
                return true;
            }
            store.yaml().set("reports", null);
            store.save();
            Msg.send(sender, "&a已清空全部举报记录。");
            return true;
        }
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 2) {
            Msg.send(sender, "&c用法：/report <玩家> <举报原因>");
            return true;
        }
        String targetName = args[0];
        String reason = Util.join(args, 1);
        long now = System.currentTimeMillis();
        String id = String.valueOf(now);
        store.yaml().set("reports." + id + ".reporter", player.getName());
        store.yaml().set("reports." + id + ".reporter-uuid", player.getUniqueId().toString());
        store.yaml().set("reports." + id + ".target", targetName);
        store.yaml().set("reports." + id + ".reason", reason);
        store.yaml().set("reports." + id + ".time", now);
        store.yaml().set("reports." + id + ".handled", false);
        store.save();
        Msg.send(player, "&a举报已提交，感谢你的反馈！管理员会尽快处理。");
        Msg.send(player, "&7举报对象：&f" + targetName + " &7原因：&f" + reason);
        int notified = 0;
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("canglan.report.notify")) {
                Msg.send(admin, "&c&l[举报] &f" + player.getName() + " &7举报了 &f" + targetName
                        + " &7原因：&f" + reason);
                admin.sendActionBar(Msg.color("&c收到一条新举报，输入 /reports 查看"));
                notified++;
            }
        }
        if (notified == 0) {
            Msg.send(player, "&7当前没有管理员在线，举报已记录，管理员上线后会看到。");
        }
        appendLog(player.getName(), targetName, reason);
        return true;
    }

    private void appendLog(String reporter, String target, String reason) {
        try {
            File file = new File(plugin.getDataFolder(), "logs");
            if (!file.exists() && !file.mkdirs()) {
                return;
            }
            try (FileWriter writer = new FileWriter(new File(file, "reports.log"), true)) {
                writer.write("[" + format.format(new Date()) + "] " + reporter + " 举报 " + target + "：" + reason + "\n");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("写入举报日志失败：" + e.getMessage());
        }
    }

    private void list(CommandSender sender, String[] args) {
        ConfigurationSection section = store.yaml().getConfigurationSection("reports");
        Msg.header(sender, "&b&l举报记录");
        if (section == null || section.getKeys(false).isEmpty()) {
            Msg.raw(sender, "&7还没有任何举报记录。");
            return;
        }
        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort((a, b) -> Long.compare(Long.parseLong(b), Long.parseLong(a)));
        int limit = Math.min(15, keys.size());
        for (int i = 0; i < limit; i++) {
            String key = keys.get(i);
            long time = store.yaml().getLong("reports." + key + ".time", 0L);
            String reporter = store.yaml().getString("reports." + key + ".reporter", "?");
            String target = store.yaml().getString("reports." + key + ".target", "?");
            String reason = store.yaml().getString("reports." + key + ".reason", "");
            Msg.raw(sender, "&8#" + (i + 1) + " &7" + format.format(new Date(time))
                    + " &f" + reporter + " &7→ &f" + target + " &8| &7" + reason);
        }
        Msg.raw(sender, "&7共 &f" + keys.size() + " &7条举报，最近显示 15 条。&8（/report 清空 可清空记录）");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
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
