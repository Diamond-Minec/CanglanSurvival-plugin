package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import com.canglan.survival.core.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 模块 03 · 信息公告栏
 * BOSS 栏轮播公告 + 定时全服广播，公告内容可在游戏内增删。
 */
public class NoticeModule extends Module {

    private final YamlStore store;
    private BossBar bar;
    private int index;
    private int bossTicks;
    private int bossIntervalTicks;
    private int chatTicks;

    private final Listener joinListener = new Listener() {
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            if (bar != null) {
                bar.addPlayer(event.getPlayer());
            }
            event.getPlayer().sendMessage(Msg.color(plugin.getConfig().getString("notice.join-tip",
                    "&b[沧蓝工坊] &f输入 &b/notice &f查看本服公告。")));
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            if (bar != null) {
                bar.removePlayer(event.getPlayer());
            }
        }
    };

    public NoticeModule(CanglanSurvival plugin) {
        super(plugin, "notice", "信息公告栏",
                "BOSS 栏轮播公告 + 定时全服广播，支持在游戏内直接添加、删除公告。",
                true, "notice");
        this.store = new YamlStore(plugin, "notices.yml");
    }

    @Override
    protected void onEnable() {
        if (notices().isEmpty()) {
            for (String line : plugin.getConfig().getStringList("notice.default")) {
                notices().add(line);
            }
            if (notices().isEmpty()) {
                notices().add("&e欢迎来到本服，祝你玩得开心！");
                notices().add("&e输入 &f/帮助 &e或 &f/cl 我的 &e查看你能用的功能。");
                notices().add("&e文明游戏，禁止刷屏、外挂与诈骗。");
            }
            store.save();
        }
        listen(joinListener);
        int bossInterval = Math.max(5, plugin.getConfig().getInt("notice.bossbar-interval", 12));
        int chatInterval = Math.max(30, plugin.getConfig().getInt("notice.chat-interval", 300));
        this.bossIntervalTicks = bossInterval * 20;
        this.bossTicks = this.bossIntervalTicks;
        this.chatTicks = chatInterval * 20;

        if (plugin.getConfig().getBoolean("notice.bossbar-enabled", true)) {
            this.bar = Bukkit.createBossBar(Msg.color("&b&l沧蓝工坊 &7» &f公告加载中..."), BarColor.BLUE, BarStyle.SOLID);
            for (Player player : Bukkit.getOnlinePlayers()) {
                bar.addPlayer(player);
            }
            bar.setVisible(true);
            every(20L, this::tickBossBar);
        }
        if (plugin.getConfig().getBoolean("notice.chat-enabled", true)) {
            every(this.chatTicks, this::broadcastNext);
        }
    }

    @Override
    protected void onDisable() {
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
            bar = null;
        }
    }

    @Override
    public void reload() {
        store.load();
    }

    @SuppressWarnings("unchecked")
    private List<String> notices() {
        List<String> list = (List<String>) store.yaml().getList("list");
        if (list == null) {
            list = new ArrayList<>();
            store.yaml().set("list", list);
        }
        return list;
    }

    private void tickBossBar() {
        if (bar == null) {
            return;
        }
        if (bossTicks <= 0) {
            bossTicks = bossIntervalTicks;
            index = (index + 1) % Math.max(1, notices().size());
        }
        List<String> list = notices();
        if (list.isEmpty()) {
            bar.setTitle(Msg.color("&b&l沧蓝工坊 &7» &f暂无公告"));
            bar.setProgress(1.0D);
            return;
        }
        String text = list.get(Math.floorMod(index, list.size()));
        bar.setTitle(Msg.color("&b&l公告 &7» &f" + text));
        double progress = (double) bossTicks / (double) Math.max(1, bossIntervalTicks);
        bar.setProgress(Math.max(0.0D, Math.min(1.0D, progress)));
        bossTicks -= 20;
    }

    private void broadcastNext() {
        List<String> list = notices();
        if (list.isEmpty()) {
            return;
        }
        String text = list.get(index % list.size());
        index = (index + 1) % list.size();
        Msg.broadcastRaw("&b&l[公告] &f" + text);
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        if (args.length == 0) {
            list(sender, 1);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list", "列表" -> {
                int page = 1;
                if (args.length > 1) {
                    Integer parsed = parseInt(sender, args[1]);
                    if (parsed == null) {
                        return true;
                    }
                    page = parsed;
                }
                list(sender, page);
            }
            case "add", "添加" -> {
                if (!checkAdmin(sender)) {
                    return true;
                }
                if (args.length < 2) {
                    Msg.send(sender, "&c用法：/notice 添加 <公告内容，可用 & 颜色代码>");
                    return true;
                }
                notices().add(Util.join(args, 1));
                store.save();
                Msg.send(sender, "&a已添加公告：&f" + Util.join(args, 1));
            }
            case "del", "删除" -> {
                if (!checkAdmin(sender)) {
                    return true;
                }
                if (args.length < 2) {
                    Msg.send(sender, "&c用法：/notice 删除 <序号>");
                    return true;
                }
                Integer num = parseInt(sender, args[1]);
                if (num == null) {
                    return true;
                }
                List<String> list = notices();
                if (num < 1 || num > list.size()) {
                    Msg.send(sender, "&c序号超出范围，共 " + list.size() + " 条公告。");
                    return true;
                }
                String removed = list.remove(num - 1);
                store.save();
                Msg.send(sender, "&a已删除公告：&f" + removed);
            }
            case "reload", "重载" -> {
                if (!checkAdmin(sender)) {
                    return true;
                }
                reload();
                Msg.send(sender, "&a公告数据已重载。");
            }
            case "off", "关闭" -> {
                Player player = asPlayer(sender);
                if (player != null && bar != null) {
                    bar.removePlayer(player);
                    Msg.send(sender, "&a已关闭你自己的公告栏显示。");
                }
            }
            case "on", "开启" -> {
                Player player = asPlayer(sender);
                if (player != null && bar != null) {
                    bar.addPlayer(player);
                    Msg.send(sender, "&a已开启你自己的公告栏显示。");
                }
            }
            default -> Msg.send(sender, "&c用法：/notice 列表|添加|删除|重载|开启|关闭");
        }
        return true;
    }

    private void list(CommandSender sender, int page) {
        List<String> list = notices();
        Msg.header(sender, "&b&l服务器公告 &7共 " + list.size() + " 条");
        if (list.isEmpty()) {
            Msg.raw(sender, "&7暂无公告。");
            return;
        }
        for (String line : Util.page(enumerate(list), page, 8)) {
            Msg.raw(sender, line);
        }
        Msg.raw(sender, "&7管理员：&f/notice 添加 <内容> &7、&f/notice 删除 <序号>");
    }

    private List<String> enumerate(List<String> list) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            out.add("&8" + (i + 1) + ". &f" + list.get(i));
        }
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String option : List.of("列表", "添加", "删除", "重载", "开启", "关闭")) {
                if (option.startsWith(args[0])) {
                    out.add(option);
                }
            }
            return out;
        }
        return List.of();
    }
}
