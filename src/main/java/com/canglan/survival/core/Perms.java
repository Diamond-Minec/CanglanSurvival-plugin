package com.canglan.survival.core;

import com.canglan.survival.CanglanSurvival;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 内置权限系统（LuckPerms 风格）：组、组继承、组前缀、玩家额外权限节点。
 * 权限会以 Bukkit PermissionAttachment 形式挂到在线玩家身上，因此其它插件也能识别。
 */
public class Perms extends YamlStore {

    public static class Group {
        private final String name;
        private String prefix;
        private int weight;
        private final List<String> permissions;
        private final List<String> parents;

        public Group(String name, String prefix, int weight, List<String> permissions, List<String> parents) {
            this.name = name;
            this.prefix = prefix;
            this.weight = weight;
            this.permissions = permissions;
            this.parents = parents;
        }

        public String name() {
            return name;
        }

        public String prefix() {
            return prefix;
        }

        public int weight() {
            return weight;
        }

        public List<String> permissions() {
            return permissions;
        }

        public List<String> parents() {
            return parents;
        }
    }

    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<UUID, String> userGroup = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> userPerms = new LinkedHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public Perms(CanglanSurvival plugin) {
        super(plugin, "permissions.yml");
        loadData();
    }

    public void loadData() {
        groups.clear();
        userGroup.clear();
        userPerms.clear();
        ConfigurationSection groupSection = yaml.getConfigurationSection("groups");
        if (groupSection != null) {
            for (String name : groupSection.getKeys(false)) {
                String prefix = groupSection.getString(name + ".prefix", "&7");
                int weight = groupSection.getInt(name + ".weight", 0);
                List<String> permissions = groupSection.getStringList(name + ".permissions");
                List<String> parents = groupSection.getStringList(name + ".parents");
                groups.put(name.toLowerCase(Locale.ROOT),
                        new Group(name.toLowerCase(Locale.ROOT), prefix, weight,
                                new ArrayList<>(permissions), new ArrayList<>(parents)));
            }
        }
        ConfigurationSection playerSection = yaml.getConfigurationSection("players");
        if (playerSection != null) {
            for (String key : playerSection.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                String group = playerSection.getString(key + ".group", "default");
                userGroup.put(uuid, group.toLowerCase(Locale.ROOT));
                Set<String> nodes = new LinkedHashSet<>(playerSection.getStringList(key + ".permissions"));
                if (!nodes.isEmpty()) {
                    userPerms.put(uuid, nodes);
                }
            }
        }
        if (groups.isEmpty()) {
            createDefaults();
        }
    }

    private void createDefaults() {
        createGroup("default", "&7[默认] &r", 0);
        createGroup("vip", "&6[&eVIP&6] &r", 10);
        createGroup("staff", "&c[&4管理&c] &r", 100);
        for (String node : plugin.getConfig().getStringList("permissions.default-group-permissions")) {
            addGroupPerm("default", node);
        }
        yaml.set("players.example-uuid.group", "default");
        save();
        plugin.getLogger().info("已生成默认权限组：default、vip、staff。");
    }

    public Collection<Group> groups() {
        return groups.values();
    }

    public Group group(String name) {
        return name == null ? null : groups.get(name.toLowerCase(Locale.ROOT));
    }

    public Group groupOf(UUID uuid) {
        Group group = group(userGroup.get(uuid));
        return group != null ? group : group("default");
    }

    public Group groupOf(Player player) {
        return groupOf(player.getUniqueId());
    }

    public String prefixOf(UUID uuid) {
        Group group = groupOf(uuid);
        return group == null ? "" : Msg.color(group.prefix());
    }

    public void createGroup(String name, String prefix, int weight) {
        String id = name.toLowerCase(Locale.ROOT);
        groups.put(id, new Group(id, prefix, weight, new ArrayList<>(), new ArrayList<>()));
        yaml.set("groups." + id + ".prefix", prefix);
        yaml.set("groups." + id + ".weight", weight);
        yaml.set("groups." + id + ".permissions", new ArrayList<String>());
        yaml.set("groups." + id + ".parents", new ArrayList<String>());
        save();
    }

