package com.cyayo.timer.listener;

import com.cyayo.timer.CyayoTimer;
import com.cyayo.timer.util.ColorUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class MenuListener implements Listener {

    private final CyayoTimer plugin;

    public MenuListener(CyayoTimer plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        FileConfiguration config = plugin.getConfigManager().getRewards();
        String title = ColorUtils.translateLegacy(config.getString("gui.title", "&bClaim Rewards"));
        
        if (!event.getView().getTitle().equals(title)) return;
        
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        int slot = event.getRawSlot();
        
        // Dynamic Reward Checking
        ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");
        if (rewardsSection != null) {
            for (String key : rewardsSection.getKeys(false)) {
                if (plugin.getRewardManager().isEnabled(key)) {
                    if (slot == rewardsSection.getInt(key + ".slot", -1)) {
                        plugin.getConfigManager().playSound(player, "gui-click");
                        plugin.getRewardManager().claim(player, key);
                        return;
                    }
                }
            }
        }
    }
}
