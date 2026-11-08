package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Perms;
import com.canglan.survival.core.Util;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 模块 01 · 权限系统（LuckPerms 风格）
 * 组、组继承、组前缀、玩家额外权限节点、单玩家授权。
 */
public class PermModule extends Module {

    public PermModule(CanglanSurvival plugin) {
        super(plugin, "perm", "权限系统",
                "LuckPerms 风格的权限组：创建组、设置组前缀、继承、给玩家/组增删权限节点。"
                        + "如果服务器已安装 LuckPerms，权限以 LuckPerms 为准，本模块仍可管理前缀。",
                true, "perm");
    }

    @Override
    protected void onEnable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.perms().apply(player);
        }
    }

    @Override
    protected void onDisable() {
        // 权限附件保留到玩家下线，关闭模块时不做清理，避免其它插件权限异常。
    }

    @Override
    public void reload() {
        plugin.perms().loadData();
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.perms().apply(player);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equals("帮助")) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list", "groups", "组列表", "列表" -> groups(sender);
            case "create", "创建", "creategroup" -> createGroup(sender, args);
            case "delete", "删除" -> deleteGroup(sender, args);
            case "group", "组" -> groupCmd(sender, args);
            case "user", "玩家", "用户" -> userCmd(sender, args);
            case "reload", "重载" -> {
                if (!checkAdmin(sender)) {
                    return true;
                }
                reload();
                Msg.send(sender, "&a权限数据已重载。");
            }
            default -> Msg.send(sender, "&c未知子指令，输入 &f/perm 帮助 &c查看用法。");
        }
        return true;
    }

    private void help(CommandSender sender) {
        Msg.header(sender, "&b&l权限系统 &7(LuckPerms 风格)");
        Msg.raw(sender, "&7管理权限组：");
        Msg.raw(sender, "&f/perm 组列表 &8- &7查看所有权限组");
        Msg.raw(sender, "&f/perm 创建 <组名> [前缀] [权重] &8- &7创建权限组");
        Msg.raw(sender, "&f/perm 删除 <组名> &8- &7删除权限组");
        Msg.raw(sender, "&f/perm 组 <组名> 前缀 <文本> &8- &7设置组前缀");
        Msg.raw(sender, "&f/perm 组 <组名> 权重 <数字> &8- &7设置组权重");
        Msg.raw(sender, "&f/perm 组 <组名> 添加权限 <节点> &8- &7给组加权限");
        Msg.raw(sender, "&f/perm 组 <组名> 移除权限 <节点> &8- &7给组删权限");
        Msg.raw(sender, "&f/perm 组 <组名> 继承 <父组> &8- &7设置组继承");
        Msg.raw(sender, "&7管理玩家：");
        Msg.raw(sender, "&f/perm 玩家 <玩家> 信息 &8- &7查看玩家权限信息");
        Msg.raw(sender, "&f/perm 玩家 <玩家> 设置组 <组名> &8- &7把玩家加入权限组");
        Msg.raw(sender, "&f/perm 玩家 <玩家> 添加权限 <节点> &8- &7给玩家单独加权限");
        Msg.raw(sender, "&f/perm 玩家 <玩家> 移除权限 <节点> &8- &7移除玩家单独权限");
        Msg.raw(sender, "&7提示：单开某个模块也可以用 &f/cl 玩家 <玩家> 允许|禁止 <模块>");
        Msg.raw(sender, "&7常用节点：&fcanglan.admin &7（全部管理）、&fcanglan.module.<模块ID>");
    }

    private void groups(CommandSender sender) {
        if (!check(sender, "canglan.module.perm")) {
            return;
        }
        Msg.header(sender, "&b&l权限组列表");
        for (Perms.Group group : plugin.perms().groups()) {
            Msg.raw(sender, "&f" + group.name() + " &8| &7前缀：" + group.prefix()
                    + " &8| &7权重：&f" + group.weight()
                    + " &8| &7权限：&f" + group.permissions().size()
                    + " &8| &7继承：&f" + (group.parents().isEmpty() ? "无" : String.join(",", group.parents())));
        }
        Msg.raw(sender, "&7在线玩家权限组：");
        for (Player player : Bukkit.getOnlinePlayers()) {
            Msg.raw(sender, "&8- &f" + player.getName() + " &7→ &f" + plugin.perms().groupName(player.getUniqueId()));
        }
    }

    private void createGroup(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, "&c用法：/perm 创建 <组名> [前缀] [权重]");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (plugin.perms().group(name) != null) {
            Msg.send(sender, "&c权限组 &f" + name + " &c已存在。");
            return;
        }
        String prefix = args.length > 2 ? args[2] : "&7";
        int weight = 0;
        if (args.length > 3) {
            Integer parsed = parseInt(sender, args[3]);
            if (parsed == null) {
                return;
            }
            weight = parsed;
        }
        plugin.perms().createGroup(name, prefix, weight);
        Msg.send(sender, "&a已创建权限组 &f" + name + " &7（前缀 " + prefix + "&7，权重 " + weight + "）");
    }

    private void deleteGroup(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, "&c用法：/perm 删除 <组名>");
            return;
        }
        if (plugin.perms().deleteGroup(args[1])) {
            Msg.send(sender, "&a已删除权限组 &f" + args[1] + "&a，原成员已移至 default 组。");
        } else {
            Msg.send(sender, "&c删除失败：组不存在，或不能删除 default 组。");
        }
    }

    private void groupCmd(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            Msg.send(sender, "&c用法：/perm 组 <组名> 前缀|权重|添加权限|移除权限|继承 <值>");
            return;
        }
        Perms.Group group = plugin.perms().group(args[1]);
        if (group == null) {
            Msg.send(sender, "&c权限组不存在：&f" + args[1]);
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "prefix", "前缀" -> {
                if (args.length < 4) {
                    Msg.send(sender, "&c用法：/perm 组 <组名> 前缀 <文本>");
                    return;
                }
                String prefix = Util.join(args, 3);
                plugin.perms().yaml().set("groups." + group.name() + ".prefix", prefix);
                plugin.perms().save();
                plugin.perms().loadData();
                Msg.send(sender, "&a已设置组 &f" + group.name() + " &a的前缀为：" + Msg.color(prefix));
            }
            case "weight", "权重" -> {
                if (args.length < 4) {
                    Msg.send(sender, "&c用法：/perm 组 <组名> 权重 <数字>");
                    return;
                }
                Integer weight = parseInt(sender, args[3]);
                if (weight == null) {
                    return;
                }
                plugin.perms().yaml().set("groups." + group.name() + ".weight", weight);
                plugin.perms().save();
                plugin.perms().loadData();
                Msg.send(sender, "&a已设置组 &f" + group.name() + " &a的权重为 &f" + weight + "&a。");
            }
            case "addperm", "添加权限", "add" -> {
                if (args.length < 4) {
                    Msg.send(sender, "&c用法：/perm 组 <组名> 添加权限 <节点>");
                    return;
                }
                plugin.perms().addGroupPerm(group.name(), args[3]);
                Msg.send(sender, "&a已给组 &f" + group.name() + " &a添加权限 &f" + args[3]);
            }
            case "delperm", "移除权限", "remove" -> {
                if (args.length < 4) {
                    Msg.send(sender, "&c用法：/perm 组 <组名> 移除权限 <节点>");
                    return;
                }
                plugin.perms().removeGroupPerm(group.name(), args[3]);
                Msg.send(sender, "&a已从组 &f" + group.name() + " &a移除权限 &f" + args[3]);
            }
            case "parent", "继承" -> {
                if (args.length < 4) {
                    Msg.send(sender, "&c用法：/perm 组 <组名> 继承 <父组名>");
                    return;
                }
                List<String> parents = new ArrayList<>(group.parents());
                if (!parents.contains(args[3].toLowerCase(Locale.ROOT)) && args[3].equalsIgnoreCase("none")) {
                    parents.clear();
                } else if (!parents.contains(args[3].toLowerCase(Locale.ROOT))) {
                    parents.add(args[3].toLowerCase(Locale.ROOT));
                }
                plugin.perms().yaml().set("groups." + group.name() + ".parents", parents);
                plugin.perms().save();
                plugin.perms().loadData();
                Msg.send(sender, "&a已设置组 &f" + group.name() + " &a的继承为：&f"
                        + (parents.isEmpty() ? "无" : String.join(",", parents)));
            }
            default -> Msg.send(sender, "&c未知操作：&f" + args[2]);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.perms().apply(player);
        }
    }

    private void userCmd(CommandSender sender, String[] args) {
        if (!check(sender, "canglan.module.perm")) {
            return;
        }
        if (args.length < 3) {
            Msg.send(sender, "&c用法：/perm 玩家 <玩家> 信息|设置组|添加权限|移除权限 <值>");
            return;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(args[1]);
        }
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            Msg.send(sender, "&c没有找到玩家：&f" + args[1]);
            return;
        }
        UUID uuid = target.getUniqueId();
        String name = target.getName() == null ? args[1] : target.getName();
        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "info", "信息" -> {
                Msg.header(sender, "&b" + name + " &7的权限信息");
                Msg.raw(sender, "&7权限组：&f" + plugin.perms().groupName(uuid)
                        + " &8| &7前缀：" + plugin.perms().prefixOf(uuid));
                Msg.raw(sender, "&7单独权限节点：");
                if (plugin.perms().userPerms(uuid).isEmpty()) {
                    Msg.raw(sender, "&8- &7无");
                } else {
                    for (String node : plugin.perms().userPerms(uuid)) {
                        Msg.raw(sender, "&8- &f" + node);
                    }
                }
                Msg.raw(sender, "&7最终生效节点：&f" + plugin.perms().resolve(uuid).size() + " 个");
                Msg.raw(sender, "&7模块单独设置：&f" + plugin.modules().overridesOf(uuid));
            }
            case "setgroup", "设置组", "group" -> {
                if (!checkAdmin(sender)) {
                    return;
                }
                if (args.length < 4) {
                    Msg.send(sender, "&c用法：/perm 玩家 <玩家> 设置组 <组名>");
                    return;
                }
                if (plugin.perms().group(args[3]) == null) {
                    Msg.send(sender, "&c权限组不存在：&f" + args[3]);
                    return;
                }
                plugin.perms().setGroup(uuid, args[3]);
                Msg.send(sender, "&a已把 &f" + name + " &a加入权限组 &f" + args[3]);
            }
            case "addperm", "添加权限", "add" -> {
                if (!checkAdmin(sender)) {
                    return;
                }
                if (args.length < 4) {
                    Msg.send(sender, "&c用法：/perm 玩家 <玩家> 添加权限 <节点>");
                    return;
                }
                plugin.perms().addUserPerm(uuid, args[3]);
                Msg.send(sender, "&a已给 &f" + name + " &a添加权限 &f" + args[3]);
            }
            case "delperm", "移除权限", "remove" -> {
                if (!checkAdmin(sender)) {
                    return;
                }
                if (args.length < 4) {
                    Msg.send(sender, "&c用法：/perm 玩家 <玩家> 移除权限 <节点>");
                    return;
                }
                plugin.perms().removeUserPerm(uuid, args[3]);
                Msg.send(sender, "&a已移除 &f" + name + " &a的权限 &f" + args[3]);
            }
            default -> Msg.send(sender, "&c未知操作：&f" + args[2]);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("帮助", "组列表", "创建", "删除", "组", "玩家", "重载"), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("组") || sub.equals("group")) {
                List<String> names = new ArrayList<>();
                plugin.perms().groups().forEach(g -> names.add(g.name()));
                return filter(names, args[1]);
            }
            if (sub.equals("玩家") || sub.equals("user")) {
                List<String> names = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                return filter(names, args[1]);
            }
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("组") || sub.equals("group")) {
                return filter(Arrays.asList("前缀", "权重", "添加权限", "移除权限", "继承"), args[2]);
            }
            if (sub.equals("玩家") || sub.equals("user")) {
                return filter(Arrays.asList("信息", "设置组", "添加权限", "移除权限"), args[2]);
            }
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                out.add(option);
            }
        }
        return out;
    }
}
