package com.canglan.survival.core;

import com.canglan.survival.CanglanSurvival;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能模块基类。
 * 每个模块拥有自己的中英文标识、中文名称、描述、默认开关和指令集合；
 * 支持运行时启停，关闭时会自动注销监听器、取消定时任务。
 */
public abstract class Module {

    protected final CanglanSurvival plugin;
    private final String id;
    private final String cnName;
    private final String description;
    private final boolean defaultEnabled;
    private final List<String> commands;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private boolean enabled;

    protected Module(CanglanSurvival plugin, String id, String cnName, String description,
                     boolean defaultEnabled, String... commands) {
        this.plugin = plugin;
        this.id = id;
        this.cnName = cnName;
        this.description = description;
        this.defaultEnabled = defaultEnabled;
        this.commands = List.of(commands);
    }

    public final String id() {
        return id;
    }

    public final String cnName() {
        return cnName;
    }

    public final String description() {
        return description;
    }

    public final boolean defaultEnabled() {
        return defaultEnabled;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final List<String> commands() {
        return commands;
    }

    /** 由模块管理器调用。 */
    public final void enable() {
        if (enabled) {
            return;
        }
        enabled = true;
        try {
            onEnable();
        } catch (Throwable t) {
            plugin.getLogger().severe("启用模块「" + cnName + "」时出错：" + t);
            t.printStackTrace();
        }
    }

    /** 由模块管理器调用。 */
    public final void disable() {
        if (!enabled) {
            return;
        }
        enabled = false;
        try {
            onDisable();
        } catch (Throwable t) {
            plugin.getLogger().warning("关闭模块「" + cnName + "」时出错：" + t.getMessage());
        }
        for (Listener listener : listeners) {
            HandlerList.unregisterAll(listener);
        }
        listeners.clear();
        for (BukkitTask task : tasks) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
        tasks.clear();
    }

    /** 模块启用时调用。 */
    protected abstract void onEnable();

    /** 模块关闭时调用。 */
    protected abstract void onDisable();

    /** 处理该模块的指令。指令名会从 commands() 里匹配。 */
    public abstract boolean onCommand(CommandSender sender, String command, String[] args);

    /** 指令补全。 */
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        return List.of();
    }

    /** /cl reload 时调用。 */
    public void reload() {
    }

    protected void listen(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    protected void later(long delayTicks, Runnable runnable) {
        tasks.add(Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks));
    }

    protected void every(long periodTicks, Runnable runnable) {
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, runnable, periodTicks, periodTicks));
    }

    protected void every(long delayTicks, long periodTicks, Runnable runnable) {
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks));
    }

    /** 权限检查，失败时发送中文提示。服主用 /cl 玩家 <玩家> 允许 <模块> 单独授权时同样生效。 */
    protected boolean check(CommandSender sender, String node) {
        if (sender.hasPermission(node)) {
            return true;
        }
        if (sender instanceof Player player
                && Boolean.TRUE.equals(plugin.modules().override(player.getUniqueId(), id()))) {
            return true;
        }
        Msg.send(sender, "&c你没有权限这样做：" + node);
        return false;
    }

    protected boolean checkAdmin(CommandSender sender) {
        return check(sender, "canglan.admin");
    }

    /** 仅玩家可执行，控制台会收到中文提示。 */
    protected Player asPlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Msg.send(sender, "&c这条指令只能由玩家在游戏内执行。");
        return null;
    }

    /** 数字解析失败提示。 */
    protected Integer parseInt(CommandSender sender, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            Msg.send(sender, "&c「" + raw + "」不是有效的数字。");
            return null;
        }
    }

    protected Double parseDouble(CommandSender sender, String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            Msg.send(sender, "&c「" + raw + "」不是有效的数字。");
            return null;
        }
    }

    public String usageHint() {
        if (commands.isEmpty()) {
            return "无";
        }
        return "/" + String.join("、/", commands);
    }
}
