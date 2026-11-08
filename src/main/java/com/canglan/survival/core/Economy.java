package com.canglan.survival.core;

import com.canglan.survival.CanglanSurvival;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 内置经济系统：余额、增减、排行。商店、签到、排行榜等模块共用。
 */
public class Economy extends YamlStore {

    private final Map<UUID, Double> balances = new java.util.LinkedHashMap<>();

    public Economy(CanglanSurvival plugin) {
        super(plugin, "economy.yml");
        loadData();
    }

    public void loadData() {
        balances.clear();
        ConfigurationSection section = yaml.getConfigurationSection("balances");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), section.getDouble(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public double balance(UUID uuid) {
        return balances.getOrDefault(uuid, 0.0D);
    }

    public void set(UUID uuid, double amount) {
        balances.put(uuid, Math.max(0.0D, amount));
    }

    public void add(UUID uuid, double amount) {
        set(uuid, balance(uuid) + amount);
    }

    public boolean take(UUID uuid, double amount) {
        if (amount <= 0) {
            return true;
        }
        double current = balance(uuid);
        if (current < amount) {
            return false;
        }
        set(uuid, current - amount);
        return true;
    }

    public boolean has(UUID uuid, double amount) {
        return balance(uuid) >= amount;
    }

    public List<Map.Entry<UUID, Double>> top(int limit) {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>(balances.entrySet());
        list.sort(Comparator.comparingDouble((Map.Entry<UUID, Double> e) -> e.getValue()).reversed());
        if (list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    @Override
    public void save() {
        yaml.set("balances", null);
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            yaml.set("balances." + entry.getKey(), Math.round(entry.getValue() * 100.0D) / 100.0D);
        }
        super.save();
    }
}
