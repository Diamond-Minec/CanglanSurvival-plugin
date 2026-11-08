package com.canglan.survival.core;

import com.canglan.survival.CanglanSurvival;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 核心监听：登录时挂载权限、统计在线时长与行为数据。
 */
public class CoreListener implements Listener {

    private final CanglanSurvival plugin;

    public CoreListener(CanglanSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.perms().apply(player);
        plugin.stats().join(player.getUniqueId());
        if (plugin.getConfig().getBoolean("login-message.enabled", false)) {
            Msg.send(player, plugin.getConfig().getString("login-message.text", "&7欢迎回到服务器。"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.stats().quit(player.getUniqueId());
        plugin.perms().detach(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("stats.count-blocks", true)) {
            return;
        }
        plugin.stats().add(event.getPlayer().getUniqueId(), Stats.BROKEN, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("stats.count-blocks", true)) {
            return;
        }
        plugin.stats().add(event.getPlayer().getUniqueId(), Stats.PLACED, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.stats().add(player.getUniqueId(), Stats.DEATHS, 1L);
        Player killer = player.getKiller();
        if (killer != null && !killer.getUniqueId().equals(player.getUniqueId())) {
            plugin.stats().add(killer.getUniqueId(), Stats.KILLS, 1L);
        }
    }
}
