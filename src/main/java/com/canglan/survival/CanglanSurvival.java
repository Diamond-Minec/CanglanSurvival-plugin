package com.canglan.survival;

import com.canglan.survival.core.CommandAliases;
import com.canglan.survival.core.CommandRouter;
import com.canglan.survival.core.CoreListener;
import com.canglan.survival.core.Economy;
import com.canglan.survival.core.GuiListener;
import com.canglan.survival.core.ModuleManager;
import com.canglan.survival.core.Msg;
import com.canglan.survival.core.Mutes;
import com.canglan.survival.core.Perms;
import com.canglan.survival.core.Stats;
import com.canglan.survival.module.AuthModule;
import com.canglan.survival.module.ChatModule;
import com.canglan.survival.module.ClaimsModule;
import com.canglan.survival.module.EconomyModule;
import com.canglan.survival.module.HomesModule;
import com.canglan.survival.module.MenuModule;
import com.canglan.survival.module.MonitorModule;
import com.canglan.survival.module.NoticeModule;
import com.canglan.survival.module.PermModule;
import com.canglan.survival.module.PunishModule;
import com.canglan.survival.module.ReportModule;
import com.canglan.survival.module.RtpModule;
import com.canglan.survival.module.ScoreboardModule;
import com.canglan.survival.module.ShopModule;
import com.canglan.survival.module.SigninModule;
import com.canglan.survival.module.StatsModule;
import com.canglan.survival.module.TabModule;
import com.canglan.survival.module.TopModule;
import com.canglan.survival.module.TpaModule;
import com.canglan.survival.module.WarpsModule;
import com.canglan.survival.module.WelcomeModule;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * 沧蓝工坊 · 整合生存服务端核心（Paper 1.21.11）
 * 把生存服常用的 20 个功能模块整合进一个插件包，
 * 服主可以在游戏内用 /cl 指令随时启用或禁用任意模块，并可给单个玩家单独授权。
 *
 * @author 沧蓝工坊
 */
public final class CanglanSurvival extends JavaPlugin {

    public static final String AUTHOR = "沧蓝工坊";
    public static final String VERSION = "1.0.0";

    private static CanglanSurvival instance;

    private ModuleManager moduleManager;
    private Perms perms;
    private Economy economy;
    private Stats stats;
    private Mutes mutes;
    private BukkitTask autosaveTask;

    public static CanglanSurvival get() {
        return instance;
    }

    /** 只影响展示的默认服务器名称。 */
    public static final String DEFAULT_SERVER_NAME = "&b&l沧蓝工坊 &f生存服";

