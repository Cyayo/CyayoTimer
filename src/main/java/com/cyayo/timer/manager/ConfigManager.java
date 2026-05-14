package com.cyayo.timer.manager;

import com.cyayo.timer.CyayoTimer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigManager {

    private final CyayoTimer plugin;
    private FileConfiguration config;
    private FileConfiguration rewards;
    private FileConfiguration bosses;

    public ConfigManager(CyayoTimer plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        this.config = loadFile("config.yml");
        this.rewards = loadFile("rewards.yml");
        this.bosses = loadFile("bosses.yml");
    }

    private FileConfiguration loadFile(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        loadConfigs();
        plugin.getRewardManager().loadData();
        plugin.getBossManager().loadBosses();
    }

    public String getMessage(String path) {
        String msg = config.getString("messages." + path, path);
        return com.cyayo.timer.util.ColorUtils.translateLegacy(msg);
    }

    public String getPrefix() {
        return getMessage("prefix");
    }

    public String getTimezone() {
        return config.getString("settings.timezone", "GMT+7");
    }

    public long getCheckInterval() {
        return config.getLong("settings.timer-check-interval", 20L);
    }

    public void playSound(org.bukkit.entity.Player player, String path) {
        String soundData = config.getString("sounds." + path);
        playRawSound(player, soundData);
    }

    public void playRawSound(org.bukkit.entity.Player player, String soundData) {
        if (soundData == null || soundData.isEmpty()) return;
        
        try {
            String[] parts = soundData.split(";");
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0].toUpperCase());
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid sound data: " + soundData);
        }
    }

    public void playGlobalSound(String path) {
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            playSound(player, path);
        }
    }

    public FileConfiguration getRewards() {
        return rewards;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getBosses() {
        return bosses;
    }
}
