package com.cyayo.timer.manager;

import com.cyayo.timer.CyayoTimer;
import com.cyayo.timer.util.ColorUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RewardManager implements Listener {

    private final CyayoTimer plugin;
    private FileConfiguration data;
    private File dataFile;
    private final Random random = new Random();

    public RewardManager(CyayoTimer plugin) {
        this.plugin = plugin;
        this.data = new YamlConfiguration(); // Initialize to prevent NPE
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startPlaytimeTracker();
    }

    public void loadData() {
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public synchronized void saveData() {
        if (data == null || dataFile == null) return;
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml!");
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // Check specifically for playtime path to ensure it's initialized for old players too
        synchronized (this) {
            if (!data.contains(uuid + ".playtime.total-seconds")) {
                data.set(uuid + ".playtime.total-seconds", 0L);
            }
            // Proactively check for reset upon join
            long currentTotal = data.getLong(uuid + ".playtime.total-seconds", 0L);
            List<String> resetTypes = checkAndResetSnapshots(uuid, currentTotal);
            if (!resetTypes.isEmpty()) {
                for (String type : resetTypes) {
                    sendResetMessages(event.getPlayer(), type);
                }
            }
            saveData();
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        saveData();
    }

    private void startPlaytimeTracker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (data == null) return;
            boolean changed = false;
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                synchronized (this) {
                    long currentTotal = data.getLong(uuid + ".playtime.total-seconds", 0L);
                    // Proactively check for reset during tracking (catches 00:00 transition)
                    List<String> resetTypes = checkAndResetSnapshots(uuid, currentTotal);
                    if (!resetTypes.isEmpty()) {
                        changed = true;
                        for (String type : resetTypes) {
                            sendResetMessages(player, type);
                        }
                    }
                    data.set(uuid + ".playtime.total-seconds", currentTotal + 10L);
                }
                changed = true;
            }
            if (changed) {
                saveData();
            }
        }, 200L, 200L);
    }

    /**
     * Checks if the day/week/month has changed and updates snapshots accordingly.
     * @return List of reset types that occurred (DAILY, WEEKLY, MONTHLY)
     */
    private List<String> checkAndResetSnapshots(UUID uuid, long currentTotalSeconds) {
        ZonedDateTime now = getNow();
        int currentDay = now.getDayOfYear();
        int currentWeek = now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int currentMonth = now.getMonthValue();
        List<String> resetTypes = new ArrayList<>();

        if (data.getInt(uuid + ".playtime.daily.last-day", -1) != currentDay) {
            data.set(uuid + ".playtime.daily.last-day", currentDay);
            data.set(uuid + ".playtime.daily.snapshot", currentTotalSeconds);
            resetTypes.add("DAILY");
        }
        if (data.getInt(uuid + ".playtime.weekly.last-week", -1) != currentWeek) {
            data.set(uuid + ".playtime.weekly.last-week", currentWeek);
            data.set(uuid + ".playtime.weekly.snapshot", currentTotalSeconds);
            resetTypes.add("WEEKLY");
        }
        if (data.getInt(uuid + ".playtime.monthly.last-month", -1) != currentMonth) {
            data.set(uuid + ".playtime.monthly.last-month", currentMonth);
            data.set(uuid + ".playtime.monthly.snapshot", currentTotalSeconds);
            resetTypes.add("MONTHLY");
        }
        return resetTypes;
    }

    private void sendResetMessages(Player player, String resetType) {
        FileConfiguration rewardsConfig = plugin.getConfigManager().getRewards();
        ConfigurationSection section = rewardsConfig.getConfigurationSection("rewards");
        if (section == null) return;
        
        for (String key : section.getKeys(false)) {
            String type = section.getString(key + ".reset-type", "DAILY");
            if (type.equalsIgnoreCase(resetType)) {
                String msg = section.getString(key + ".reset-message");
                if (msg != null && !msg.isEmpty()) {
                    sendMessage(player, msg);
                }
                String sound = section.getString(key + ".reset-sound");
                if (sound != null && !sound.isEmpty()) {
                    plugin.getConfigManager().playRawSound(player, sound);
                }
            }
        }
    }

    private ZonedDateTime getNow() {
        return ZonedDateTime.now(java.time.ZoneId.of(plugin.getConfigManager().getTimezone()));
    }

    public boolean isEnabled(String type) {
        return plugin.getConfigManager().getRewards().getBoolean("rewards." + type + ".enabled", true);
    }

    public boolean canClaimCooldown(Player player, String type) {
        FileConfiguration config = plugin.getConfigManager().getRewards();
        String resetType = config.getString("rewards." + type + ".reset-type", "DAILY").toUpperCase();
        String specificDay = config.getString("rewards." + type + ".day", "ALL").toUpperCase();

        ZonedDateTime now = getNow();

        if (!specificDay.equals("ALL")) {
            if (!now.getDayOfWeek().name().equals(specificDay)) return false;
        }

        String lastClaimed = data.getString(player.getUniqueId() + ".rewards." + type);
        if (lastClaimed == null) return true;

        try {
            ZonedDateTime last = ZonedDateTime.parse(lastClaimed);
            switch (resetType) {
                case "DAILY":
                    return now.getDayOfYear() != last.getDayOfYear() || now.getYear() != last.getYear();
                case "WEEKLY":
                    return now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) != last.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) 
                            || now.getYear() != last.getYear();
                case "MONTHLY":
                    return now.getMonthValue() != last.getMonthValue() || now.getYear() != last.getYear();
                default:
                    return false;
            }
        } catch (Exception e) {
            return true;
        }
    }

    public String getNextResetTime(String type) {
        FileConfiguration config = plugin.getConfigManager().getRewards();
        String resetType = config.getString("rewards." + type + ".reset-type", "DAILY").toUpperCase();
        
        ZonedDateTime now = getNow();
        ZonedDateTime nextReset;

        switch (resetType) {
            case "WEEKLY":
                nextReset = now.with(TemporalAdjusters.next(java.time.DayOfWeek.SUNDAY)).withHour(0).withMinute(0).withSecond(0).withNano(0);
                break;
            case "MONTHLY":
                nextReset = now.with(TemporalAdjusters.firstDayOfNextMonth()).withHour(0).withMinute(0).withSecond(0).withNano(0);
                break;
            default: // DAILY
                nextReset = now.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                break;
        }

        Duration duration = Duration.between(now, nextReset);
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        ConfigurationSection format = plugin.getConfigManager().getConfig().getConfigurationSection("formatting.reset-countdown");
        String prefix = format != null ? format.getString("prefix", "&7Reset dalam ") : "&7Reset dalam ";
        String dUnit = format != null ? format.getString("days", "h ") : "h ";
        String hUnit = format != null ? format.getString("hours", "j ") : "j ";
        String mUnit = format != null ? format.getString("minutes", "m") : "m";

        StringBuilder sb = new StringBuilder(prefix);
        if (days > 0) sb.append(days).append(dUnit);
        if (hours > 0) sb.append(hours).append(hUnit);
        sb.append(minutes).append(mUnit);
        
        return sb.toString();
    }

    public synchronized int getPlayerPlaytime(Player player, String resetType) {
        UUID uuid = player.getUniqueId();
        long currentTotalSeconds = data.getLong(uuid + ".playtime.total-seconds", 0L);
        
        // Ensure snapshots are up to date even when just reading
        List<String> resetTypes = checkAndResetSnapshots(uuid, currentTotalSeconds);
        if (!resetTypes.isEmpty()) {
            for (String type : resetTypes) {
                sendResetMessages(player, type);
            }
            saveData();
        }

        String typeKey = resetType.toLowerCase();
        long snapshot = data.getLong(uuid + ".playtime." + typeKey + ".snapshot", 0L);
        
        // Safety check: if snapshot is somehow ahead of total (e.g. data reset)
        if (snapshot > currentTotalSeconds) {
            data.set(uuid + ".playtime." + typeKey + ".snapshot", currentTotalSeconds);
            saveData();
            snapshot = currentTotalSeconds;
        }

        return (int) Math.max(0, (currentTotalSeconds - snapshot) / 60);
    }

    public int getRequiredPlaytime(String type) {
        return plugin.getConfigManager().getRewards().getInt("rewards." + type + ".playtime-required", 0);
    }

    public void resetReward(UUID uuid, String type) {
        data.set(uuid + ".rewards." + type, null);
        saveData();
    }

    private String applyPlaceholders(Player player, String text) {
        String result = text.replace("%player%", player.getName());
        
        // Handle %random_min-max%
        Pattern randomPattern = Pattern.compile("%random_(\\d+)-(\\d+)%");
        Matcher matcher = randomPattern.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            int min = Integer.parseInt(matcher.group(1));
            int max = Integer.parseInt(matcher.group(2));
            int val = random.nextInt(max - min + 1) + min;
            matcher.appendReplacement(sb, String.valueOf(val));
        }
        matcher.appendTail(sb);
        result = sb.toString();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return PlaceholderAPI.setPlaceholders(player, result);
        }
        return result;
    }

    private void handleCommandExecution(Player player, String cmdLine, boolean console) {
        String finalCmd = cmdLine;
        int count = 1;

        if (finalCmd.startsWith("[REPEAT:")) {
            int closingBracket = finalCmd.indexOf("]");
            if (closingBracket > 7) {
                String range = finalCmd.substring(8, closingBracket);
                count = parseAmount(range);
                finalCmd = finalCmd.substring(closingBracket + 1).trim();
            }
        }

        for (int i = 0; i < count; i++) {
            String processed = applyPlaceholders(player, finalCmd);
            if (console) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed);
            } else {
                player.performCommand(processed);
            }
        }
    }

    private void sendMessage(Player player, String msg) {
        plugin.getAdventure().player(player).sendMessage(ColorUtils.parseToComponent(applyPlaceholders(player, msg)));
    }

    public void claim(Player player, String type) {
        if (!isEnabled(type)) return;

        FileConfiguration rewardConfig = plugin.getConfigManager().getRewards();
        String resetType = rewardConfig.getString("rewards." + type + ".reset-type", "DAILY").toUpperCase();

        if (!canClaimCooldown(player, type)) {
            plugin.getConfigManager().playSound(player, "reward-failed");
            sendMessage(player, plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getMessage("reward-already-claimed")
                    .replace("%type%", type)
                    .replace("%time%", getNextResetTime(type)));
            return;
        }

        int currentPlaytime = getPlayerPlaytime(player, resetType);
        int requiredPlaytime = getRequiredPlaytime(type);

        if (currentPlaytime < requiredPlaytime) {
            plugin.getConfigManager().playSound(player, "reward-failed");
            String timeMsg = (requiredPlaytime - currentPlaytime) + " menit";
            sendMessage(player, plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getMessage("insufficient-playtime")
                    .replace("%time%", timeMsg));
            return;
        }

        ConfigurationSection actions = rewardConfig.getConfigurationSection("rewards." + type + ".actions");
        
        if (actions != null) {
            // Build items list first to check space
            List<ItemStack> items = buildItemsList(player, actions);
            if (!hasSpace(player, items)) {
                plugin.getConfigManager().playSound(player, "reward-failed");
                sendMessage(player, plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getMessage("inventory-full"));
                return;
            }
            giveActions(player, actions, false);
        }

        ZonedDateTime now = getNow();
        data.set(player.getUniqueId() + ".rewards." + type, now.toString());
        saveData();

        plugin.getConfigManager().playSound(player, "reward-claimed");
        sendMessage(player, plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getMessage("reward-claimed")
                .replace("%type%", type));
        
        if (player.getOpenInventory().getTitle().equals(ColorUtils.translateLegacy(rewardConfig.getString("gui.title")))) {
            plugin.getRewardGUI().refresh(player);
        }
    }

    public void giveActions(Player player, ConfigurationSection actions, boolean dropIfFull) {
        if (actions == null) return;
        
        List<ItemStack> itemsToGive = buildItemsList(player, actions);

        // Give items and handle overflow
        for (ItemStack item : itemsToGive) {
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            if (!overflow.isEmpty() && dropIfFull) {
                for (ItemStack o : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), o);
                }
            }
        }

        if (actions.contains("commands")) {
            for (String cmd : actions.getStringList("commands")) {
                handleCommandExecution(player, cmd, true);
            }
        }
        if (actions.contains("player-commands")) {
            for (String cmd : actions.getStringList("player-commands")) {
                handleCommandExecution(player, cmd, false);
            }
        }
        if (actions.contains("messages")) {
            for (String msg : actions.getStringList("messages")) {
                sendMessage(player, msg);
            }
        }
    }

    public List<ItemStack> buildItemsList(Player player, ConfigurationSection actions) {
        List<ItemStack> itemsToGive = new ArrayList<>();
        if (actions == null) return itemsToGive;

        if (actions.contains("vanilla-items")) {
            for (String itemStr : actions.getStringList("vanilla-items")) {
                String[] parts = itemStr.split(":");
                try {
                    Material mat = Material.valueOf(parts[0]);
                    int amount = parseAmount(parts.length > 1 ? parts[1] : "1");
                    ItemStack item = new ItemStack(mat, amount);
                    if (parts.length > 2) {
                        ItemMeta meta = item.getItemMeta();
                        meta.setDisplayName(ColorUtils.translateLegacy(applyPlaceholders(player, parts[2])));
                        item.setItemMeta(meta);
                    }
                    itemsToGive.add(item);
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid vanilla item: " + itemStr);
                }
            }
        }
        
        if (actions.contains("mmo-items") && Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            for (String mmoStr : actions.getStringList("mmo-items")) {
                String[] parts = mmoStr.split(":");
                if (parts.length >= 2) {
                    Type mmoType = MMOItems.plugin.getTypes().get(parts[0]);
                    if (mmoType != null) {
                        ItemStack mmoItem = MMOItems.plugin.getItem(mmoType, parts[1]);
                        if (mmoItem != null) {
                            int amount = parseAmount(parts.length > 2 ? parts[2] : "1");
                            mmoItem.setAmount(amount);
                            itemsToGive.add(mmoItem);
                        }
                    }
                }
            }
        }
        return itemsToGive;
    }

    private boolean hasSpace(Player player, List<ItemStack> items) {
        int emptySlots = 0;
        for (ItemStack is : player.getInventory().getStorageContents()) {
            if (is == null || is.getType() == Material.AIR) emptySlots++;
        }
        return emptySlots >= items.size();
    }

    private int parseAmount(String input) {
        if (input.contains("-")) {
            String[] range = input.split("-");
            int min = Integer.parseInt(range[0]);
            int max = Integer.parseInt(range[1]);
            return random.nextInt(max - min + 1) + min;
        }
        return Integer.parseInt(input);
    }
}
