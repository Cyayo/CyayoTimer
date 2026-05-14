package com.cyayo.timer.gui;

import com.cyayo.timer.CyayoTimer;
import com.cyayo.timer.util.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RewardGUI {

    private final CyayoTimer plugin;

    public RewardGUI(CyayoTimer plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        FileConfiguration config = plugin.getConfigManager().getRewards();
        String title = ColorUtils.translateLegacy(config.getString("gui.title", "&bClaim Rewards"));
        int rows = config.getInt("gui.rows", 3);
        int size = rows * 9;
        
        Inventory inv = Bukkit.createInventory(null, size, title);
        
        // Filler
        ItemStack filler = new ItemStack(Material.valueOf(config.getString("gui.filler", "GRAY_STAINED_GLASS_PANE")));
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        
        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }

        // Custom Decoration Items
        List<Map<?, ?>> customItems = config.getMapList("gui.custom-items");
        for (Map<?, ?> itemMap : customItems) {
            Object slotObj = itemMap.get("slot");
            if (slotObj == null) continue;
            int slot = (int) slotObj;
            Material mat = Material.valueOf((String) itemMap.get("item"));
            String name = (String) itemMap.get("name");
            
            ItemStack customItem = new ItemStack(mat);
            ItemMeta customMeta = customItem.getItemMeta();
            if (name != null) customMeta.setDisplayName(ColorUtils.translateLegacy(name));
            customItem.setItemMeta(customMeta);
            inv.setItem(slot, customItem);
        }

        // Dynamic Rewards
        ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");
        if (rewardsSection != null) {
            for (String key : rewardsSection.getKeys(false)) {
                if (plugin.getRewardManager().isEnabled(key)) {
                    int slot = rewardsSection.getInt(key + ".slot", -1);
                    if (slot != -1 && slot < size) {
                        setupItem(inv, player, key, slot);
                    }
                }
            }
        }

        player.openInventory(inv);
    }

    public void refresh(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        FileConfiguration config = plugin.getConfigManager().getRewards();
        String title = ColorUtils.translateLegacy(config.getString("gui.title", "&bClaim Rewards"));

        // Only refresh if it's our GUI
        if (!player.getOpenInventory().getTitle().equals(title)) return;

        ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");
        if (rewardsSection != null) {
            for (String key : rewardsSection.getKeys(false)) {
                if (plugin.getRewardManager().isEnabled(key)) {
                    int slot = rewardsSection.getInt(key + ".slot", -1);
                    if (slot != -1 && slot < inv.getSize()) {
                        setupItem(inv, player, key, slot);
                    }
                }
            }
        }
    }

    private void setupItem(Inventory inv, Player player, String type, int slot) {
        FileConfiguration config = plugin.getConfigManager().getRewards();
        String path = "rewards." + type;
        
        Material mat = Material.valueOf(config.getString(path + ".item", "CHEST_MINECART"));
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(ColorUtils.translateLegacy(config.getString(path + ".display-name")));
        
        List<String> rawLore = config.getStringList(path + ".description");
        List<String> lore = new ArrayList<>(ColorUtils.translateLegacyList(rawLore));
        
        lore.add("");
        
        // Cooldown Status (Reset Status)
        boolean canClaim = plugin.getRewardManager().canClaimCooldown(player, type);
        FileConfiguration mainConfig = plugin.getConfigManager().getConfig();
        String checkmark = mainConfig.getString("formatting.checkmark", "&a✔");
        
        String resetStatus = canClaim ? checkmark : plugin.getRewardManager().getNextResetTime(type);
        lore.add(ColorUtils.translateLegacy(config.getString(path + ".lore.cooldown")
                .replace("%status%", resetStatus)));
        
        // Playtime Status
        String resetType = config.getString(path + ".reset-type", "DAILY").toUpperCase();
        int current = plugin.getRewardManager().getPlayerPlaytime(player, resetType);
        int required = plugin.getRewardManager().getRequiredPlaytime(type);
        
        String playtimeStatusFormat = mainConfig.getString("formatting.playtime-status", "&7(%current%/%required% menit)");
        String playtimeStatus = (current >= required) ? checkmark : playtimeStatusFormat
                .replace("%current%", String.valueOf(current))
                .replace("%required%", String.valueOf(required));
        
        lore.add(ColorUtils.translateLegacy(config.getString(path + ".lore.playtime")
                .replace("%current%", String.valueOf(current))
                .replace("%required%", String.valueOf(required))
                .replace("%status%", playtimeStatus)));
        
        if (canClaim && current >= required) {
            lore.add("");
            lore.add(plugin.getConfigManager().getMessage("gui.click-to-claim"));
        } else if (!canClaim) {
            lore.add(plugin.getConfigManager().getMessage("gui.wait-reset"));
        } else {
            lore.add(plugin.getConfigManager().getMessage("gui.need-playtime"));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }
}
