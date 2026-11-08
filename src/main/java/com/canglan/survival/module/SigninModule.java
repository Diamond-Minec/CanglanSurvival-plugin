package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import com.canglan.survival.core.YamlStore;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 模块 17 · 每日签到
 * /signin 每日签到领取金币，连续签到有额外奖励，断签重新计算。
 */
public class SigninModule extends Module {

    private final YamlStore store;

    public SigninModule(CanglanSurvival plugin) {
        super(plugin, "signin", "每日签到",
                "每日签到领金币：连续签到天数越多奖励越高，断签后重新累计。",
                true, "signin");
        this.store = new YamlStore(plugin, "signin.yml");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
        store.save();
    }

    @Override
    public void reload() {
        store.load();
    }

    private String today() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }

    private String yesterday() {
        return LocalDate.now(ZoneId.systemDefault()).minusDays(1).toString();
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        UUID uuid = player.getUniqueId();
        if (args.length > 0 && (args[0].equalsIgnoreCase("info") || args[0].equals("信息"))) {
            info(player, uuid);
            return true;
        }
        String last = store.yaml().getString("players." + uuid + ".last", "");
        if (today().equals(last)) {
            Msg.send(player, "&e你今天已经签到过了，明天再来吧！");
            info(player, uuid);
            return true;
        }
        int streak = store.yaml().getInt("players." + uuid + ".streak", 0);
        if (yesterday().equals(last)) {
            streak++;
        } else {
            streak = 1;
        }
        int total = store.yaml().getInt("players." + uuid + ".total", 0) + 1;
        double base = plugin.getConfig().getDouble("signin.base-reward", 50.0D);
        double bonus = plugin.getConfig().getDouble("signin.streak-bonus", 10.0D);
        double maxBonus = plugin.getConfig().getDouble("signin.max-streak-bonus", 200.0D);
        double reward = base + Math.min(maxBonus, Math.max(0, streak - 1) * bonus);
        plugin.economy().add(uuid, reward);
        store.yaml().set("players." + uuid + ".last", today());
        store.yaml().set("players." + uuid + ".streak", streak);
        store.yaml().set("players." + uuid + ".total", total);
        store.save();
        Msg.send(player, "&a签到成功！&7连续签到 &f" + streak + " &7天，获得 &6"
                + Util.money(reward) + " &7金币。");
        Msg.send(player, "&7累计签到 &f" + total + " &7天，明天记得再来哦～");
        Msg.title(player, "&a&l签到成功", "&f+" + Util.money(reward) + " 金币", 10, 40, 10);
        return true;
    }

    private void info(Player player, UUID uuid) {
        Msg.header(player, "&b&l每日签到");
        Msg.raw(player, "&7连续签到：&f" + store.yaml().getInt("players." + uuid + ".streak", 0) + " &7天");
        Msg.raw(player, "&7累计签到：&f" + store.yaml().getInt("players." + uuid + ".total", 0) + " &7天");
        Msg.raw(player, "&7上次签到：&f" + store.yaml().getString("players." + uuid + ".last", "从未签到"));
        double base = plugin.getConfig().getDouble("signin.base-reward", 50.0D);
        double bonus = plugin.getConfig().getDouble("signin.streak-bonus", 10.0D);
        double maxBonus = plugin.getConfig().getDouble("signin.max-streak-bonus", 200.0D);
        int streak = store.yaml().getInt("players." + uuid + ".streak", 0);
        if (today().equals(store.yaml().getString("players." + uuid + ".last", ""))) {
            Msg.raw(player, "&7今日已签到，明天可得 &6" + Util.money(base + Math.min(maxBonus, streak * bonus)) + " &7金币。");
        } else {
            Msg.raw(player, "&7今日签到可得 &6" + Util.money(base + Math.min(maxBonus, streak * bonus)) + " &7金币。");
        }
        Msg.raw(player, "&7输入 &f/signin &7立即签到。");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        return args.length == 1 ? List.of("信息") : List.of();
    }
}
