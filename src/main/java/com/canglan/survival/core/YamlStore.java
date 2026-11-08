package com.canglan.survival.core;

import com.canglan.survival.CanglanSurvival;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * 简单的 YAML 数据文件包装器，统一读写插件数据目录下的文件。
 */
public class YamlStore {

    protected final CanglanSurvival plugin;
    private final String fileName;
    protected YamlConfiguration yaml;

    public YamlStore(CanglanSurvival plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.yaml = YamlConfiguration.loadConfiguration(file());
    }

    public File file() {
        return new File(plugin.getDataFolder(), fileName);
    }

    public String fileName() {
        return fileName;
    }

    public YamlConfiguration yaml() {
        return yaml;
    }

    public void load() {
        this.yaml = YamlConfiguration.loadConfiguration(file());
    }

    public void save() {
        File file = file();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("无法创建数据目录：" + parent.getAbsolutePath());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存 " + fileName + " 失败：" + e.getMessage());
        }
    }

    public void reload() {
        load();
    }
}
