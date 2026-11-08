package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import com.canglan.survival.core.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 模块 05 · 经济系统
 * 金币余额、转账、财富排行、管理员增减。
 */
public class EconomyModule extends Module {

    private final YamlStore store;

    private final Listener joinListener = new Listener() {
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            UUID uuid = event.getPlayer().getUniqueId();
            if (store.yaml().getBoolean("initialized." + uuid, false)) {
                return;
            }
            double starting = plugin.getConfig().getDouble("economy.starting-balance", 100.0D);
            plugin.economy().set(uuid, starting);
            store.yaml().set("initialized." + uuid, true);
            store.save();
            Msg.send(event.getPlayer(), "&a作为新玩家，你获得了初始金币 &6" + Util.money(starting) + " &a。");
        }
    };

    public EconomyModule(CanglanSurvival plugin) {
        super(plugin, "economy", "经济系统",
                "服务器金币系统：余额查询、玩家转账、财富排行榜、管理员发放与扣除，是商店/签到/排行等模块的基础。",
                true, "money", "bal", "pay", "baltop", "eco");
        this.store = new YamlStore(plugin, "economy-module.yml");
    }

    @Override
    protected void onEnable() {
        listen(joinListener);
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!store.yaml().getBoolean("initialized." + uuid, false)) {
                plugin.economy().set(uuid, plugin.getConfig().getDouble("economy.starting-balance", 100.0D));
                store.yaml().set("initialized." + uuid, true);
                store.save();
            }
        }
    }

    @Override
    protected void onDisable() {
        plugin.economy().save();
    }

    @Override
    public void reload() {
        store.load();
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        String currency = plugin.getConfig().getString("economy.currency", "金币");
        switch (command.toLowerCase()) {
            case "money", "bal" -> {
                Player player = asPlayer(sender);
                if (player == null) {
                    return true;
                }
                if (args.length >= 1) {
                    if (!check(sender, "canglan.economy.admin")) {
                        return true;
                    }
                    OfflinePlayer target = resolve(args[0]);
                    if (target == null) {
                        Msg.send(sender, "&c没有找到玩家：&f" + args[0]);
                        return true;
                    }
                    Msg.send(sender, "&7玩家 &f" + target.getName() + " &7的金币：&6"
                            + Util.money(plugin.economy().balance(target.getUniqueId())) + " " + currency);
                    return true;
                }
                Msg.send(sender, "&7你的金币：&6" + Util.money(plugin.economy().balance(player.getUniqueId()))
                        + " " + currency);
            }
            case "pay" -> pay(sender, args, currency);
            case "baltop" -> baltop(sender, args, currency);
            case "eco" -> eco(sender, args, currency);
            default -> Msg.send(sender, "&c未知经济指令。");
        }
        return true;
    }

    private void pay(CommandSender sender, String[] args, String currency) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, "&c用法：/pay <玩家> <金额>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Msg.send(sender, "&c玩家 &f" + args[0] + " &c不在线，无法转账。");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            Msg.send(sender, "&c不能给自己转账。");
            return;
        }
        Double amount = parseDouble(sender, args[1]);
        if (amount == null) {
            return;
        }
        if (amount <= 0) {
            Msg.send(sender, "&c转账金额必须大于 0。");
            return;
        }
        double fee = plugin.getConfig().getDouble("economy.transfer-fee", 0.0D);
        double total = amount + amount * fee;
        if (!plugin.economy().take(player.getUniqueId(), total)) {
            Msg.send(sender, "&c你的金币不足，需要 &6" + Util.money(total) + " " + currency
                    + (fee > 0 ? " &7（含手续费 " + Util.money(amount * fee) + "）" : ""));
            return;
        }
        plugin.economy().add(target.getUniqueId(), amount);
        Msg.send(sender, "&a已转账 &6" + Util.money(amount) + " " + currency + " &a给 &f" + target.getName()
                + (fee > 0 ? " &7（手续费 " + Util.money(amount * fee) + "）" : ""));
        Msg.send(target, "&a你收到来自 &f" + player.getName() + " &a的转账 &6" + Util.money(amount) + " " + currency);
    }

    private void baltop(CommandSender sender, String[] args, String currency) {
        int page = 1;
        if (args.length > 0) {
            Integer parsed = parseInt(sender, args[0]);
            if (parsed == null) {
                return;
            }
            page = parsed;
        }
        List<Map.Entry<UUID, Double>> top = plugin.economy().top(100);
        Msg.header(sender, "&b&l财富排行榜 &7(第 " + page + " 页)");
        if (top.isEmpty()) {
            Msg.raw(sender, "&7暂时还没有人拥有金币。");
            return;
        }
        int perPage = 10;
        int from = (Math.max(1, page) - 1) * perPage;
        for (int i = from; i < Math.min(top.size(), from + perPage); i++) {
            Map.Entry<UUID, Double> entry = top.get(i);
            String name = nameOf(entry.getKey());
            Msg.raw(sender, "&8" + (i + 1) + ". &f" + name + " &8- &6" + Util.money(entry.getValue()) + " " + currency);
        }
        Msg.raw(sender, "&7共 &f" + top.size() + "&7 名玩家，下一页：&f/baltop " + (page + 1));
    }

    private void eco(CommandSender sender, String[] args, String currency) {
        if (!check(sender, "canglan.economy.admin")) {
            return;
        }
        if (args.length < 3) {
            Msg.send(sender, "&c用法：/eco give|take|set <玩家> <金额>");
            return;
        }
        String action = args[0].toLowerCase();
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            Msg.send(sender, "&c没有找到玩家：&f" + args[1]);
            return;
        }
        Double amount = parseDouble(sender, args[2]);
        if (amount == null) {
            return;
        }
        switch (action) {
            case "give" -> {
                plugin.economy().add(target.getUniqueId(), amount);
                Msg.send(sender, "&a已给 &f" + target.getName() + " &a发放 &6" + Util.money(amount) + " " + currency);
            }
            case "take" -> {
                if (!plugin.economy().take(target.getUniqueId(), amount)) {
                    Msg.send(sender, "&c该玩家金币不足。");
                    return;
                }
                Msg.send(sender, "&a已从 &f" + target.getName() + " &a扣除 &6" + Util.money(amount) + " " + currency);
            }
            case "set" -> {
                plugin.economy().set(target.getUniqueId(), amount);
                Msg.send(sender, "&a已设置 &f" + target.getName() + " &a的金币为 &6" + Util.money(amount) + " " + currency);
            }
            default -> Msg.send(sender, "&c未知操作：&f" + action + "&c（可用 give / take / set）");
        }
    }

    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private String nameOf(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (command.equalsIgnoreCase("eco")) {
            if (args.length == 1) {
                return filter(List.of("give", "take", "set"), args[0]);
            }
            if (args.length == 2) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            }
        }
        if ((command.equalsIgnoreCase("pay") || command.equalsIgnoreCase("money")) && args.length == 1) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[0]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new java.util.ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(prefix.toLowerCase())) {
                out.add(option);
            }
        }
        return out;
    }
}