    /** 当前服务器名称（可含 & 颜色代码）。 */
    public String serverName() {
        String raw = getConfig().getString("server-name", DEFAULT_SERVER_NAME);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SERVER_NAME;
        }
        return raw;
    }

    /** 纯文字版服务器名称（去掉颜色代码）。 */
    public String plainServerName() {
        return Msg.strip(Msg.color(serverName()));
    }

    /** 修改服务器名称并保存到 config.yml。 */
    public void setServerName(String name) {
        getConfig().set("server-name", name);
        saveConfig();
    }

    @Override
    public void onEnable() {
        instance = this;
        long started = System.currentTimeMillis();
        saveDefaultConfig();
        Msg.prefix(getConfig().getString("prefix", "&b&l沧蓝工坊 &7» &f"));
        migrateConfig();

        this.perms = new Perms(this);
        this.economy = new Economy(this);
        this.stats = new Stats(this);
        this.mutes = new Mutes(this);
        this.moduleManager = new ModuleManager(this);

        registerModules();
        moduleManager.init();

        CommandRouter router = new CommandRouter(this);
        // 把 plugin.yml 里声明的全部指令都交给统一路由处理（模块启用/禁用与权限判断都在路由里）
        int routed = 0;
        for (String commandName : getDescription().getCommands().keySet()) {
            org.bukkit.command.PluginCommand command = getCommand(commandName);
            if (command == null) {
                getLogger().warning("指令 " + commandName + " 未注册。");
                continue;
            }
            command.setExecutor(router);
            command.setTabCompleter(router);
            routed++;
        }
        getLogger().info("已接管 " + routed + " 条指令。");

        Bukkit.getPluginManager().registerEvents(new CommandAliases(), this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(), this);
        Bukkit.getPluginManager().registerEvents(new CoreListener(this), this);

        long interval = Math.max(600, getConfig().getLong("auto-save-seconds", 300L)) * 20L;
        this.autosaveTask = Bukkit.getScheduler().runTaskTimer(this, this::saveAllData, interval, interval);

        banner(started);
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (stats != null) {
            stats.flush();
        }
        if (moduleManager != null) {
            moduleManager.shutdown();
        }
        saveAllData();
        getLogger().info("沧蓝工坊整合生存包已关闭，数据已保存。");
    }

    /** 注册全部 20 个功能模块（顺序即 /cl list 的显示顺序）。 */
    private void registerModules() {
        moduleManager.register(new PermModule(this));
        moduleManager.register(new TabModule(this));
        moduleManager.register(new NoticeModule(this));
        moduleManager.register(new ScoreboardModule(this));
        moduleManager.register(new EconomyModule(this));
        moduleManager.register(new ShopModule(this));
        moduleManager.register(new ClaimsModule(this));
        moduleManager.register(new WarpsModule(this));
        moduleManager.register(new RtpModule(this));
        moduleManager.register(new HomesModule(this));
        moduleManager.register(new TpaModule(this));
        moduleManager.register(new ChatModule(this));
        moduleManager.register(new ReportModule(this));
        moduleManager.register(new PunishModule(this));
        moduleManager.register(new AuthModule(this));
        moduleManager.register(new WelcomeModule(this));
        moduleManager.register(new SigninModule(this));
        moduleManager.register(new TopModule(this));
        moduleManager.register(new StatsModule(this));
        moduleManager.register(new MonitorModule(this));
        moduleManager.register(new MenuModule(this));
    }

    /** 老版本配置自动升级：补上 server-name，并把写死的服务器名换成 {server} 占位符。 */
    private void migrateConfig() {
        boolean changed = false;
        if (!getConfig().isSet("server-name")) {
            getConfig().set("server-name", DEFAULT_SERVER_NAME);
            changed = true;
        }
        java.util.List<String> header = getConfig().getStringList("tab.header");
        if (header.contains("&b&l沧蓝工坊 &f生存服")) {
            java.util.List<String> replaced = new java.util.ArrayList<>();
            for (String line : header) {
                replaced.add(line.equals("&b&l沧蓝工坊 &f生存服") ? "{server}" : line);
            }
            getConfig().set("tab.header", replaced);
            changed = true;
        }
        String title = getConfig().getString("scoreboard.title", "");
        if ("&b&l沧蓝工坊 &f生存服".equals(title)) {
            getConfig().set("scoreboard.title", "{server}");
            changed = true;
        }
        if (changed) {
            saveConfig();
            getLogger().info("已升级 config.yml：新增 server-name，并把默认标题改为 {server} 占位符。");
        }
    }

    /** /cl reload 使用：重载配置与全部数据。 */
    public void reloadEverything() {
        reloadConfig();
        Msg.prefix(getConfig().getString("prefix", "&b&l沧蓝工坊 &7» &f"));
        perms.loadData();
        economy.loadData();
        stats.loadData();
        mutes.loadData();
        moduleManager.reloadAll();
    }

    public void saveAllData() {
        try {
            if (stats != null) {
                stats.flush();
                stats.save();
            }
            if (economy != null) {
                economy.save();
            }
            if (mutes != null) {
                mutes.save();
            }
            if (perms != null) {
                perms.save();
            }
            if (moduleManager != null) {
                moduleManager.savePlayers();
            }
        } catch (Throwable t) {
            getLogger().warning("保存数据时出错：" + t.getMessage());
        }
    }

    private void banner(long started) {
        long cost = System.currentTimeMillis() - started;
        getLogger().info("==================================================");
        getLogger().info("  沧蓝工坊 · 整合生存服务端核心  v" + VERSION);
        getLogger().info("  适配版本：Paper 1.21.11   作者：" + AUTHOR);
        getLogger().info("  已载入模块：" + moduleManager.size() + " 个，本次启动耗时 " + cost + " ms");
        getLogger().info("  服主指令：/cl 帮助   （别名 /canglan /cyan）");
        getLogger().info("==================================================");
    }

    public ModuleManager modules() {
        return moduleManager;
    }

    public Perms perms() {
        return perms;
    }

    public Economy economy() {
        return economy;
    }

    public Stats stats() {
        return stats;
    }

    public Mutes mutes() {
        return mutes;
    }
}