    public boolean deleteGroup(String name) {
        String id = name.toLowerCase(Locale.ROOT);
        if (id.equals("default")) {
            return false;
        }
        if (!groups.containsKey(id)) {
            return false;
        }
        groups.remove(id);
        yaml.set("groups." + id, null);
        for (Map.Entry<UUID, String> entry : new ArrayList<>(userGroup.entrySet())) {
            if (entry.getValue().equals(id)) {
                setGroup(entry.getKey(), "default", false);
            }
        }
        save();
        return true;
    }

    public void setGroup(UUID uuid, String groupName) {
        setGroup(uuid, groupName, true);
    }

    private void setGroup(UUID uuid, String groupName, boolean save) {
        String id = groupName.toLowerCase(Locale.ROOT);
        if (!groups.containsKey(id)) {
            return;
        }
        userGroup.put(uuid, id);
        yaml.set("players." + uuid + ".group", id);
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null) {
            yaml.set("players." + uuid + ".name", offline.getName());
        }
        if (save) {
            save();
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                apply(online);
            }
        }
    }

    public void addGroupPerm(String groupName, String node) {
        Group group = group(groupName);
        if (group == null) {
            return;
        }
        if (!group.permissions.contains(node)) {
            group.permissions.add(node);
            yaml.set("groups." + group.name() + ".permissions", new ArrayList<>(group.permissions));
            save();
        }
    }

    public void removeGroupPerm(String groupName, String node) {
        Group group = group(groupName);
        if (group == null) {
            return;
        }
        if (group.permissions.remove(node)) {
            yaml.set("groups." + group.name() + ".permissions", new ArrayList<>(group.permissions));
            save();
        }
    }

    public void addUserPerm(UUID uuid, String node) {
        Set<String> nodes = userPerms.computeIfAbsent(uuid, k -> new LinkedHashSet<>());
        if (nodes.add(node)) {
            yaml.set("players." + uuid + ".permissions", new ArrayList<>(nodes));
            save();
            applyIfOnline(uuid);
        }
    }

    public void removeUserPerm(UUID uuid, String node) {
        Set<String> nodes = userPerms.get(uuid);
        if (nodes != null && nodes.remove(node)) {
            yaml.set("players." + uuid + ".permissions", new ArrayList<>(nodes));
            save();
            applyIfOnline(uuid);
        }
    }

    public Set<String> userPerms(UUID uuid) {
        return userPerms.getOrDefault(uuid, Set.of());
    }

    public String groupName(UUID uuid) {
        String name = userGroup.get(uuid);
        return name == null ? "default" : name;
    }

    /** 计算玩家最终权限节点（含组继承、父组、玩家额外节点）。 */
    public Set<String> resolve(UUID uuid) {
        Set<String> nodes = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        collectGroup(groupName(uuid), nodes, visited);
        nodes.addAll(userPerms(uuid));
        return nodes;
    }

    private void collectGroup(String name, Set<String> out, Set<String> visited) {
        if (!visited.add(name)) {
            return;
        }
        Group group = group(name);
        if (group == null) {
            return;
        }
        out.addAll(group.permissions());
        for (String parent : group.parents()) {
            collectGroup(parent, out, visited);
        }
    }

    public void apply(Player player) {
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try {
                player.removeAttachment(old);
            } catch (IllegalArgumentException ignored) {
            }
        }
        Set<String> nodes = resolve(player.getUniqueId());
        if (nodes.isEmpty()) {
            return;
        }
        PermissionAttachment attachment = player.addAttachment(plugin);
        for (String node : nodes) {
            if (node == null || node.isBlank()) {
                continue;
            }
            if (node.startsWith("-")) {
                attachment.setPermission(node.substring(1), false);
            } else {
                attachment.setPermission(node, true);
            }
        }
        attachments.put(player.getUniqueId(), attachment);
    }

    public void detach(Player player) {
        PermissionAttachment attachment = attachments.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void applyIfOnline(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            apply(online);
        }
    }

    /** 兼容 LuckPerms：如果服务器装了 LuckPerms，就不再重复挂载，交给它处理。 */
    public boolean luckPermsPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
    }
}
