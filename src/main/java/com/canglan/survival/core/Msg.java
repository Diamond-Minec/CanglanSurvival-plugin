package com.canglan.survival.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * 统一的中文消息与显示工具。
 * 所有对外文本都从这里走，方便统一前缀与配色。
 */
public final class Msg {

    private static String prefix = "&b&l沧蓝工坊 &7» &f";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private Msg() {
    }

    public static void prefix(String value) {
        prefix = value == null ? "" : value;
    }

    public static String prefix() {
        return color(prefix);
    }

    /** 把 & 颜色代码翻译成 §。 */
    public static String color(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static String strip(String text) {
        return ChatColor.stripColor(color(text));
    }

    public static Component component(String text) {
        return LEGACY.deserialize(color(text));
    }

    /** 带前缀发送。 */
    public static void send(CommandSender sender, String message) {
        sender.sendMessage(color(prefix + message));
    }

    /** 不带前缀发送（列表、表格用）。 */
    public static void raw(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    public static void raw(CommandSender sender, List<String> lines) {
        for (String line : lines) {
            raw(sender, line);
        }
    }

    public static void broadcast(String message) {
        Bukkit.getServer().sendMessage(component(prefix + message));
    }

    public static void broadcastRaw(String message) {
        Bukkit.getServer().sendMessage(component(message));
    }

    public static void actionBar(Player player, String message) {
        player.sendActionBar(component(message));
    }

    public static void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.showTitle(Title.title(
                component(title),
                component(subtitle),
                Title.Times.times(
                        Duration.ofMillis(fadeIn * 50L),
                        Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L))));
    }

    public static void title(Player player, String title, String subtitle) {
        title(player, title, subtitle, 10, 50, 10);
    }

    /** 分割线。 */
    public static String line() {
        return color("&8&m------------------------------------------------");
    }

    public static String line(String colorCode) {
        return color(colorCode + "&m------------------------------------------------");
    }

    public static void header(CommandSender sender, String title) {
        raw(sender, "&8&m----------------&r " + title + " &8&m----------------");
    }

    public static void multi(CommandSender sender, String text) {
        for (String line : text.split("\n")) {
            raw(sender, line);
        }
    }

    public static void sendAll(Collection<? extends CommandSender> senders, String message) {
        for (CommandSender sender : senders) {
            send(sender, message);
        }
    }
}
