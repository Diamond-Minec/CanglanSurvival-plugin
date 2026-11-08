package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 模块 09 · 随机传送
 * /rtp 把玩家随机传送到安全位置，带冷却与防卡墙处理。
 */
public class RtpModule extends Module {

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public RtpModule(CanglanSurvival plugin) {
        super(plugin, "rtp", "随机传送",
                "打野神器：/rtp 随机传送到一个安全位置，自动避开岩浆、水和悬空，带冷却限制。",
                true, "rtp");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("reload") || args[0].equals("重载"))) {
            if (!checkAdmin(sender)) {
                return true;
            }
            plugin.reloadEverything();
            Msg.send(sender, "&a随机传送配置已重载。");
            return true;
        }
        long cooldown = plugin.getConfig().getLong("rtp.cooldown", 30L);
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldown * 1000L && !player.hasPermission("canglan.rtp.bypass")) {
            long remain = (cooldown * 1000L - (now - last)) / 1000L + 1;
            Msg.send(sender, "&c随机传送冷却中，请 &f" + remain + " &c秒后再试。");
            return true;
        }
        World world = pickWorld(player);
        if (world == null) {
            Msg.send(sender, "&c这个世界不支持随机传送，请到主世界使用。");
            return true;
        }
        int min = Math.max(50, plugin.getConfig().getInt("rtp.min-radius", 200));
        int max = Math.max(min + 50, plugin.getConfig().getInt("rtp.max-radius", 3000));
        Location spot = Util.randomSafe(world, world.getSpawnLocation().getBlockX(),
                world.getSpawnLocation().getBlockZ(), min, max);
        if (spot == null) {
            Msg.send(sender, "&c没有找到合适的安全位置，请稍后再试。");
            return true;
        }
        cooldowns.put(player.getUniqueId(), now);
        Msg.send(sender, "&a正在随机传送，请稍候……");
        player.sendActionBar(Msg.color("&b正在寻找安全落点……"));
        if (!Util.safeTeleport(player, spot)) {
            Msg.send(sender, "&c传送失败，请稍后再试。");
            return true;
        }
        Msg.send(sender, "&a已随机传送到 &f" + Util.loc(spot) + "&a，祝你玩得愉快！");
        Msg.title(player, "&b&l随机传送", "&f" + Util.loc(spot), 5, 40, 10);
        return true;
    }

    private World pickWorld(Player player) {
        List<String> worlds = plugin.getConfig().getStringList("rtp.worlds");
        World world = player.getWorld();
        if (worlds.isEmpty()) {
            return world.getEnvironment() == World.Environment.NORMAL ? world : Bukkit.getWorlds().get(0);
        }
        if (worlds.contains(world.getName())) {
            return world;
        }
        World fallback = Bukkit.getWorld(worlds.get(0));
        return fallback != null ? fallback : Bukkit.getWorlds().get(0);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        return args.length == 1 ? List.of("重载") : List.of();
    }
}
