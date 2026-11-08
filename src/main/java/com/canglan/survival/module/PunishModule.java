package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import com.canglan.survival.core.YamlStore;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 模块 14 · 处罚管理
 * 警告、查看警告、踢出、封禁、解封、禁言与解禁。
 */
public class PunishModule extends Module {

    private final YamlStore store;

    public PunishModule(CanglanSurvival plugin) {
        super(plugin, "punish", "处罚管理",
                "管理员工具：/warn 警告、/kick 踢出、/ban 封禁、/unban 解封、/mute 禁言，全部有记录可查。",
                true, "warn", "warnings", "kick", "ban", "unban", "mute", "unmute");
        this.store = new YamlStore(plugin, "punish.yml");
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
        switch (command.toLowerCase(Locale.ROOT)) {
            case "warn", "警告" -> warn(sender, args);
            case "warnings", "警告记录" -> warnings(sender, args);
            case "kick", "踢出" -> kick(sender, args);
            case "ban", "封禁" -> ban(sender, args);
            case "unban", "解封" -> unban(sender, args);
            case "mute", "禁言" -> mute(sender, args);
            case "unmute", "解禁" -> unmute(sender, args);
            default -> Msg.send(sender, "&c未知处罚指令。");
        }
        return true;
    }

    private void warn(CommandSender sender, String[] args) {
        if (!check(sender, "canglan.punish.admin")) {
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, "&c用法：/warn <玩家> <原因>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        OfflinePlayer offline = target;
        if (offline == null) {
            offline = Bukkit.getOfflinePlayer(args[0]);
            if (!offline.hasPlayedBefore()) {
                Msg.send(sender, "&c没有找到玩家：&f" + args[0]);
                return;
            }
        }
        String reason = Util.join(args, 1);
        String id = String.valueOf(System.currentTimeMillis());
        store.yaml().set("warnings." + offline.getUniqueId() + "." + id + ".reason", reason);
        store.yaml().set("warnings." + offline.getUniqueId() + "." + id + ".by", sender.getName());
        store.yaml().set("warnings." + offline.getUniqueId() + "." + id + ".time", System.currentTimeMillis());
        store.save();
        int count = warningsOf(offline.getUniqueId()).size();
        Msg.send(sender, "&a已警告 &f" + offline.getName() + "&a，原因：&f" + reason + "&a（累计 " + count + " 次警告）");
        if (target != null) {
            Msg.send(target, "&c你收到一次警告：&f" + reason);
            Msg.title(target, "&c&l警告", "&f" + reason, 10, 50, 10);
        }
        if (count >= plugin.getConfig().getInt("punish.auto-kick-warnings", 3)) {
            if (target != null) {
                target.kickPlayer(Msg.color("&c你因多次违规被踢出服务器。\n&7原因：&f" + reason));
            }
        }
    }

    private List<String> warningsOf(UUID uuid) {
        List<String> list = new ArrayList<>();
        ConfigurationSection section = store.yaml().getConfigurationSection("warnings." + uuid);
        if (section != null) {
            list.addAll(section.getKeys(false));
        }
        return list;
    }

    private void warnings(CommandSender sender, String[] args) {
        if (!check(sender, "canglan.punish.admin")) {
            return;
        }
        if (args.length < 1) {
            Msg.send(sender, "&c用法：/warnings <玩家>");
            return;
        }
        OfflinePlayer offline = Bukkit.getPlayerExact(args[0]);
        if (offline == null) {
            offline = Bukkit.getOfflinePlayer(args[0]);
        }
        List<String> ids = warningsOf(offline.getUniqueId());
        Msg.header(sender, "&b" + offline.getName() + " &7的警告记录（共 " + ids.size() + " 条）");
        if (ids.isEmpty()) {
            Msg.raw(sender, "&7该玩家没有警告记录。");
            return;
        }
        for (String id : ids) {
            String reason = store.yaml().getString("warnings." + offline.getUniqueId() + "." + id + ".reason", "");
            String by = store.yaml().getString("warnings." + offline.getUniqueId() + "." + id + ".by", "?");
            long time = store.yaml().getLong("warnings." + offline.getUniqueId() + "." + id + ".time", 0L);
            Msg.raw(sender, "&8- &7" + new java.text.SimpleDateFormat("MM-dd HH:mm").format(new Date(time))
                    + " &f" + by + " &8| &7" + reason);
        }
    }

    private void kick(CommandSender sender, String[] args) {
        if (!check(sender, "canglan.punish.admin")) {
            return;
        }
        if (args.length < 1) {
            Msg.send(sender, "&c用法：/kick <玩家> [原因]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Msg.send(sender, "&c玩家 &f" + args[0] + " &c不在线。");
            return;
        }
        String reason = args.length > 1 ? Util.join(args, 1) : "未填写原因";
        target.kickPlayer(Msg.color("&c你被管理员踢出服务器。\n&7原因：&f" + reason));
        Msg.send(sender, "&a已踢出 &f" + target.getName() + "&a，原因：&f" + reason);
    }

    @SuppressWarnings("deprecation")
    private void ban(CommandSender sender, String[] args) {
        if (!check(sender, "canglan.punish.admin")) {
            return;
        }
        if (args.length < 1) {
            Msg.send(sender, "&c用法：/ban <玩家> [时长，如 7d，不填为永久] [原因]");
            return;
        }
        Player online = Bukkit.getPlayerExact(args[0]);
        String name = online != null ? online.getName() : args[0];
        String reason = "未填写原因";
        long seconds = 0L;
        int reasonStart = 1;
        if (args.length > 1) {
            long parsed = Util.parseDuration(args[1], -1L);
            if (parsed > 0) {
                seconds = parsed;
                reasonStart = 2;
            }
        }
        if (args.length > reasonStart) {
            reason = Util.join(args, reasonStart);
        }
        Date expires = seconds > 0 ? new Date(System.currentTimeMillis() + seconds * 1000L) : null;
        Bukkit.getBanList(BanList.Type.NAME).addBan(name, Msg.color("&c" + reason), expires, sender.getName());
        if (online != null) {
            online.kickPlayer(Msg.color("&c你已被封禁。\n&7原因：&f" + reason
                    + (seconds > 0 ? "\n&7剩余时间：&f" + Util.time(seconds) : "\n&7封禁时长：&f永久")));
        }
        Msg.send(sender, "&a已封禁 &f" + name + "&a，原因：&f" + reason
                + (seconds > 0 ? " &7（" + Util.time(seconds) + "）" : " &7（永久）"));
    }

    @SuppressWarnings("deprecation")
    private void unban(CommandSender sender, String[] args) {
        if (!check(sender, "canglan.punish.admin")) {
            return;
        }
        if (args.length < 1) {
            Msg.send(sender, "&c用法：/unban <玩家>");
            return;
        }
        Bukkit.getBanList(BanList.Type.NAME).pardon(args[0]);
        Msg.send(sender, "&a已解除 &f" + args[0] + " &a的封禁。");
    }

    private void mute(CommandSender sender, String[] args) {
        if (!check(sender, "canglan.punish.admin")) {
            return;
        }
        if (args.length < 1) {
            Msg.send(sender, "&c用法：/mute <玩家> [时长] [原因]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Msg.send(sender, "&c玩家 &f" + args[0] + " &c不在线。");
            return;
        }
        long seconds = args.length > 1 ? Util.parseDuration(args[1], 0L) : 0L;
        String reason = args.length > 2 ? Util.join(args, 2) : "未填写原因";
        plugin.mutes().mute(target.getUniqueId(), seconds, reason, sender.getName());
        Msg.send(sender, "&a已禁言 &f" + target.getName() + "&a，时长：&f"
                + (seconds <= 0 ? "永久" : Util.time(seconds)) + "&a，原因：&f" + reason);
        Msg.send(target, "&c你已被禁言，原因：&f" + reason);
    }

    private void unmute(CommandSender sender, String[] args) {
        if (!check(sender, "canglan.punish.admin")) {
            return;
        }
        if (args.length < 1) {
            Msg.send(sender, "&c用法：/unmute <玩家>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        UUID uuid;
        String name;
        if (target != null) {
            uuid = target.getUniqueId();
            name = target.getName();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
            if (!offline.hasPlayedBefore()) {
                Msg.send(sender, "&c没有找到玩家：&f" + args[0]);
                return;
            }
            uuid = offline.getUniqueId();
            name = offline.getName();
        }
        if (!plugin.mutes().isMuted(uuid)) {
            Msg.send(sender, "&7该玩家当前没有被禁言。");
            return;
        }
        plugin.mutes().unmute(uuid);
        Msg.send(sender, "&a已解除 &f" + name + " &a的禁言。");
        if (target != null) {
            Msg.send(target, "&a你已被解除禁言。");
        }
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
