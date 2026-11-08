package com.canglan.survival.core;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 中文指令别名：玩家在聊天框里输入 /公告、/商店、/签到 这类中文指令时，
 * 自动改写成对应的英文指令（/notice、/shop、/signin）再执行。
 * 英文指令本身照常可用，两种写法都行。
 */
public class CommandAliases implements Listener {

    /** 中文（小写）→ 英文指令名 */
    private static final Map<String, String> MAP = new LinkedHashMap<>();

    static {
        put("notice", "公告", "公告栏", "通知", "公告板");
        put("shop", "商店", "商城", "系统商店");
        put("money", "金币", "余额", "我的金币");
        put("pay", "转账", "付款", "给钱");
        put("baltop", "财富榜", "富豪榜", "金币榜");
        put("eco", "经济", "经济管理");
        put("claim", "领地", "圈地", "占用领地");
        put("unclaim", "取消领地", "放弃领地", "退领地");
        put("warps", "地标列表", "传送点列表", "地标");
        put("warp", "传送点", "地标传送");
        put("setwarp", "设置地标", "设置传送点");
        put("delwarp", "删除地标", "删除传送点");
        put("rtp", "随机传送", "随机");
        put("home", "家", "回家", "我的家");
        put("sethome", "设置家", "创建家");
        put("delhome", "删除家");
        put("homes", "家园", "家园列表");
        put("tpa", "传送请求", "请求传送");
        put("tpahere", "邀请传送", "拉人传送");
        put("tpaccept", "接受传送", "同意传送");
        put("tpdeny", "拒绝传送", "拒绝请求");
        put("report", "举报");
        put("reports", "举报列表", "举报记录");
        put("warn", "警告");
        put("warnings", "警告记录", "查看警告");
        put("kick", "踢出", "踢人");
        put("ban", "封禁", "封号");
        put("unban", "解封", "解除封禁");
        put("mute", "禁言");
        put("unmute", "解除禁言", "取消禁言");
        put("register", "注册");
        put("login", "登录");
        put("changepassword", "修改密码", "改密码");
        put("signin", "签到", "打卡");
        put("top", "排行榜", "榜单", "排名");
        put("stats", "统计", "我的统计", "数据");
        put("monitor", "监控", "服务器状态");
        put("menu", "菜单", "钟表菜单", "功能菜单");
        put("perm", "权限", "权限组", "权限管理");
        put("tab", "标签", "列表设置");
        put("sb", "计分板", "侧边栏", "计分版");
        put("chat", "聊天", "聊天管理");
        put("welcome", "欢迎", "服务器规则");
        put("cl", "主菜单", "整合包", "帮助菜单");
        put("servername", "服务器名称", "服务器名", "服务器改名", "改名");
    }

    private static void put(String english, String... chinese) {
        for (String name : chinese) {
            MAP.put(name, english);
        }
    }

    /** 中文指令 → 英文指令。 */
    public String translate(String label) {
        return MAP.get(label.toLowerCase(Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || !message.startsWith("/")) {
            return;
        }
        String body = message.substring(1);
        int space = body.indexOf(' ');
        String label = space < 0 ? body : body.substring(0, space);
        String rest = space < 0 ? "" : body.substring(space);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (label.isEmpty()) {
            return;
        }
        String target = MAP.get(label.toLowerCase(Locale.ROOT));
        if (target != null) {
            event.setMessage("/" + target + rest);
            return;
        }
        if (containsChinese(label) && !known(label)) {
            event.setCancelled(true);
            suggest(event.getPlayer(), label);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsoleCommand(ServerCommandEvent event) {
        String body = event.getCommand();
        if (body == null || body.isEmpty()) {
            return;
        }
        int space = body.indexOf(' ');
        String label = space < 0 ? body : body.substring(0, space);
        String rest = space < 0 ? "" : body.substring(space);
        String target = MAP.get(label.toLowerCase(Locale.ROOT));
        if (target != null) {
            event.setCommand(target + rest);
            return;
        }
        if (containsChinese(label) && !known(label)) {
            event.setCancelled(true);
            event.getSender().sendMessage(Msg.color(Msg.prefix() + "&c没有这个指令：&f" + label
                    + " &7（可以用 /cl 帮助 查看全部指令）"));
        }
    }

    private boolean known(String label) {
        try {
            return Bukkit.getCommandMap().getCommand(label) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void suggest(CommandSender sender, String label) {
        List<String> hints = new ArrayList<>();
        for (Map.Entry<String, String> entry : MAP.entrySet()) {
            String key = entry.getKey();
            if (key.contains(label) || label.contains(key)) {
                hints.add("/" + key + "（/" + entry.getValue() + "）");
                if (hints.size() >= 3) {
                    break;
                }
            }
        }
        if (hints.isEmpty()) {
            Msg.send(sender, "&c没有这个指令：&f/" + label + " &7输入 &f/cl help &7查看全部指令。");
        } else {
            Msg.send(sender, "&c没有这个指令：&f/" + label + " &7你可能想输入：&f" + String.join("&7、&f", hints));
        }
    }

    private static boolean containsChinese(String text) {
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    /** 供 /cl 帮助 使用的映射表大小。 */
    public int size() {
        return MAP.size();
    }

    /** 供帮助界面列出中文别名。 */
    public Map<String, String> mapping() {
        return MAP;
    }

    /** 帮助界面里统一提示英文指令（中文写法照旧可用，但不再作为主推）。 */
    public void hint(Player player) {
        Msg.send(player, "&7常用指令：&f/notice &7公告、&f/shop &7商店、&f/signin &7签到、&f/menu &7功能菜单。");
    }
}
