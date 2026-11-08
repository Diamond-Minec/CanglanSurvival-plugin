package com.canglan.survival.core;

import com.canglan.survival.CanglanSurvival;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 禁言数据（聊天管理、处罚模块共用）。
 */
public class Mutes extends YamlStore {

    public static class Mute {
        public final long until;
        public final String reason;
        public final String by;

        public Mute(long until, String reason, String by) {
            this.until = until;
            this.reason = reason;
            this.by = by;
        }

        public boolean active() {
            return until < 0 || until > System.currentTimeMillis();
        }
    }

    private final Map<UUID, Mute> mutes = new ConcurrentHashMap<>();

    public Mutes(CanglanSurvival plugin) {
        super(plugin, "mutes.yml");
        loadData();
    }

    public void loadData() {
        mutes.clear();
        if (yaml.getConfigurationSection("mutes") == null) {
            return;
        }
        for (String key : yaml.getConfigurationSection("mutes").getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            long until = yaml.getLong("mutes." + key + ".until", -1L);
            String reason = yaml.getString("mutes." + key + ".reason", "未填写原因");
            String by = yaml.getString("mutes." + key + ".by", "控制台");
            Mute mute = new Mute(until, reason, by);
            if (mute.active()) {
                mutes.put(uuid, mute);
            }
        }
    }

    public boolean isMuted(UUID uuid) {
        Mute mute = mutes.get(uuid);
        if (mute == null) {
            return false;
        }
        if (!mute.active()) {
            mutes.remove(uuid);
            return false;
        }
        return true;
    }

    public Mute mute(UUID uuid) {
        return mutes.get(uuid);
    }

    public void mute(UUID uuid, long seconds, String reason, String by) {
        long until = seconds <= 0 ? -1L : System.currentTimeMillis() + seconds * 1000L;
        mutes.put(uuid, new Mute(until, reason, by));
        yaml.set("mutes." + uuid + ".until", until);
        yaml.set("mutes." + uuid + ".reason", reason);
        yaml.set("mutes." + uuid + ".by", by);
        save();
    }

    public void unmute(UUID uuid) {
        mutes.remove(uuid);
        yaml.set("mutes." + uuid, null);
        save();
    }

    public long remaining(UUID uuid) {
        Mute mute = mutes.get(uuid);
        if (mute == null || !mute.active()) {
            return 0L;
        }
        return mute.until < 0 ? -1L : (mute.until - System.currentTimeMillis()) / 1000L;
    }
}
