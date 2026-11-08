package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 模块 11 · 传送请求
 * /tpa 请求传送到对方、/tpahere 请求对方传送到你、接受与拒绝。
 */
public class TpaModule extends Module {

    private static final class Request {
        private final UUID sender;
        private final boolean here;
        private final long time;

        private Request(UUID sender, boolean here) {
            this.sender = sender;
            this.here = here;
            this.time = System.currentTimeMillis();
        }
    }

    private final Map<UUID, Map<UUID, Request>> requests = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public TpaModule(CanglanSurvival plugin) {
        super(plugin, "tpa", "传送请求",
                "/tpa 请求传送到好友身边，/tpahere 邀请好友过来，对方可以接受或拒绝，请求 60 秒后自动失效。",
                true, "tpa", "tpahere", "tpaccept", "tpdeny");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
        requests.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        switch (command.toLowerCase(Locale.ROOT)) {
            case "tpahere" -> request(player, args, true);
            case "tpaccept", "接受" -> accept(player, args);
            case "tpdeny", "拒绝" -> deny(player, args);
            default -> request(player, args, false);
        }
        return true;
    }

    private void request(Player player, String[] args, boolean here) {
        if (args.length < 1) {
            Msg.send(player, "&c用法：/" + (here ? "tpahere" : "tpa") + " <玩家>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Msg.send(player, "&c玩家 &f" + args[0] + " &c不在线。");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            Msg.send(player, "&c不能给自己发送传送请求。");
            return;
        }
        long cooldown = plugin.getConfig().getLong("tpa.cooldown", 15L);
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldown * 1000L) {
            long remain = (cooldown * 1000L - (now - last)) / 1000L + 1;
            Msg.send(player, "&c传送请求冷却中，请 &f" + remain + " &c秒后再试。");
            return;
        }
        cooldowns.put(player.getUniqueId(), now);
        requests.computeIfAbsent(target.getUniqueId(), k -> new HashMap<>())
                .put(player.getUniqueId(), new Request(player.getUniqueId(), here));
        Msg.send(player, "&a已向 &f" + target.getName() + " &a发送传送请求，等待对方回应……");
        Msg.send(target, "&e玩家 &f" + player.getName() + " &e请求"
                + (here ? " &e你传送到他身边" : " &e传送到你身边") + "&e。");
        Msg.send(target, "&a输入 &f/tpaccept " + player.getName() + " &a接受，或 &f/tpdeny " + player.getName() + " &a拒绝。");
        // 60 秒后自动清理
        long expire = plugin.getConfig().getLong("tpa.expire-seconds", 60L) * 20L;
        later(expire, () -> {
            Map<UUID, Request> map = requests.get(target.getUniqueId());
            if (map == null) {
                return;
            }
            Request request = map.get(player.getUniqueId());
            if (request == null || System.currentTimeMillis() - request.time < plugin.getConfig()
                    .getLong("tpa.expire-seconds", 60L) * 1000L) {
                return;
            }
            map.remove(player.getUniqueId());
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online != null) {
                Msg.send(online, "&7你发给 &f" + target.getName() + " &7的传送请求已过期。");
            }
        });
    }

    private void accept(Player player, String[] args) {
        Request request = take(player, args);
        if (request == null) {
            return;
        }
        Player sender = Bukkit.getPlayer(request.sender);
        if (sender == null) {
            Msg.send(player, "&c对方已经下线了。");
            return;
        }
        if (request.here) {
            Util2.teleport(sender, player.getLocation());
            Msg.send(sender, "&a" + player.getName() + " 接受了你的请求，你已被传送到 TA 身边。");
            Msg.send(player, "&a已接受 &f" + sender.getName() + " &a的请求。");
        } else {
            Util2.teleport(player, sender.getLocation());
            Msg.send(sender, "&a" + player.getName() + " 接受了你的传送请求。");
            Msg.send(player, "&a已传送到 &f" + sender.getName() + " &a身边。");
        }
    }

    private void deny(Player player, String[] args) {
        Request request = take(player, args);
        if (request == null) {
            return;
        }
        Player sender = Bukkit.getPlayer(request.sender);
        if (sender != null) {
            Msg.send(sender, "&c" + player.getName() + " 拒绝了你的传送请求。");
        }
        Msg.send(player, "&7已拒绝该传送请求。");
    }

    private Request take(Player player, String[] args) {
        Map<UUID, Request> map = requests.get(player.getUniqueId());
        if (map == null || map.isEmpty()) {
            Msg.send(player, "&c你没有任何待处理的传送请求。");
            return null;
        }
        UUID senderId;
        if (args.length >= 1) {
            Player sender = Bukkit.getPlayerExact(args[0]);
            if (sender == null || !map.containsKey(sender.getUniqueId())) {
                Msg.send(player, "&c没有来自 &f" + args[0] + " &c的传送请求。");
                return null;
            }
            senderId = sender.getUniqueId();
        } else {
            senderId = map.keySet().iterator().next();
        }
        Request request = map.remove(senderId);
        if (System.currentTimeMillis() - request.time > plugin.getConfig().getLong("tpa.expire-seconds", 60L) * 1000L) {
            Msg.send(player, "&c这个传送请求已经过期了。");
            return null;
        }
        return request;
    }

    /** 小工具：安全传送。 */
    private static final class Util2 {
        private static void teleport(Player player, org.bukkit.Location location) {
            com.canglan.survival.core.Util.safeTeleport(player, location.clone());
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
