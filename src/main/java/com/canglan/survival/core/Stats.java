package com.canglan.survival.core;

import com.canglan.survival.CanglanSurvival;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家统计：击杀、死亡、挖掘、放置、在线时长、进入次数。
 * 排行榜、计分板、统计模块共用。
 */
public class Stats extends YamlStore {

    public static final String KILLS = "kills";
    public static final String DEATHS = "deaths";
    public static final String BROKEN = "broken";
    public static final String PLACED = "placed";
    public static final String PLAYTIME = "playtime";
    public static final String JOINS = "joins";

    private final Map<UUID, Map<String, Long>> data = new LinkedHashMap<>();
    private final Map<UUID, Long> sessions = new HashMap<>();

    public Stats(CanglanSurvival plugin) {
        super(plugin, "stats.yml");
        loadData();
    }

    public void loadData() {
        data.clear();
        ConfigurationSection section = yaml.getConfigurationSection("stats");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Map<String, Long> map = new LinkedHashMap<>();
            ConfigurationSection stats = section.getConfigurationSection(key);
            if (stats != null) {
                for (String statKey : stats.getKeys(false)) {
                    map.put(statKey, stats.getLong(statKey));
                }
            }
            data.put(uuid, map);
        }
    }

    public Map<String, Long> of(UUID uuid) {
        return data.computeIfAbsent(uuid, k -> new LinkedHashMap<>());
    }

    public long get(UUID uuid, String key) {
        long base = of(uuid).getOrDefault(key, 0L);
        if (PLAYTIME.equals(key)) {
            Long start = sessions.get(uuid);
            if (start != null) {
                return base + (System.currentTimeMillis() - start) / 1000L;
            }
        }
        return base;
    }

    public void set(UUID uuid, String key, long value) {
        of(uuid).put(key, Math.max(0L, value));
    }

    public void add(UUID uuid, String key, long delta) {
        set(uuid, key, get(uuid, key) + delta);
    }

    public void join(UUID uuid) {
        sessions.put(uuid, System.currentTimeMillis());
        add(uuid, JOINS, 1L);
    }

    public void quit(UUID uuid) {
        Long start = sessions.remove(uuid);
        if (start == null) {
            return;
        }
        long seconds = (System.currentTimeMillis() - start) / 1000L;
        if (seconds > 0) {
            Map<String, Long> map = of(uuid);
            map.put(PLAYTIME, map.getOrDefault(PLAYTIME, 0L) + seconds);
        }
    }

    public void flush() {
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            Long start = sessions.remove(uuid);
            if (start == null) {
                continue;
            }
            long seconds = (System.currentTimeMillis() - start) / 1000L;
            if (seconds > 0) {
                Map<String, Long> map = of(uuid);
                map.put(PLAYTIME, map.getOrDefault(PLAYTIME, 0L) + seconds);
            }
        }
    }

    public List<Map.Entry<UUID, Long>> top(String key, int limit) {
        List<Map.Entry<UUID, Long>> list = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, Long>> entry : data.entrySet()) {
            long value = get(entry.getKey(), key);
            if (value > 0) {
                list.add(Map.entry(entry.getKey(), value));
            }
        }
        list.sort(Comparator.comparingLong((Map.Entry<UUID, Long> e) -> e.getValue()).reversed());
        if (list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    @Override
    public void save() {
        yaml.set("stats", null);
        for (Map.Entry<UUID, Map<String, Long>> entry : data.entrySet()) {
            for (Map.Entry<String, Long> stat : entry.getValue().entrySet()) {
                yaml.set("stats." + entry.getKey() + "." + stat.getKey(), stat.getValue());
            }
        }
        super.save();
    }
}
