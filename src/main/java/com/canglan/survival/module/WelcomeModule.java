package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 模块 16 · 欢迎引导
 * 新玩家欢迎词、全服公告、新手礼包、服务器规则与玩法引导。
 */
public class WelcomeModule extends Module {

    private final Listener joinListener = new Listener() {
        @EventHandler(priority = EventPriority.MONITOR)
        public void onJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            long joins = plugin.stats().get(player.getUniqueId(), "joins");
            if (joins <= 1) {
                firstJoin(player);
            } else {
                returnJoin(player);
            }
        }
    };

    public WelcomeModule(CanglanSurvival plugin) {
        super(plugin, "welcome", "欢迎引导",
                "新玩家欢迎词、全服公告、新手礼包、服务器规则与玩法引导，全部可在 config.yml 中自定义。",
                true, "welcome");
    }

    @Override
    protected void onEnable() {
        listen(joinListener);
    }

    @Override
    protected void onDisable() {
    }

    @Override
    public void reload() {
    }

    private void firstJoin(Player player) {
        for (String line : plugin.getConfig().getStringList("welcome.first-join-broadcast")) {
            Msg.broadcastRaw(line.replace("{player}", player.getName()));
        }
        Msg.title(player, plugin.serverName(), "&f欢迎来到本服", 10, 60, 10);
        for (String line : plugin.getConfig().getStringList("welcome.new-player-tips")) {
            Msg.send(player, line.replace("{player}", player.getName()));
        }
        giveKit(player);
    }

    private void returnJoin(Player player) {
        Msg.title(player, "&b&l欢迎回来", "&f" + player.getName(), 10, 40, 10);
        Msg.send(player, "&7欢迎回来！你已击杀 &f" + plugin.stats().get(player.getUniqueId(), "kills")
                + " &7次，拥有金币 &6" + Util.money(plugin.economy().balance(player.getUniqueId())) + " &7。");
        Msg.send(player, "&7输入 &f/welcome &7可以查看服务器规则与玩法介绍。");
    }

    private void giveKit(Player player) {
        if (!plugin.getConfig().getBoolean("welcome.kit-enabled", true)) {
            return;
        }
        for (String line : plugin.getConfig().getStringList("welcome.kit")) {
            String[] parts = line.split(":");
            Material material = Material.matchMaterial(parts[0].trim().toUpperCase());
            if (material == null) {
                continue;
            }
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(parts[1].trim())));
                } catch (NumberFormatException ignored) {
                }
            }
            player.getInventory().addItem(new ItemStack(material, amount));
        }
        Msg.send(player, "&a新手礼包已经放进你的背包了，输入 &f/welcome &a查看玩法引导。");
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        Msg.header(player, "&b&l服务器规则与玩法引导");
        for (String line : plugin.getConfig().getStringList("welcome.rules")) {
            Msg.raw(player, line);
        }
        Msg.raw(player, "&8&m------------------------------------------------");
        Msg.raw(player, "&7常用指令：&f/cl 我的 &7查看可用功能，&f/welcome &7本页，&f/shop &7商店，&f/signin &7签到");
        Msg.raw(player, "&7需要帮助？在聊天里 @管理员，或用 &f/report &7举报违规玩家。");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        return List.of();
    }
}
