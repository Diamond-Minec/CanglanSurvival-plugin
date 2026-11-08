package com.canglan.survival.core;

import com.canglan.survival.CanglanSurvival;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 模块管理器：负责 20 个功能模块的注册、全局开关、玩家级权限覆盖与持久化。
 */
public class ModuleManager {

    private final CanglanSurvival plugin;
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final Map<String, Module> byCommand = new LinkedHashMap<>();
    private final Map<String, Boolean> global = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Boolean>> overrides = new LinkedHashMap<>();

    private final File stateFile;
    private final File playersFile;
    private YamlConfiguration state;
    private YamlConfiguration players;

    public ModuleManager(CanglanSurvival plugin) {
        this.plugin = plugin;
        File dir = plugin.getDataFolder();
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("无法创建插件数据目录：" + dir.getAbsolutePath());
        }
        this.stateFile = new File(dir, "modules.yml");
        this.playersFile = new File(dir, "players.yml");
        this.state = YamlConfiguration.loadConfiguration(stateFile);
        this.players = YamlConfiguration.loadConfiguration(playersFile);
        this.players.options().setHeader(java.util.List.of(
                "沧蓝工坊 · 玩家单独授权记录",
                "allow = 服主单独允许，deny = 服主单独禁止",
                "游戏内用 /cl 玩家 <玩家> 允许|禁止|重置 <模块> 修改"));
        loadOverrides();
    }

    public void register(Module module) {
        if (modules.containsKey(module.id())) {
            throw new IllegalStateException("模块 ID 重复：" + module.id());
        }
        modules.put(module.id(), module);
        for (String command : module.commands()) {
            byCommand.put(command.toLowerCase(Locale.ROOT), module);
        }
    }

    /** 按 modules.yml 的开关状态初始化所有模块。 */
    public void init() {
        state.options().setHeader(java.util.List.of(
                "沧蓝工坊 · 整合生存服务端核心",
                "模块开关：true = 启用，false = 禁用",
                "可以直接改这个文件后执行 /cl 重载 all，也可以在游戏里用 /cl 启用|禁用 <模块>"));
        for (Module module : modules.values()) {
            boolean on = state.getBoolean("modules." + module.id(), module.defaultEnabled());
            global.put(module.id(), on);
            // 把默认值也显式写进文件，服主打开就能看到 20 个模块的开关
            state.set("modules." + module.id(), on);
        }
        saveState();
        for (Module module : modules.values()) {
            if (Boolean.TRUE.equals(global.get(module.id()))) {
                module.enable();
            }
        }
        plugin.getLogger().info("已载入 " + modules.size() + " 个功能模块。");
    }

    public void shutdown() {
        java.util.List<Module> reversed = new java.util.ArrayList<>(modules.values());
        java.util.Collections.reverse(reversed);
        for (Module module : reversed) {
            module.disable();
        }
        saveState();
        savePlayers();
    }

    public void reloadAll() {
        for (Module module : modules.values()) {
            if (module.isEnabled()) {
                try {
                    module.reload();
                } catch (Throwable t) {
                    plugin.getLogger().warning("重载模块「" + module.cnName() + "」失败：" + t.getMessage());
                }
            }
        }
    }

    public Collection<Module> all() {
        return modules.values();
    }

    public Module get(String id) {
        if (id == null) {
            return null;
        }
        Module direct = modules.get(id.toLowerCase(Locale.ROOT));
        if (direct != null) {
            return direct;
        }
        for (Module module : modules.values()) {
            if (module.cnName().equals(id)) {
                return module;
            }
        }
        return byCommand.get(id.toLowerCase(Locale.ROOT));
    }

    public Module byCommand(String command) {
        return byCommand.get(command.toLowerCase(Locale.ROOT));
    }

    public boolean globalOn(String id) {
        return Boolean.TRUE.equals(global.get(id));
    }

    public boolean setGlobal(String id, boolean on) {
        Module module = modules.get(id);
        if (module == null) {
            return false;
        }
        global.put(id, on);
        state.set("modules." + id, on);
        saveState();
        if (on) {
            module.enable();
        } else {
            module.disable();
        }
        return true;
    }

    /** 模块全局启用，且没有被该玩家单独禁止。 */
    public boolean playerOn(Player player, String id) {
        if (!globalOn(id)) {
            return false;
        }
        return !Boolean.FALSE.equals(override(player.getUniqueId(), id));
    }

    /** 玩家真正能用的判断：全局启用 + 未被禁止 + （被单独允许 或 拥有模块权限节点）。 */
    public boolean canUse(Player player, String id) {
        if (!playerOn(player, id)) {
            return false;
        }
        if (Boolean.TRUE.equals(override(player.getUniqueId(), id))) {
            return true;
        }
        return player.hasPermission("canglan.module." + id);
    }

    public boolean hasNode(Player player, String id) {
        return player.hasPermission("canglan.module." + id);
    }

    public Boolean override(UUID uuid, String moduleId) {
        Map<String, Boolean> map = overrides.get(uuid);
        return map == null ? null : map.get(moduleId);
    }

    public Map<String, Boolean> overridesOf(UUID uuid) {
        return overrides.getOrDefault(uuid, Map.of());
    }

    /** value 为 null 表示恢复默认。 */
    public void setOverride(UUID uuid, String moduleId, Boolean value) {
        Map<String, Boolean> map = overrides.computeIfAbsent(uuid, k -> new LinkedHashMap<>());
        if (value == null) {
            map.remove(moduleId);
            if (map.isEmpty()) {
                overrides.remove(uuid);
            }
        } else {
            map.put(moduleId, value);
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        if (name != null) {
            players.set("players." + uuid + ".name", name);
        }
        if (value == null) {
            players.set("players." + uuid + ".modules." + moduleId, null);
        } else {
            players.set("players." + uuid + ".modules." + moduleId, value);
        }
        savePlayers();
    }

    private void loadOverrides() {
        ConfigurationSection root = players.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection modulesSection = root.getConfigurationSection(key + ".modules");
            if (modulesSection == null) {
                continue;
            }
            Map<String, Boolean> map = new LinkedHashMap<>();
            for (String moduleId : modulesSection.getKeys(false)) {
                map.put(moduleId, modulesSection.getBoolean(moduleId));
            }
            if (!map.isEmpty()) {
                overrides.put(uuid, map);
            }
        }
    }

    public void saveState() {
        try {
            state.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("保存 modules.yml 失败：" + e.getMessage());
        }
    }

    public void savePlayers() {
        try {
            players.save(playersFile);
        } catch (IOException e) {
            plugin.getLogger().warning("保存 players.yml 失败：" + e.getMessage());
        }
    }

    public int size() {
        return modules.size();
    }

    public int enabledCount() {
        int count = 0;
        for (Module module : modules.values()) {
            if (module.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    public Set<String> ids() {
        return new LinkedHashSet<>(modules.keySet());
    }
}
