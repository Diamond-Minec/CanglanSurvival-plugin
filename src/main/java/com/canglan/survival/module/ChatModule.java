package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 模块 12 · 聊天管理
 * 聊天格式（带权限组前缀）、禁言、刷屏冷却、违禁词过滤、进服/退服提示。
 */
public class ChatModule extends Module {

    private final Map<UUID, Long> lastChat = new HashMap<>();

    private final Listener chatListener = new Listener() {
        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onChat(AsyncChatEvent event) {
            Player player = event.getPlayer();
            if (plugin.mutes().isMuted(player.getUniqueId())) {
                event.setCancelled(true);
                long remaining = plugin.mutes().remaining(player.getUniqueId());
                Msg.send(player, "&c你已被禁言" + (remaining < 0 ? "" : "，剩余 &f" + Util.time(remaining))
                        + "&c。原因：" + plugin.mutes().mute(player.getUniqueId()).reason);
                return;
            }
            String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
            for (String word : plugin.getConfig().getStringList("chat.banned-words")) {
                if (!word.isBlank() && plain.toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT))) {
                    event.setCancelled(true);
                    Msg.send(player, "&c你的消息包含违禁词，已被拦截。");
                    return;
                }
            }
            long cooldown = plugin.getConfig().getLong("chat.cooldown-seconds", 0L) * 1000L;
            if (cooldown > 0 && !player.hasPermission("canglan.chat.bypass")) {
                Long last = lastChat.get(player.getUniqueId());
                long now = System.currentTimeMillis();
                if (last != null && now - last < cooldown) {
                    event.setCancelled(true);
                    Msg.send(player, "&c发言太快了，请 &f" + ((cooldown - (now - last)) / 1000L + 1) + " &c秒后再试。");
                    return;
                }
                lastChat.put(player.getUniqueId(), now);
            }
            String format = plugin.getConfig().getString("chat.format", "&7[{group}] &f{player} &8» &f{message}");
            String prefix = plugin.perms().prefixOf(player.getUniqueId());
            String before = format.replace("{group}", plugin.perms().groupName(player.getUniqueId()))
                    .replace("{prefix}", prefix)
                    .replace("{player}", player.getName())
                    .replace("{world}", player.getWorld().getName());
            int index = before.indexOf("{message}");
            String head = index >= 0 ? before.substring(0, index) : before + " ";
            String tail = index >= 0 ? before.substring(index + "{message}".length()) : "";
            Component headComponent = Msg.component(head);
            Component tailComponent = Msg.component(tail);
            event.renderer((source, sourceDisplayName, message, viewer) ->
                    headComponent.append(message).append(tailComponent));
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            String format = plugin.getConfig().getString("chat.join-message", "&8[&a+&8] &f{player} &7加入了服务器");
            event.setJoinMessage(Msg.color(format.replace("{player}", player.getName())
                    .replace("{group}", plugin.perms().groupName(player.getUniqueId()))));
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onQuit(PlayerQuitEvent event) {
            Player player = event.getPlayer();
            String format = plugin.getConfig().getString("chat.quit-message", "&8[&c-&8] &f{player} &7离开了服务器");
            event.setQuitMessage(Msg.color(format.replace("{player}", player.getName())
                    .replace("{group}", plugin.perms().groupName(player.getUniqueId()))));
        }
    };

    public ChatModule(CanglanSurvival plugin) {
        super(plugin, "chat", "聊天管理",
                "聊天格式、禁言、刷屏冷却、违禁词过滤、进服与退服提示，全部可在 config.yml 调整。",
                true, "chat");
    }

    @Override
    protected void onEnable() {
        listen(chatListener);
    }

    @Override
    protected void onDisable() {
        lastChat.clear();
    }

    @Override
    public void reload() {
        lastChat.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        if (args.length == 0) {
            Msg.header(sender, "&b&l聊天管理");
            Msg.raw(sender, "&7当前禁言人数：&f" + 0);
            Msg.raw(sender, "&f/chat 禁言 <玩家> [时长] [原因] &8- &7禁言玩家（时长如 1h、7d，不填为永久）");
            Msg.raw(sender, "&f/chat 解禁 <玩家> &8- &7解除禁言");
            Msg.raw(sender, "&f/chat 重载 &8- &7重载聊天配置");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "mute", "禁言" -> {
                if (!check(sender, "canglan.chat.admin")) {
                    return true;
                }
                if (args.length < 2) {
                    Msg.send(sender, "&c用法：/chat 禁言 <玩家> [时长] [原因]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    Msg.send(sender, "&c玩家 &f" + args[1] + " &c不在线（禁言需要玩家在线）。");
                    return true;
                }
                long seconds = args.length > 2 ? Util.parseDuration(args[2], 0L) : 0L;
                String reason = args.length > 3 ? Util.join(args, 3) : "未填写原因";
                plugin.mutes().mute(target.getUniqueId(), seconds, reason, sender.getName());
                Msg.send(sender, "&a已禁言 &f" + target.getName() + "&a，时长：&f"
                        + (seconds <= 0 ? "永久" : Util.time(seconds)) + "&a，原因：&f" + reason);
                Msg.send(target, "&c你已被禁言" + (seconds <= 0 ? "（永久）" : "（" + Util.time(seconds) + "）")
                        + "&c，原因：&f" + reason);
            }
            case "unmute", "解禁", "解除禁言" -> {
                if (!check(sender, "canglan.chat.admin")) {
                    return true;
                }
                if (args.length < 2) {
                    Msg.send(sender, "&c用法：/chat 解禁 <玩家>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                UUID uuid;
                String name;
                if (target != null) {
                    uuid = target.getUniqueId();
                    name = target.getName();
                } else {
                    org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
                    if (!offline.hasPlayedBefore()) {
                        Msg.send(sender, "&c没有找到玩家：&f" + args[1]);
                        return true;
                    }
                    uuid = offline.getUniqueId();
                    name = offline.getName();
                }
                plugin.mutes().unmute(uuid);
                Msg.send(sender, "&a已解除 &f" + name + " &a的禁言。");
                if (target != null) {
                    Msg.send(target, "&a你已被解除禁言。");
                }
            }
            case "reload", "重载" -> {
                if (!checkAdmin(sender)) {
                    return true;
                }
                reload();
                Msg.send(sender, "&a聊天配置已重载。");
            }
            case "clear", "清屏" -> {
                if (!check(sender, "canglan.chat.admin")) {
                    return true;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    for (int i = 0; i < 60; i++) {
                        player.sendMessage(Component.empty());
                    }
                    Msg.raw(player, "&7聊天区已被管理员清理。");
                }
            }
            default -> Msg.send(sender, "&c用法：/chat 禁言|解禁|重载|清屏");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String option : List.of("禁言", "解禁", "重载", "清屏")) {
                if (option.startsWith(args[0])) {
                    out.add(option);
                }
            }
            return out;
        }
        if (args.length == 2) {
            List<String> out = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    out.add(player.getName());
                }
            }
            return out;
        }
        return List.of();
    }
}
