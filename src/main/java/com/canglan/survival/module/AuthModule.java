package com.canglan.survival.module;

import com.canglan.survival.CanglanSurvival;
import com.canglan.survival.core.Module;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Util;
import com.canglan.survival.core.YamlStore;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 模块 15 · 登录验证
 * 离线服必备：/register 注册、/login 登录，未登录时不能移动、聊天、交互，超时自动踢出。
 * 密码使用 SHA-256 + 随机盐保存，不保存明文。
 */
public class AuthModule extends Module {

    private final YamlStore store;
    private final Set<UUID> authed = new HashSet<>();
    private final Set<UUID> frozen = new HashSet<>();
    private final Map<UUID, Location> lastPosition = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    private final Listener authListener = new Listener() {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            if (player.hasPermission("canglan.auth.bypass")) {
                authed.add(player.getUniqueId());
                return;
            }
            boolean registered = store.yaml().isSet("accounts." + player.getUniqueId() + ".hash");
            lastPosition.put(player.getUniqueId(), player.getLocation());
            frozen.add(player.getUniqueId());
            Msg.title(player, "&b&l沧蓝工坊", registered ? "&f请输入 &b/login 密码 &f登录" : "&f请输入 &b/register 密码 确认密码 &f注册", 10, 100, 20);
            Msg.send(player, registered
                    ? "&e欢迎回来！请先登录：&f/login <密码>"
                    : "&e欢迎新玩家！请先注册：&f/register <密码> <确认密码>");
            Msg.send(player, "&7登录前无法移动、聊天和交互，&c60 秒内未登录将被踢出。");
            int timeout = Math.max(15, plugin.getConfig().getInt("auth.timeout-seconds", 60));
            later(timeout * 20L, () -> {
                if (frozen.contains(player.getUniqueId())) {
                    player.kickPlayer(Msg.color("&c登录超时，请在规定时间内输入 /login 或 /register。"));
                }
            });
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            UUID uuid = event.getPlayer().getUniqueId();
            authed.remove(uuid);
            frozen.remove(uuid);
            lastPosition.remove(uuid);
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onMove(PlayerMoveEvent event) {
            if (!frozen.contains(event.getPlayer().getUniqueId())) {
                return;
            }
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) {
                return;
            }
            if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY()
                    || from.getBlockZ() != to.getBlockZ()) {
                event.setTo(from);
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onChat(AsyncChatEvent event) {
            if (frozen.contains(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                Msg.send(event.getPlayer(), "&c请先登录后再聊天：&f/login <密码>");
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onCommand(PlayerCommandPreprocessEvent event) {
            if (!frozen.contains(event.getPlayer().getUniqueId())) {
                return;
            }
            String message = event.getMessage().toLowerCase(Locale.ROOT);
            if (message.startsWith("/login") || message.startsWith("/register")
                    || message.startsWith("/changepassword") || message.startsWith("/登录")
                    || message.startsWith("/注册")) {
                return;
            }
            event.setCancelled(true);
            Msg.send(event.getPlayer(), "&c登录前不能使用其它指令：&f/login <密码> 或 &f/register <密码> <确认密码>");
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onDamage(EntityDamageEvent event) {
            if (event.getEntity() instanceof Player player && frozen.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onInteract(PlayerInteractEvent event) {
            if (frozen.contains(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            if (frozen.contains(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onPlace(BlockPlaceEvent event) {
            if (frozen.contains(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
            }
        }
    };

    public AuthModule(CanglanSurvival plugin) {
        super(plugin, "auth", "登录验证",
                "离线服登录系统：/register 注册、/login 登录、/changepassword 改密码，未登录不能移动与交互。",
                true, "register", "login", "changepassword");
        this.store = new YamlStore(plugin, "auth.yml");
    }

    @Override
    protected void onEnable() {
        listen(authListener);
    }

    @Override
    protected void onDisable() {
        frozen.clear();
        authed.clear();
        lastPosition.clear();
    }

    @Override
    public void reload() {
        store.load();
    }

    @Override
    public boolean onCommand(CommandSender sender, String command, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        switch (command.toLowerCase(Locale.ROOT)) {
            case "register", "注册" -> register(player, args);
            case "login", "登录" -> login(player, args);
            case "changepassword", "改密码" -> changePassword(player, args);
            default -> Msg.send(sender, "&c用法：/register <密码> <确认密码>、/login <密码>");
        }
        return true;
    }

    private void register(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        if (store.yaml().isSet("accounts." + uuid + ".hash")) {
            Msg.send(player, "&c你已经注册过了，请直接 &f/login <密码> &c登录。");
            return;
        }
        if (args.length < 2) {
            Msg.send(player, "&c用法：/register <密码> <再次输入密码>");
            return;
        }
        if (!args[0].equals(args[1])) {
            Msg.send(player, "&c两次输入的密码不一致，请重新输入。");
            return;
        }
        int min = Math.max(4, plugin.getConfig().getInt("auth.min-password-length", 4));
        if (args[0].length() < min) {
            Msg.send(player, "&c密码至少需要 &f" + min + " &c位。");
            return;
        }
        String salt = randomSalt();
        store.yaml().set("accounts." + uuid + ".hash", hash(salt, args[0]));
        store.yaml().set("accounts." + uuid + ".salt", salt);
        store.yaml().set("accounts." + uuid + ".registered", System.currentTimeMillis());
        store.yaml().set("accounts." + uuid + ".name", player.getName());
        store.save();
        authed.add(uuid);
        frozen.remove(uuid);
        Msg.send(player, "&a注册成功！欢迎加入服务器，祝你玩得开心。");
        Msg.title(player, "&a&l注册成功", "&f欢迎来到沧蓝工坊", 10, 40, 10);
    }

    private void login(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        if (authed.contains(uuid)) {
            Msg.send(player, "&a你已经登录过了。");
            return;
        }
        if (!store.yaml().isSet("accounts." + uuid + ".hash")) {
            Msg.send(player, "&c你还没有注册，请先 &f/register <密码> <确认密码>");
            return;
        }
        if (args.length < 1) {
            Msg.send(player, "&c用法：/login <密码>");
            return;
        }
        String salt = store.yaml().getString("accounts." + uuid + ".salt", "");
        String stored = store.yaml().getString("accounts." + uuid + ".hash", "");
        if (!stored.equals(hash(salt, args[0]))) {
            Msg.send(player, "&c密码错误，请重试。");
            return;
        }
        authed.add(uuid);
        frozen.remove(uuid);
        Msg.send(player, "&a登录成功，祝你玩得开心！");
        Msg.title(player, "&a&l登录成功", "&f欢迎回来", 10, 40, 10);
    }

    private void changePassword(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        if (args.length < 2) {
            Msg.send(player, "&c用法：/changepassword <旧密码> <新密码>");
            return;
        }
        if (!authed.contains(uuid)) {
            Msg.send(player, "&c请先登录后再修改密码。");
            return;
        }
        String salt = store.yaml().getString("accounts." + uuid + ".salt", "");
        String stored = store.yaml().getString("accounts." + uuid + ".hash", "");
        if (!stored.equals(hash(salt, args[0]))) {
            Msg.send(player, "&c旧密码不正确。");
            return;
        }
        int min = Math.max(4, plugin.getConfig().getInt("auth.min-password-length", 4));
        if (args[1].length() < min) {
            Msg.send(player, "&c新密码至少需要 &f" + min + " &c位。");
            return;
        }
        String newSalt = randomSalt();
        store.yaml().set("accounts." + uuid + ".hash", hash(newSalt, args[1]));
        store.yaml().set("accounts." + uuid + ".salt", newSalt);
        store.save();
        Msg.send(player, "&a密码修改成功，请牢记新密码。");
    }

    private String randomSalt() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String hash(String salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("服务器不支持 SHA-256", e);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String command, String[] args) {
        if (command.equalsIgnoreCase("login")) {
            return args.length == 1 ? List.of("<密码>") : List.of();
        }
        return List.of();
    }
}
