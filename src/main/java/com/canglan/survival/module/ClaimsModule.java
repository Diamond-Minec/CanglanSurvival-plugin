package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import com.canglan.survival.core.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 模块 07 · 领地保护（立体领地）。
 * 支持用两个对角点圈出立体的领地，单边最大 64 格、最高 64 格，
 * 领地内禁止他人破坏 / 放置 / 开箱子 / 倒水 / 爆炸破坏 / PvP，可信任好友。
 */
public class ClaimsModule extends Module {

    /** 单边最大格数（X / Z 方向）。 */
    private static final int DEFAULT_MAX_SIZE = 64;
    /** 高度上限。 */
    private static final int DEFAULT_MAX_HEIGHT = 64;

    private final YamlStore store;
    private final List<Claim> claims = new ArrayList<>();
    private final Map<UUID, Location[]> selections = new HashMap<>();

    private final Listener protection = new Listener() {

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            if (denied(event.getPlayer(), event.getBlock().getLocation())) {
                event.setCancelled(true);
                Msg.send(event.getPlayer(), "&c这里是别人的领地，你不能破坏方块。");
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onPlace(BlockPlaceEvent event) {
            if (denied(event.getPlayer(), event.getBlock().getLocation())) {
                event.setCancelled(true);
                Msg.send(event.getPlayer(), "&c这里是别人的领地，你不能放置方块。");
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onInteract(PlayerInteractEvent event) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
                return;
            }
            Block block = event.getClickedBlock();
            if (block == null || !isProtectedBlock(block)) {
                return;
            }
            if (denied(event.getPlayer(), block.getLocation())) {
                event.setCancelled(true);
                Msg.send(event.getPlayer(), "&c这里是别人的领地，你不能使用这里的设施。");
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onBucketEmpty(PlayerBucketEmptyEvent event) {
            if (plugin.getConfig().getBoolean("claims.protection.buckets", true)
                    && denied(event.getPlayer(), event.getBlock().getLocation())) {
                event.setCancelled(true);
                Msg.send(event.getPlayer(), "&c这里是别人的领地，不能倒液体。");
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onBucketFill(PlayerBucketFillEvent event) {
            if (plugin.getConfig().getBoolean("claims.protection.buckets", true)
                    && denied(event.getPlayer(), event.getBlock().getLocation())) {
                event.setCancelled(true);
                Msg.send(event.getPlayer(), "&c这里是别人的领地，不能取液体。");
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onDamage(EntityDamageByEntityEvent event) {
            if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
                return;
            }
            if (plugin.getConfig().getBoolean("claims.pvp-in-claims", false)) {
                return;
            }
            if (claimAt(victim.getLocation()) != null || claimAt(attacker.getLocation()) != null) {
                event.setCancelled(true);
                Msg.send(attacker, "&c领地内禁止 PvP。");
            }
        }

        /** 爆炸不破坏领地内的方块。 */
        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onExplode(EntityExplodeEvent event) {
            if (!plugin.getConfig().getBoolean("claims.protection.explosions", true)) {
                return;
            }
            event.blockList().removeIf(block -> claimAt(block.getLocation()) != null);
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onBlockExplode(BlockExplodeEvent event) {
            if (!plugin.getConfig().getBoolean("claims.protection.explosions", true)) {
                return;
            }
            event.blockList().removeIf(block -> claimAt(block.getLocation()) != null);
        }

        /** 别人领地里的画、物品展示框不能被破坏。 */
        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onHangingBreak(HangingBreakByEntityEvent event) {
            if (!(event.getRemover() instanceof Player player)) {
                return;
            }
            if (denied(player, event.getEntity().getLocation())) {
                event.setCancelled(true);
                Msg.send(player, "&c这里是别人的领地，你不能破坏这里的装饰。");
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            selections.remove(event.getPlayer().getUniqueId());
        }
    };

    public ClaimsModule(CanglanSurvival plugin) {
        super(plugin, "claims", "领地保护",
                "立体领地系统：用 /claim pos1、/claim pos2 选两个对角点后 /claim create 圈地，"
                        + "单块最大 64×64×64；领地内禁止他人破坏、放置、开箱子、倒液体、爆炸破坏与 PvP。",
                true, "claim", "unclaim");
        this.store = new YamlStore(plugin, "claims.yml");
    }

    @Override
    protected void onEnable() {
        loadClaims();
        listen(protection);
    }

    @Override
    protected void onDisable() {
        store.save();
    }

    @Override
    public void reload() {
        store.load();
        loadClaims();
    }

    private int maxSize() {
        return Math.max(1, Math.min(256, plugin.getConfig().getInt("claims.max-size", DEFAULT_MAX_SIZE)));
    }

    private int maxHeight() {
        return Math.max(1, Math.min(256, plugin.getConfig().getInt("claims.max-height", DEFAULT_MAX_HEIGHT)));
    }

    // ------------------------------------------------------------------
    // 领地数据
    // ------------------------------------------------------------------

    private static final class Claim {
        private String id;
        private String world;
        private UUID owner;
        private String ownerName;
        private String name;
        private int minX;
        private int minY;
        private int minZ;
        private int maxX;
        private int maxY;
        private int maxZ;
        private final Set<UUID> trusted = new LinkedHashSet<>();

        private int sizeX() {
            return maxX - minX + 1;
        }

        private int sizeY() {
            return maxY - minY + 1;
        }

        private int sizeZ() {
            return maxZ - minZ + 1;
        }

        private long volume() {
            return (long) sizeX() * sizeY() * sizeZ();
        }

        private boolean contains(String world, int x, int y, int z) {
            return this.world.equals(world) && x >= minX && x <= maxX && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }

        private boolean intersects(Claim other) {
            return world.equals(other.world)
                    && minX <= other.maxX && maxX >= other.minX
                    && minY <= other.maxY && maxY >= other.minY
                    && minZ <= other.maxZ && maxZ >= other.minZ;
        }

        private String describe() {
            String label = name == null || name.isBlank() ? "未命名领地" : name;
            return label + " &7(" + sizeX() + "×" + sizeY() + "×" + sizeZ() + "，主人 " + ownerName + ")";
        }
    }

    private void loadClaims() {
        claims.clear();
        ConfigurationSection section = store.yaml().getConfigurationSection("claims");
        if (section == null) {
            return;
        }
        boolean migrated = false;
        for (String id : new ArrayList<>(section.getKeys(false))) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            // 旧版按区块存的数据：world;chunkX;chunkZ → 转成立体领地
            if (!s.isSet("min-x")) {
                String[] parts = id.split(";");
                if (parts.length != 3) {
                    continue;
                }
                int chunkX;
                int chunkZ;
                try {
                    chunkX = Integer.parseInt(parts[1]);
                    chunkZ = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    continue;
                }
                s.set("world", parts[0]);
                s.set("min-x", chunkX * 16);
                s.set("max-x", chunkX * 16 + 15);
                s.set("min-z", chunkZ * 16);
                s.set("max-z", chunkZ * 16 + 15);
                s.set("min-y", s.getInt("min-y", -64));
                s.set("max-y", s.getInt("max-y", 319));
                s.set("name", s.getString("name", "旧版区块领地"));
                migrated = true;
            }
            String rawOwner = s.getString("owner");
            if (rawOwner == null) {
                continue;
            }
            Claim claim = new Claim();
            claim.id = id;
            claim.world = s.getString("world", "world");
            try {
                claim.owner = UUID.fromString(rawOwner);
            } catch (IllegalArgumentException e) {
                continue;
            }
            claim.ownerName = s.getString("owner-name", "未知");
            claim.name = s.getString("name", "未命名领地");
            claim.minX = s.getInt("min-x");
            claim.minY = s.getInt("min-y");
            claim.minZ = s.getInt("min-z");
            claim.maxX = s.getInt("max-x");
            claim.maxY = s.getInt("max-y");
            claim.maxZ = s.getInt("max-z");
            for (String trusted : s.getStringList("trusted")) {
                try {
                    claim.trusted.add(UUID.fromString(trusted));
                } catch (IllegalArgumentException ignored) {
                }
            }
            claims.add(claim);
        }
        if (migrated) {
            saveClaims();
            plugin.getLogger().info("已把旧的区块领地数据升级为立体领地（" + claims.size() + " 块）。");
        }
    }

    private void saveClaims() {
        ConfigurationSection section = store.yaml().getConfigurationSection("claims");
        if (section != null) {
            for (String key : new ArrayList<>(section.getKeys(false))) {
                section.set(key, null);
            }
        }
        for (Claim claim : claims) {
            String base = "claims." + claim.id;
            store.yaml().set(base + ".world", claim.world);
            store.yaml().set(base + ".owner", claim.owner.toString());
            store.yaml().set(base + ".owner-name", claim.ownerName);
            store.yaml().set(base + ".name", claim.name);
            store.yaml().set(base + ".min-x", claim.minX);
            store.yaml().set(base + ".min-y", claim.minY);
            store.yaml().set(base + ".min-z", claim.minZ);
            store.yaml().set(base + ".max-x", claim.maxX);
            store.yaml().set(base + ".max-y", claim.maxY);
            store.yaml().set(base + ".max-z", claim.maxZ);
            store.yaml().set(base + ".trusted", claim.trusted.stream().map(UUID::toString).toList());
        }
        store.save();
    }

    private Claim claimAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        String world = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (Claim claim : claims) {
            if (claim.contains(world, x, y, z)) {
                return claim;
            }
        }
        return null;
    }

    private boolean denied(Player player, Location location) {
        if (player.hasPermission("canglan.claim.bypass")) {
            return false;
        }
        Claim claim = claimAt(location);
        if (claim == null || claim.owner.equals(player.getUniqueId())) {
            return false;
        }
        return !claim.trusted.contains(player.getUniqueId());
    }

    private boolean isProtectedBlock(Block block) {
        if (block.getState() instanceof Container) {
            return plugin.getConfig().getBoolean("claims.protection.containers", true);
        }
        String name = block.getType().name();
        return name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR") || name.endsWith("_FENCE_GATE")
                || name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE")
                || name.equals("LEVER") || name.endsWith("_BED")
                || name.equals("ANVIL") || name.equals("CHIPPED_ANVIL") || name.equals("DAMAGED_ANVIL")
                || name.equals("CRAFTING_TABLE") || name.equals("ENCHANTING_TABLE")
                || name.equals("BREWING_STAND") || name.equals("BEACON") || name.equals("RESPAWN_ANCHOR")
                || name.equals("JUKEBOX") || name.equals("LECTERN") || name.equals("LOOM")
                || name.equals("SMITHING_TABLE") || name.equals("STONECUTTER") || name.equals("CARTOGRAPHY_TABLE")
                || name.equals("GRINDSTONE") || name.equals("BARREL") || name.equals("HOPPER");
    }

    // ------------------------------------------------------------------
    // 指令
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        if (command.equalsIgnoreCase("unclaim")) {
            unclaim(player);
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pos1", "p1", "点1", "一号点" -> pos(player, 1);
            case "pos2", "p2", "点2", "二号点" -> pos(player, 2);
            case "create", "confirm", "创建", "确认" -> create(player, args);
            case "auto", "quick", "快速", "一键" -> quick(player);
            case "list", "列表", "我的领地" -> list(player);
            case "info", "信息", "查看" -> info(player);
            case "trust", "信任" -> trust(player, args, true);
            case "untrust", "取消信任" -> trust(player, args, false);
            case "delete", "remove", "删除", "取消" -> unclaim(player);
            case "rename", "改名", "重命名" -> rename(player, args);
            case "help", "帮助" -> help(player);
            default -> help(player);
        }
        return true;
    }

    private void help(Player player) {
        Msg.header(player, "&b&l领地保护 &7最大 " + maxSize() + "×" + maxHeight() + "×" + maxSize());
        Msg.raw(player, "&f/claim pos1 &8- &7把当前位置设为第一个角点");
        Msg.raw(player, "&f/claim pos2 &8- &7把当前位置设为第二个角点");
        Msg.raw(player, "&f/claim create <名字> &8- &7把选好的范围圈成领地");
        Msg.raw(player, "&f/claim auto &8- &7一键圈地下这个区块（16×16，高 64）");
        Msg.raw(player, "&f/claim info &8- &7查看脚下领地信息");
        Msg.raw(player, "&f/claim list &8- &7查看你的全部领地");
        Msg.raw(player, "&f/claim trust <玩家> &8- &7信任好友，让 TA 在你领地活动");
        Msg.raw(player, "&f/claim untrust <玩家> &8- &7取消信任");
        Msg.raw(player, "&f/claim rename <新名字> &8- &7给脚下的领地改名");
        Msg.raw(player, "&f/unclaim &8- &7删除脚下的领地");
        Msg.raw(player, "&8每块领地单边最长 &f" + maxSize() + " &8格、最高 &f" + maxHeight()
                + " &8格；你最多能圈 &f" + limit(player) + " &8块。");
    }

    private void pos(Player player, int index) {
        Location location = player.getLocation();
        Location[] selection = selections.computeIfAbsent(player.getUniqueId(), k -> new Location[2]);
        selection[index - 1] = location.clone();
        Msg.send(player, "&a已设置第 &f" + index + " &a个角点：&f" + Util.locString(location));
        if (selection[0] != null && selection[1] != null) {
            Msg.send(player, "&7两个点都选好了，输入 &f/claim create <名字> &7圈地。");
            sendSizeHint(player, selection);
        } else {
            Msg.send(player, "&7再去另一个对角位置输入 &f/claim pos" + (index == 1 ? 2 : 1) + "&7。");
        }
    }

    private void sendSizeHint(Player player, Location[] selection) {
        Location a = selection[0];
        Location b = selection[1];
        if (a == null || b == null || a.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            Msg.send(player, "&c两个角点必须在同一个世界。");
            return;
        }
        int sx = Math.abs(a.getBlockX() - b.getBlockX()) + 1;
        int sy = Math.abs(a.getBlockY() - b.getBlockY()) + 1;
        int sz = Math.abs(a.getBlockZ() - b.getBlockZ()) + 1;
        String color = (sx > maxSize() || sz > maxSize() || sy > maxHeight()) ? "&c" : "&a";
        Msg.send(player, "&7当前范围：" + color + sx + "×" + sy + "×" + sz
                + " &7（上限 " + maxSize() + "×" + maxHeight() + "×" + maxSize() + "）");
    }

    private void create(Player player, String[] args) {
        Location[] selection = selections.get(player.getUniqueId());
        if (selection == null || selection[0] == null || selection[1] == null) {
            Msg.send(player, "&c请先用 &f/claim pos1 &c和 &f/claim pos2 &c选两个对角点（或者用 &f/claim auto&c）。");
            return;
        }
        Location a = selection[0];
        Location b = selection[1];
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            Msg.send(player, "&c两个角点必须在同一个世界。");
            return;
        }
        String name = args.length >= 2 ? Util.join(args, 1) : "我的领地";
        createClaim(player, a.getWorld(), a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ(), name, true);
    }

    private void quick(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        int minX = location.getBlockX() >> 4 << 4;
        int minZ = location.getBlockZ() >> 4 << 4;
        int halfUp = Math.max(0, Math.min(32, maxHeight() / 2));
        int halfDown = maxHeight() - halfUp - 1;
        int centerY = location.getBlockY();
        int minY = Math.max(world.getMinHeight(), centerY - halfDown);
        int maxY = Math.min(world.getMaxHeight() - 1, centerY + halfUp);
        createClaim(player, world, minX, minY, minZ, minX + 15, maxY, minZ + 15, "快速领地", false);
    }

    private void createClaim(Player player, World world, int x1, int y1, int z1,
                            int x2, int y2, int z2, String name, boolean fromSelection) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        if (plugin.getConfig().getStringList("claims.blocked-worlds").contains(world.getName())) {
            Msg.send(player, "&c这个世界不能圈地。");
            return;
        }
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        int maxSize = maxSize();
        int maxHeight = maxHeight();
        if (sizeX > maxSize || sizeZ > maxSize) {
            Msg.send(player, "&c领地太宽了：&f" + sizeX + "×" + sizeZ
                    + "&c，单边最多 &f" + maxSize + " &c格。");
            return;
        }
        if (sizeY > maxHeight) {
            Msg.send(player, "&c领地太高了：&f" + sizeY + "&c，最高 &f" + maxHeight + " &c格。");
            return;
        }
        int maxBlocks = plugin.getConfig().getInt("claims.max-blocks", 64 * 64 * 64);
        long volume = (long) sizeX * sizeY * sizeZ;
        if (volume > maxBlocks) {
            Msg.send(player, "&c领地体积 &f" + volume + " &c超过上限 &f" + maxBlocks + " &c格。");
            return;
        }

        Claim probe = new Claim();
        probe.world = world.getName();
        probe.minX = minX;
        probe.minY = minY;
        probe.minZ = minZ;
        probe.maxX = maxX;
        probe.maxY = maxY;
        probe.maxZ = maxZ;
        for (Claim other : claims) {
            if (probe.intersects(other)) {
                Msg.send(player, "&c这个范围与 &f" + other.ownerName + " &c的领地重叠了（&f"
                        + other.describe() + "&c），换个位置或先缩小范围。");
                return;
            }
        }

        List<Claim> own = ownClaims(player);
        int limit = limit(player);
        if (own.size() >= limit) {
            Msg.send(player, "&c你最多只能拥有 &f" + limit + " &c块领地，当前已有 &f" + own.size() + "&c 块。");
            return;
        }

        Claim claim = new Claim();
        claim.id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        claim.world = world.getName();
        claim.owner = player.getUniqueId();
        claim.ownerName = player.getName();
        claim.name = name == null || name.isBlank() ? "我的领地" : name;
        claim.minX = minX;
        claim.minY = minY;
        claim.minZ = minZ;
        claim.maxX = maxX;
        claim.maxY = maxY;
        claim.maxZ = maxZ;
        claims.add(claim);
        saveClaims();
        if (fromSelection) {
            selections.remove(player.getUniqueId());
        }
        Msg.send(player, "&a领地创建成功！&f" + claim.name + " &7共 &f" + sizeX + "×" + sizeY + "×" + sizeZ
                + " &7（" + volume + " 格），你现有 &f" + (own.size() + 1) + "/" + limit + " &7块。");
        Msg.send(player, "&7范围：&fX " + minX + "~" + maxX + " &7| &fY " + minY + "~" + maxY
                + " &7| &fZ " + minZ + "~" + maxZ);
        Msg.send(player, "&7别人在里面不能破坏、放方块、开箱子、倒液体；用 &f/claim trust <玩家>&7 可以放行好友。");
    }

    private void unclaim(Player player) {
        Claim claim = claimAt(player.getLocation());
        if (claim == null) {
            Msg.send(player, "&c你脚下不是任何人的领地。");
            return;
        }
        if (!claim.owner.equals(player.getUniqueId()) && !player.hasPermission("canglan.claim.bypass")) {
            Msg.send(player, "&c这不是你的领地（主人：&f" + claim.ownerName + "&c）。");
            return;
        }
        claims.remove(claim);
        saveClaims();
        Msg.send(player, "&a已删除领地 &f" + claim.name + "&a。");
    }

    private void list(Player player) {
        List<Claim> own = ownClaims(player);
        Msg.header(player, "&b&l我的领地 &7共 " + own.size() + " / " + limit(player) + " 块");
        if (own.isEmpty()) {
            Msg.raw(player, "&7你还没有领地。用 &f/claim pos1&7、&f/claim pos2&7 选点后 &f/claim create <名字>&7 圈地。");
            return;
        }
        for (Claim claim : own) {
            Msg.raw(player, "&8- &f" + claim.name + " &8| &7世界 &f" + claim.world
                    + " &8| &7范围 &f" + claim.sizeX() + "×" + claim.sizeY() + "×" + claim.sizeZ());
            Msg.raw(player, "&8  &7X " + claim.minX + "~" + claim.maxX
                    + " &8| &7Y " + claim.minY + "~" + claim.maxY
                    + " &8| &7Z " + claim.minZ + "~" + claim.maxZ
                    + " &8| &7信任 &f" + claim.trusted.size());
        }
    }

    private void info(Player player) {
        Claim claim = claimAt(player.getLocation());
        Msg.header(player, "&b&l领地信息");
        if (claim == null) {
            Msg.raw(player, "&7状态：&a无主之地，可以圈占。");
            Msg.raw(player, "&7选点：&f/claim pos1 &7→ &f/claim pos2 &7→ &f/claim create <名字>");
            return;
        }
        Msg.raw(player, "&7名称：&f" + claim.name);
        Msg.raw(player, "&7主人：&f" + claim.ownerName);
        Msg.raw(player, "&7世界：&f" + claim.world);
        Msg.raw(player, "&7范围：&f" + claim.sizeX() + "×" + claim.sizeY() + "×" + claim.sizeZ()
                + " &7（" + claim.volume() + " 格）");
        Msg.raw(player, "&7X &f" + claim.minX + "~" + claim.maxX + " &7| Y &f" + claim.minY + "~"
                + claim.maxY + " &7| Z &f" + claim.minZ + "~" + claim.maxZ);
        Msg.raw(player, "&7信任玩家：&f" + claim.trusted.size() + " 人");
        Msg.raw(player, "&7你的身份：" + (claim.owner.equals(player.getUniqueId()) ? "&a领地主人"
                : claim.trusted.contains(player.getUniqueId()) ? "&b信任好友" : "&7访客"));
    }

    private void rename(Player player, String[] args) {
        Claim claim = claimAt(player.getLocation());
        if (claim == null || !claim.owner.equals(player.getUniqueId())) {
            Msg.send(player, "&c你脚下不是你的领地，无法改名。");
            return;
        }
        if (args.length < 2) {
            Msg.send(player, "&c用法：&f/claim rename <新名字>");
            return;
        }
        String name = Util.join(args, 1);
        if (name.length() > 16) {
            Msg.send(player, "&c名字太长了，最多 16 个字。");
            return;
        }
        claim.name = name;
        saveClaims();
        Msg.send(player, "&a领地已改名为 &f" + name + "&a。");
    }

    private void trust(Player player, String[] args, boolean add) {
        if (args.length < 2) {
            Msg.send(player, "&c用法：&f/claim " + (add ? "trust" : "untrust") + " <玩家>");
            return;
        }
        Claim claim = claimAt(player.getLocation());
        if (claim == null || !claim.owner.equals(player.getUniqueId())) {
            Msg.send(player, "&c你脚下不是你的领地，无法设置信任。");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            Msg.send(player, "&c没有找到玩家：&f" + args[1]);
            return;
        }
        if (add) {
            claim.trusted.add(target.getUniqueId());
            Msg.send(player, "&a已信任 &f" + target.getName() + "&a，TA 可以在这块领地活动。");
        } else {
            claim.trusted.remove(target.getUniqueId());
            Msg.send(player, "&a已取消对 &f" + target.getName() + " &a的信任。");
        }
        saveClaims();
    }

    private int limit(Player player) {
        int fallback = plugin.getConfig().getInt("claims.default-limit", 1);
        for (int i = 50; i >= 1; i--) {
            if (player.hasPermission("canglan.claim.limit." + i)) {
                return i;
            }
        }
        return Math.max(1, fallback);
    }

    private List<Claim> ownClaims(Player player) {
        List<Claim> list = new ArrayList<>();
        for (Claim claim : claims) {
            if (claim.owner.equals(player.getUniqueId())) {
                list.add(claim);
            }
        }
        return list;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String option : List.of("pos1", "pos2", "create", "auto", "info", "list",
                    "trust", "untrust", "rename", "delete", "help", "信息", "列表", "信任", "取消信任")) {
                if (option.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust")
                || args[0].equals("信任") || args[0].equals("取消信任"))) {
            List<String> out = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(player.getName());
                }
            }
            return out;
        }
        return List.of();
    }
}
