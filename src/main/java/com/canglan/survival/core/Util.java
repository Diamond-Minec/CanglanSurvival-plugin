package com.canglan.survival.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 通用工具：时间、数字、坐标、安全传送、随机安全点。
 */
public final class Util {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    private static final DecimalFormat PLAIN = new DecimalFormat("#,##0.##");

    private Util() {
    }

    /** 把秒数格式化为「1天2小时3分」。 */
    public static String time(long seconds) {
        if (seconds <= 0) {
            return "0秒";
        }
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("天");
        }
        if (hours > 0) {
            sb.append(hours).append("小时");
        }
        if (minutes > 0) {
            sb.append(minutes).append("分");
        }
        if (days == 0 && hours == 0 && secs > 0) {
            sb.append(secs).append("秒");
        }
        return sb.toString();
    }

    public static String money(double amount) {
        return MONEY.format(amount);
    }

    public static String plain(double amount) {
        return PLAIN.format(amount);
    }

    public static String loc(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return "未知";
        }
        return loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
    }

    public static String locString(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getX() + ":" + loc.getY() + ":" + loc.getZ()
                + ":" + loc.getYaw() + ":" + loc.getPitch();
    }

    public static Location parseLoc(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(":");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean safeTeleport(org.bukkit.entity.Player player, Location target) {
        if (target == null || target.getWorld() == null) {
            return false;
        }
        target.getChunk().load();
        return player.teleport(target);
    }

    /** 找一块可以站立的安全方块，找不到返回 null。 */
    public static Location safeSpot(World world, int x, int z) {
        if (world == null) {
            return null;
        }
        int maxY = world.getMaxHeight();
        int minY = world.getMinHeight();
        int startY = Math.min(maxY - 2, world.getHighestBlockYAt(x, z));
        for (int y = startY; y > minY + 1; y--) {
            Block ground = world.getBlockAt(x, y, z);
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);
            if (!ground.getType().isSolid()) {
                continue;
            }
            Material groundType = ground.getType();
            if (groundType == Material.LAVA || groundType == Material.MAGMA_BLOCK
                    || groundType == Material.CACTUS || groundType == Material.CAMPFIRE
                    || groundType == Material.SOUL_CAMPFIRE || groundType == Material.POWDER_SNOW
                    || groundType == Material.WATER) {
                continue;
            }
            if (feet.getType() == Material.WATER || feet.getType() == Material.LAVA
                    || head.getType() == Material.WATER || head.getType() == Material.LAVA) {
                continue;
            }
            if (!feet.isPassable() || !head.isPassable()) {
                continue;
            }
            return new Location(world, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }

    /** 在半径内找随机安全点。 */
    public static Location randomSafe(World world, int centerX, int centerZ, int minRadius, int maxRadius) {
        for (int attempts = 0; attempts < 24; attempts++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            int radius = ThreadLocalRandom.current().nextInt(minRadius, maxRadius + 1);
            int x = centerX + (int) Math.round(Math.cos(angle) * radius);
            int z = centerZ + (int) Math.round(Math.sin(angle) * radius);
            Location spot = safeSpot(world, x, z);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /** 支持「1d2h3m4s」写法的时间解析，返回秒。 */
    public static long parseDuration(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        long total = 0;
        StringBuilder num = new StringBuilder();
        for (char c : raw.toLowerCase().toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
                continue;
            }
            if (num.length() == 0) {
                continue;
            }
            long value = Long.parseLong(num.toString());
            num.setLength(0);
            switch (c) {
                case 'd' -> total += value * 86400;
                case 'h' -> total += value * 3600;
                case 'm' -> total += value * 60;
                case 's' -> total += value;
                default -> {
                }
            }
        }
        if (num.length() > 0) {
            total += Long.parseLong(num.toString());
        }
        return total > 0 ? total : fallback;
    }

    public static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.split("\n")) {
            out.add(part);
        }
        return out;
    }

    public static String join(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    public static String join(String[] args, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end && i < args.length; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    /** 简单分页，返回当前页文本与总页数。 */
    public static List<String> page(List<String> all, int page, int perPage) {
        List<String> out = new ArrayList<>();
        int total = Math.max(1, (all.size() + perPage - 1) / perPage);
        int fixed = Math.max(1, Math.min(page, total));
        int from = (fixed - 1) * perPage;
        int to = Math.min(all.size(), from + perPage);
        for (int i = from; i < to; i++) {
            out.add(all.get(i));
        }
        out.add("&8第 &f" + fixed + "&8/&f" + total + " &8页");
        return out;
    }

    public static int pageCount(List<?> all, int perPage) {
        return Math.max(1, (all.size() + perPage - 1) / perPage);
    }
}
