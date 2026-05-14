package com.cyayo.timer;

import com.cyayo.timer.command.MainCommand;
import com.cyayo.timer.gui.RewardGUI;
import com.cyayo.timer.listener.MenuListener;
import com.cyayo.timer.manager.BossManager;
import com.cyayo.timer.manager.ConfigManager;
import com.cyayo.timer.manager.RewardManager;
import com.cyayo.timer.task.TimerTask;
import com.cyayo.timer.util.CyayoPlaceholderExpansion;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.plugin.java.JavaPlugin;

public class CyayoTimer extends JavaPlugin {

    private static CyayoTimer instance;
    private BukkitAudiences adventure;
    private ConfigManager configManager;
    private RewardManager rewardManager;
    private BossManager bossManager;
    private RewardGUI rewardGUI;
    private TimerTask timerTask;

    @Override
    public void onEnable() {
        instance = this;
        this.adventure = BukkitAudiences.create(this);
        
        // Initialize Managers
        this.configManager = new ConfigManager(this);
        this.rewardManager = new RewardManager(this);
        this.bossManager = new BossManager(this);
        this.rewardGUI = new RewardGUI(this);
        
        // Load Configs
        this.configManager.loadConfigs();
        this.rewardManager.loadData();
        this.bossManager.loadBosses();
        
        // Start Task
        this.timerTask = new TimerTask(this);
        this.timerTask.runTaskTimer(this, 20L, configManager.getCheckInterval());
        
        // Register Commands
        MainCommand mainCommand = new MainCommand(this);
        getCommand("cyayotimer").setExecutor(mainCommand);
        getCommand("cyayotimer").setTabCompleter(mainCommand);
        getCommand("claim").setExecutor(mainCommand);
        getCommand("claim").setTabCompleter(mainCommand);
        
        // GUI Refresh Task
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                rewardGUI.refresh(player);
            }
        }, 100L, 100L); // Refresh every 5 seconds

        // Register Listeners
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(bossManager, this);
        
        // PlaceholderAPI Integration
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new CyayoPlaceholderExpansion(this).register();
        }

        getLogger().info("CyayoTimer has been enabled!");
    }

    public void reloadPlugin() {
        if (this.configManager != null) {
            this.configManager.reload();
        }
        
        if (this.bossManager != null) {
            this.bossManager.loadBosses();
        }
        
        // Restart TimerTask with new interval
        if (this.timerTask != null) {
            this.timerTask.cancel();
        }
        this.timerTask = new TimerTask(this);
        this.timerTask.runTaskTimer(this, 20L, configManager.getCheckInterval());
    }

    @Override
    public void onDisable() {
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
        if (bossManager != null) {
            bossManager.cleanup();
        }
        if (rewardManager != null) {
            rewardManager.saveData();
        }
        getLogger().info("CyayoTimer has been disabled!");
    }

    public static CyayoTimer getInstance() {
        return instance;
    }

    public BukkitAudiences getAdventure() {
        if (this.adventure == null) {
            throw new IllegalStateException("Tried to access Adventure when the plugin was disabled!");
        }
        return this.adventure;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public RewardGUI getRewardGUI() {
        return rewardGUI;
    }
}
