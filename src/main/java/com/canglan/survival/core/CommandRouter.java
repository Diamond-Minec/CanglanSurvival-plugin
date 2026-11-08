package com.canglan.survival.core;

import com.canglan.survival.CanglanSurvival;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 主指令 /cl：模块列表、启用、禁用、重载、玩家单独授权。
 */
public class CommandRouter implements CommandExecutor, TabCompleter {

    private final CanglanSurvival plugin;

    public CommandRouter(CanglanSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("canglan") || name.equals("cl") || name.equals("cyan")) {
            return root(sender, args);
        }
        if (name.equals("servername") || name.equals("sn")) {
            return serverName(sender, args);
        }
        Module module = plugin.modules().byCommand(name);
        if (module == null) {
            Msg.send(sender, "&c未知指令：/" + name);
            return true;
        }
        if (!module.isEnabled()) {
            Msg.send(sender, "&c「" + module.cnName() + "」功能已被服主全局禁用，暂时无法使用。");
            return true;
        }
        if (sender instanceof Player player && !plugin.modules().canUse(player, module.id())) {
            Boolean override = plugin.modules().override(player.getUniqueId(), module.id());
            if (Boolean.FALSE.equals(override)) {
                Msg.send(sender, "&c服主已经禁止你使用「" + module.cnName() + "」功能。");
            } else {
                Msg.send(sender, "&c你没有使用「" + module.cnName() + "」功能的权限。");
            }
            return true;
        }
        try {
            return module.onCommand(sender, name, args);
        } catch (Throwable t) {
            Msg.send(sender, "&c指令执行出错，请联系服主查看后台日志。");
            plugin.getLogger().warning("模块「" + module.cnName() + "」执行 /" + name + " 出错：" + t);
            t.printStackTrace();
            return true;
        }
    }

    private boolean root(CommandSender sender, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = normalize(args[0]);
        switch (sub) {
            case "help" -> help(sender);
            case "list" -> list(sender, args);
            case "info" -> info(sender, args);
            case "enable" -> toggle(sender, args, true);
            case "disable" -> toggle(sender, args, false);
            case "reload" -> reload(sender, args);
            case "player" -> playerCmd(sender, args);
            case "my" -> my(sender);
            case "status" -> status(sender);
            case "perm" -> Msg.send(sender, "&7权限组相关指令请使用 &f/perm 帮助 &7查看（属于「权限系统」模块）。");
            default -> {
                Module module = plugin.modules().get(args[0]);
                if (module != null) {
                    info(sender, args);
                } else {
                    Msg.send(sender, "&c未知子指令：&f" + args[0] + "&c，输入 &f/cl 帮助 &c查看用法。");
                }
            }
        }
        return true;
    }

    private void help(CommandSender sender) {
        Msg.header(sender, "&b&l沧蓝工坊 &7整合生存包");
        Msg.raw(sender, "&7版本 &f" + CanglanSurvival.VERSION + " &8| &7作者 &f" + CanglanSurvival.AUTHOR
                + " &8| &7模块 &f" + plugin.modules().size() + " 个");
        Msg.raw(sender, "&8&m------------------------------------------------");
        Msg.raw(sender, "&b/cl 列表 &8- &f查看全部模块与开关状态");
        Msg.raw(sender, "&b/cl 信息 <模块> &8- &f查看某个模块的详细介绍");
        Msg.raw(sender, "&b/cl 启用 <模块> &8- &f启用一个模块 &8(管理员)");
        Msg.raw(sender, "&b/cl 禁用 <模块> &8- &f禁用一个模块 &8(管理员)");
        Msg.raw(sender, "&b/cl 重载 [模块|all] &8- &f重载配置 &8(管理员)");
        Msg.raw(sender, "&b/cl 玩家 <玩家> 列表 &8- &f查看该玩家被单独设置的模块");
        Msg.raw(sender, "&b/cl 玩家 <玩家> 允许 <模块> &8- &f单独允许该玩家使用");
        Msg.raw(sender, "&b/cl 玩家 <玩家> 禁止 <模块> &8- &f单独禁止该玩家使用");
        Msg.raw(sender, "&b/cl 玩家 <玩家> 重置 <模块> &8- &f恢复默认权限");
        Msg.raw(sender, "&b/cl 我的 &8- &f查看你自己能用的模块");
        Msg.raw(sender, "&b/cl 状态 &8- &f服务器与模块运行状态");
        Msg.raw(sender, "&b/servername \"名字\" &8- &f修改服务器名称 &8(管理员)");
        Msg.raw(sender, "&8&m------------------------------------------------");
        Msg.raw(sender, "&7也可以直接用中文：&f/cl 列表 &7、&f/cl 启用 tab &7、&f/cl 玩家 小王 禁止 商店");
        Msg.raw(sender, "&7每个模块自己的指令请用 &f/cl 信息 <模块> &7查看。");
    }

    /** /servername "名字" —— 修改服务器名称。 */
    private boolean serverName(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Msg.header(sender, "&b&l服务器名称");
            Msg.raw(sender, "&7当前名称：&r" + plugin.serverName());
            Msg.raw(sender, "&7修改：&f/servername \"新名字\"");
            Msg.raw(sender, "&7　支持 & 颜色代码，名字可带空格，最多 32 个字");
            Msg.raw(sender, "&7恢复默认：&f/servername reset");
            Msg.raw(sender, "&8提示：这里改的是插件里显示的服务器名（TAB、计分板、菜单标题）。");
            Msg.raw(sender, "&8服务器列表的 MOTD 需要改 server.properties 里的 motd 后重启。");
            return true;
        }
        if (!sender.hasPermission("canglan.admin")) {
            Msg.send(sender, "&c只有服主可以修改服务器名称（需要 canglan.admin 权限）。");
            return true;
        }
        String raw = Util.join(args, 0).trim();
        if (raw.equalsIgnoreCase("reset") || raw.equals("重置") || raw.equals("默认")) {
            plugin.setServerName(CanglanSurvival.DEFAULT_SERVER_NAME);
            refreshNameModules();
            Msg.send(sender, "&a服务器名称已恢复默认：&r" + plugin.serverName());
            Msg.broadcast("&7服务器名称已恢复为：&r" + plugin.serverName());
            return true;
        }
        // 去掉英文双引号或中文引号
        if (raw.length() >= 2) {
            boolean quoted = (raw.startsWith("\"") && raw.endsWith("\""))
                    || (raw.startsWith("'") && raw.endsWith("'"))
                    || (raw.startsWith("“") && raw.endsWith("”"));
            if (quoted) {
                raw = raw.substring(1, raw.length() - 1).trim();
            }
        }
        if (raw.isEmpty()) {
            Msg.send(sender, "&c名字不能为空。用法：&f/servername \"我的服务器\"");
            return true;
        }
        String plain = Msg.strip(Msg.color(raw));
        if (plain.length() > 32) {
            Msg.send(sender, "&c名字太长了（" + plain.length() + " 个字），最多 32 个字。");
            return true;
        }
        plugin.setServerName(raw);
        refreshNameModules();
        Msg.send(sender, "&a服务器名称已改为：&r" + plugin.serverName());
        Msg.broadcast("&7服务器名称已更新为：&r" + plugin.serverName());
        return true;
    }

    /** 改名后立刻刷新 TAB 与计分板。 */
    private void refreshNameModules() {
        for (String id : new String[]{"tab", "scoreboard"}) {
            Module module = plugin.modules().get(id);
            if (module != null && module.isEnabled()) {
                try {
                    module.reload();
                } catch (Throwable t) {
                    plugin.getLogger().warning("刷新 " + id + " 失败：" + t.getMessage());
                }
            }
        }
    }

    private void list(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length > 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
            }
        }
        List<Module> modules = new ArrayList<>(plugin.modules().all());
        int perPage = 10;
        int totalPages = Math.max(1, (modules.size() + perPage - 1) / perPage);
        page = Math.min(page, totalPages);
        Msg.header(sender, "&b&l模块列表 &7(" + page + "/" + totalPages + ")");
        int from = (page - 1) * perPage;
        for (int i = from; i < Math.min(modules.size(), from + perPage); i++) {
            Module module = modules.get(i);
            boolean on = module.isEnabled();
            String status = on ? "&a启用" : "&c禁用";
            String usable;
            if (sender instanceof Player player) {
                usable = plugin.modules().playerOn(player, module.id())
                        ? (plugin.modules().canUse(player, module.id()) ? "&a可用" : "&e无权限")
                        : "&c不可用";
            } else {
                usable = "&7-";
            }
            Msg.raw(sender, "&8" + (i + 1) + ". &f" + module.cnName() + " &8(" + module.id() + ") "
                    + status + " &8| &7你：" + usable);
        }
        Msg.raw(sender, "&8&m------------------------------------------------");
        if (page < totalPages) {
            Msg.raw(sender, "&7下一页：&f/cl 列表 " + (page + 1));
        }
        Msg.raw(sender, "&7提示：&f/cl 信息 <模块> &7查看详情与指令；&f/cl 启用/禁用 <模块> &7开关。");
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "&c用法：/cl 信息 <模块ID或中文名>");
            return;
        }
        Module module = plugin.modules().get(args[1]);
        if (module == null) {
            Msg.send(sender, "&c没有找到模块：&f" + args[1]);
            return;
        }
        Msg.header(sender, "&b&l" + module.cnName());
        Msg.raw(sender, "&7模块 ID：&f" + module.id());
        Msg.raw(sender, "&7介绍：&f" + module.description());
        Msg.raw(sender, "&7全局状态：" + (module.isEnabled() ? "&a已启用" : "&c已禁用")
                + " &8| &7默认：" + (module.defaultEnabled() ? "&a启用" : "&c禁用"));
        Msg.raw(sender, "&7模块指令：&f" + module.usageHint());
        Msg.raw(sender, "&7权限节点：&f" + "canglan.module." + module.id() + " &8(单独授权用 &f/cl 玩家 <玩家> 允许 " + module.id() + "&8)");
        if (sender instanceof Player player) {
            Msg.raw(sender, "&7你的状态：" + (plugin.modules().canUse(player, module.id()) ? "&a可用" : "&c不可用"));
        }
        Msg.raw(sender, "&7控制台指令：&f/cl 启用 " + module.id() + " &7或 &f/cl 禁用 " + module.id());
    }

    private void toggle(CommandSender sender, String[] args, boolean enable) {
        if (!sender.hasPermission("canglan.admin")) {
            Msg.send(sender, "&c只有服主（拥有 canglan.admin 权限）才能启用或禁用模块。");
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, "&c用法：/cl " + (enable ? "启用" : "禁用") + " <模块>");
            return;
        }
        Module module = plugin.modules().get(args[1]);
        if (module == null) {
            Msg.send(sender, "&c没有找到模块：&f" + args[1]);
            return;
        }
        if (module.isEnabled() == enable) {
            Msg.send(sender, "&e「" + module.cnName() + "」已经处于" + (enable ? "启用" : "禁用") + "状态了。");
            return;
        }
        plugin.modules().setGlobal(module.id(), enable);
        Msg.send(sender, "&a已" + (enable ? "启用" : "禁用") + "模块「" + module.cnName() + "」&7(" + module.id() + ")");
        plugin.getLogger().info("[模块管理] " + sender.getName() + " " + (enable ? "启用" : "禁用") + "了模块 " + module.id());
    }

    private void reload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("canglan.admin")) {
            Msg.send(sender, "&c只有服主（拥有 canglan.admin 权限）才能重载配置。");
            return;
        }
        if (args.length >= 2 && !args[1].equalsIgnoreCase("all")) {
            Module module = plugin.modules().get(args[1]);
            if (module == null) {
                Msg.send(sender, "&c没有找到模块：&f" + args[1]);
                return;
            }
            module.reload();
            Msg.send(sender, "&a已重载模块「" + module.cnName() + "」。");
            return;
        }
        plugin.reloadEverything();
        Msg.send(sender, "&a已重载全部配置与数据文件。");
        plugin.getLogger().info("[模块管理] " + sender.getName() + " 重载了全部配置。");
    }

    private void playerCmd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("canglan.admin")) {
            Msg.send(sender, "&c只有服主（拥有 canglan.admin 权限）才能设置玩家权限。");
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, "&c用法：/cl 玩家 <玩家> 列表 | 允许 <模块> | 禁止 <模块> | 重置 <模块>");
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            Msg.send(sender, "&c没有找到玩家：&f" + args[1]);
            return;
        }
        UUID uuid = target.getUniqueId();
        String targetName = target.getName() == null ? args[1] : target.getName();
        String action = args.length >= 3 ? normalize(args[2]) : "list";
        if (action.equals("list")) {
            Map<String, Boolean> overrides = plugin.modules().overridesOf(uuid);
            Msg.header(sender, "&b" + targetName + " &7的模块单独设置");
            if (overrides.isEmpty()) {
                Msg.raw(sender, "&7该玩家没有任何单独设置，全部按默认权限。");
            } else {
                for (Map.Entry<String, Boolean> entry : overrides.entrySet()) {
                    Module module = plugin.modules().get(entry.getKey());
                    String moduleName = module == null ? entry.getKey() : module.cnName() + "&8(" + entry.getKey() + ")";
                    Msg.raw(sender, "&f" + moduleName + " &8- "
                            + (entry.getValue() ? "&a允许" : "&c禁止"));
                }
            }
            Msg.raw(sender, "&7该玩家权限组：&f" + plugin.perms().groupName(uuid));
            return;
        }
        if (args.length < 4) {
            Msg.send(sender, "&c用法：/cl 玩家 <玩家> 允许|禁止|重置 <模块>");
            return;
        }
        Module module = plugin.modules().get(args[3]);
        if (module == null) {
            Msg.send(sender, "&c没有找到模块：&f" + args[3]);
            return;
        }
        switch (action) {
            case "allow" -> {
                plugin.modules().setOverride(uuid, module.id(), Boolean.TRUE);
                Msg.send(sender, "&a已允许 &f" + targetName + " &a使用「" + module.cnName() + "」（即使没有权限节点）。");
                applyIfOnline(uuid);
            }
            case "deny" -> {
                plugin.modules().setOverride(uuid, module.id(), Boolean.FALSE);
                Msg.send(sender, "&c已禁止 &f" + targetName + " &c使用「" + module.cnName() + "」。");
            }
            case "reset" -> {
                plugin.modules().setOverride(uuid, module.id(), null);
                Msg.send(sender, "&a已把 &f" + targetName + " &a对「" + module.cnName() + "」的设置恢复为默认。");
                applyIfOnline(uuid);
            }
            default -> Msg.send(sender, "&c未知操作：&f" + args[2] + "&c，可用：允许 / 禁止 / 重置 / 列表");
        }
        plugin.getLogger().info("[玩家授权] " + sender.getName() + " -> " + targetName + " " + action + " " + module.id());
    }

    private void applyIfOnline(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            plugin.perms().apply(online);
        }
    }

    private void my(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "&c这条指令只能由玩家执行。");
            return;
        }
        Msg.header(sender, "&b&l你可以使用的模块");
        int usable = 0;
        for (Module module : plugin.modules().all()) {
            boolean can = plugin.modules().canUse(player, module.id());
            if (can) {
                usable++;
            }
            Msg.raw(sender, (can ? "&a✔ " : "&c✘ ") + "&f" + module.cnName()
                    + " &8(" + module.id() + ") &8- &7" + module.description());
        }
        Msg.raw(sender, "&8&m------------------------------------------------");
        Msg.raw(sender, "&7共 &f" + usable + "&7/" + plugin.modules().size() + " 个模块可用。");
        Msg.raw(sender, "&7如果某个模块用不了，请联系服主开权限。");
    }

    private void status(CommandSender sender) {
        Msg.header(sender, "&b&l服务器运行状态");
        Msg.raw(sender, "&7模块：&a" + plugin.modules().enabledCount() + " &7启用 / &c"
                + (plugin.modules().size() - plugin.modules().enabledCount()) + " &7禁用，共 &f"
                + plugin.modules().size() + " &7个");
        Msg.raw(sender, "&7在线人数：&f" + Bukkit.getOnlinePlayers().size());
        double[] tps = Bukkit.getTPS();
        Msg.raw(sender, "&7TPS：&f" + String.format("%.2f", Math.min(20.0, tps[0]))
                + " &8/ &f" + String.format("%.2f", Math.min(20.0, tps[1]))
                + " &8/ &f" + String.format("%.2f", Math.min(20.0, tps[2])));
        Msg.raw(sender, "&7内存：&f" + formatMemory());
        Msg.raw(sender, "&7版本：&f" + Bukkit.getVersion());
    }

    private String formatMemory() {
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
        long max = runtime.maxMemory() / 1024L / 1024L;
        return used + " MB / " + max + " MB";
    }

    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "list", "ls", "modules", "列表", "模块", "模块列表", "所有" -> "list";
            case "info", "detail", "信息", "详情", "介绍" -> "info";
            case "enable", "on", "open", "启用", "开启", "打开" -> "enable";
            case "disable", "off", "close", "禁用", "关闭" -> "disable";
            case "reload", "rl", "重载", "刷新" -> "reload";
            case "player", "user", "玩家", "用户" -> "player";
            case "my", "mine", "我的", "自己" -> "my";
            case "status", "状态", "情况" -> "status";
            case "help", "?", "帮助", "命令", "指令" -> "help";
            case "allow", "yes", "允许", "同意", "给" -> "allow";
            case "deny", "no", "禁止", "拒绝", "不给" -> "deny";
            case "reset", "default", "重置", "恢复", "默认" -> "reset";
            default -> raw.toLowerCase(Locale.ROOT);
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!name.equals("canglan") && !name.equals("cl") && !name.equals("cyan")) {
            Module module = plugin.modules().byCommand(name);
            if (module == null) {
                return List.of();
            }
            return module.onTabComplete(sender, name, args);
        }
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : Arrays.asList("帮助", "列表", "信息", "启用", "禁用", "重载", "玩家", "我的", "状态")) {
                if (sub.startsWith(args[0])) {
                    out.add(sub);
                }
            }
            for (Module module : plugin.modules().all()) {
                if (module.id().startsWith(args[0])) {
                    out.add(module.id());
                }
            }
            return out;
        }
        String sub = normalize(args[0]);
        switch (sub) {
            case "info", "enable", "disable" -> {
                if (args.length == 2) {
                    for (Module module : plugin.modules().all()) {
                        if (module.id().startsWith(args[1]) || module.cnName().startsWith(args[1])) {
                            out.add(module.id());
                        }
                    }
                }
            }
            case "reload" -> {
                if (args.length == 2) {
                    out.add("all");
                    for (Module module : plugin.modules().all()) {
                        if (module.id().startsWith(args[1])) {
                            out.add(module.id());
                        }
                    }
                }
            }
            case "player" -> {
                if (args.length == 2) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                            out.add(player.getName());
                        }
                    }
                } else if (args.length == 3) {
                    for (String action : Arrays.asList("允许", "禁止", "重置", "列表")) {
                        if (action.startsWith(args[2])) {
                            out.add(action);
                        }
                    }
                } else if (args.length == 4) {
                    for (Module module : plugin.modules().all()) {
                        if (module.id().startsWith(args[3])) {
                            out.add(module.id());
                        }
                    }
                }
            }
            default -> {
            }
        }
        return out;
    }
}
