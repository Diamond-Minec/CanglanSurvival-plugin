package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 模块 20 · 服务器监控
 * TPS、内存、实体、区块、各世界负载与低 TPS 自动报警。
 */
public class MonitorModule extends Module {

    private long lastAlert;

    public MonitorModule(CanglanSurvival plugin) {
        super(plugin, "monitor", "服务器监控",
                "服务器状态监控：TPS、内存、实体数、区块数、各世界情况，TPS 过低时自动提醒管理员。",
                true, "monitor");
    }

    @Override
    protected void onEnable() {
        int interval = Math.max(20, plugin.getConfig().getInt("monitor.check-interval", 60));
        every(interval * 20L, this::check);
    }

    @Override
    protected void onDisable() {
    }

    @Override
    public void reload() {
    }

    private void check() {
        double threshold = plugin.getConfig().getDouble("monitor.tps-alert-threshold", 15.0D);
        double tps = Bukkit.getTPS()[0];
        if (tps >= threshold) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("monitor.alert-cooldown", 300L) * 1000L;
        if (now - lastAlert < cooldown) {
            return;
        }
        lastAlert = now;
        String message = Msg.color("&c[监控警告] &f服务器 TPS 偏低：&e" + String.format("%.2f", tps)
                + " &7（阈值 " + threshold + "），内存 " + memory());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("canglan.monitor.notify")) {
                Msg.raw(player, message);
            }
        }
        plugin.getLogger().warning("服务器 TPS 偏低：" + String.format("%.2f", tps) + "，内存 " + memory());
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        if (!check(sender, "canglan.module.monitor")) {
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("reload") || sub.equals("重载")) {
            if (!checkAdmin(sender)) {
                return true;
            }
            plugin.reloadEverything();
            Msg.send(sender, "&a监控配置已重载。");
            return true;
        }
        if (sub.equals("entity") || sub.equals("实体") || sub.equals("entities")) {
            entities(sender);
            return true;
        }
        overview(sender);
        return true;
    }

    private void overview(CommandSender sender) {
        double[] tps = Bukkit.getTPS();
        Msg.header(sender, "&b&l服务器监控");
        Msg.raw(sender, "&7TPS：&a" + fmt(tps[0]) + " &7/ &a" + fmt(tps[1]) + " &7/ &a" + fmt(tps[2])
                + " &8(1/5/15 分钟)");
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
        long max = runtime.maxMemory() / 1024L / 1024L;
        Msg.raw(sender, "&7内存：&f" + used + " MB / " + max + " MB &8(" + (used * 100L / Math.max(1, max)) + "%)");
        Msg.raw(sender, "&7在线玩家：&f" + Bukkit.getOnlinePlayers().size() + " &7/ &f" + Bukkit.getMaxPlayers()
                + " &8| &7模块：&a" + plugin.modules().enabledCount() + " &7/ &f" + plugin.modules().size());
        Msg.raw(sender, "&7线程数：&f" + Thread.activeCount() + " &8| &7运行时间：&f"
                + com.canglan.survival.core.Util.time(java.lang.management.ManagementFactory
                .getRuntimeMXBean().getUptime() / 1000L));
        Msg.raw(sender, "&8&m------------------------------------------------");
        Msg.raw(sender, "&7各世界情况：");
        for (World world : Bukkit.getWorlds()) {
            Msg.raw(sender, "&8- &f" + world.getName() + " &7玩家 &f" + world.getPlayers().size()
                    + " &7实体 &f" + world.getEntities().size() + " &7区块 &f" + world.getLoadedChunks().length);
        }
        Msg.raw(sender, "&7更多：&f/monitor 实体 &7查看实体类型统计 | &f/monitor 重载");
    }

    private void entities(CommandSender sender) {
        Msg.header(sender, "&b&l实体类型统计");
        for (World world : Bukkit.getWorlds()) {
            Map<String, Integer> counts = new HashMap<>();
            for (Entity entity : world.getEntities()) {
                counts.merge(entity.getType().name(), 1, Integer::sum);
            }
            Msg.raw(sender, "&7世界 &f" + world.getName() + " &7共 &f" + world.getEntities().size() + " &7个实体：");
            List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
            list.sort((a, b) -> b.getValue() - a.getValue());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(8, list.size()); i++) {
                if (i > 0) {
                    sb.append("&7, ");
                }
                sb.append("&f").append(list.get(i).getKey()).append("&7x").append(list.get(i).getValue());
            }
            if (sb.length() > 0) {
                Msg.raw(sender, "&8  " + sb);
            }
        }
    }

    private String fmt(double value) {
        return String.format("%.2f", Math.min(20.0D, value));
    }

    private String memory() {
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
        long max = runtime.maxMemory() / 1024L / 1024L;
        return used + " MB / " + max + " MB";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (args.length == 1) {
            return List.of("实体", "重载");
        }
        return List.of();
    }
}
