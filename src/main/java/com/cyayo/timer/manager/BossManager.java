package com.cyayo.timer.manager;

import com.cyayo.timer.CyayoTimer;
import com.cyayo.timer.util.ColorUtils;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import de.oliver.fancyholograms.api.hologram.HologramType;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDeathEvent;

import java.io.File;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class BossManager implements Listener {

    private final CyayoTimer plugin;
    private final List<BossData> bosses = new ArrayList<>();
    private final Map<String, Hologram> activeHolograms = new HashMap<>();
    private final Map<String, Long> lastAnnouncedKeys = new HashMap<>();
    private final Map<String, String> lastSpawnedTimeSlot = new HashMap<>();
    
    // Key: BossID, Value: Active Event Data
    private final Map<String, ActiveEventData> activeEvents = new HashMap<>();

    // Recurring Announcements Settings
    private boolean recurringEnabled = false;
    private int recurringStartMinutes = 10;
    private AnnouncementData defaultAnnouncement;

    public BossManager(CyayoTimer plugin) {
        this.plugin = plugin;
    }

    public void loadBosses() {
        try {
            fullCleanup();
            bosses.clear();
            
            // Load Recurring Settings
            ConfigurationSection annGlobal = plugin.getConfig().getConfigurationSection("boss-announcements");
            if (annGlobal != null) {
                recurringEnabled = annGlobal.getBoolean("recurring-enabled", false);
                recurringStartMinutes = annGlobal.getInt("start-at-minutes", 10);
                defaultAnnouncement = new AnnouncementData();
                defaultAnnouncement.message = annGlobal.getString("message");
                defaultAnnouncement.sound = annGlobal.getString("sound");
                defaultAnnouncement.broadcastType = annGlobal.getString("broadcast-type", "GLOBAL").toUpperCase();
                defaultAnnouncement.radius = annGlobal.getDouble("broadcast-radius", 50.0);
            }
            
            File file = new File(plugin.getDataFolder(), "bosses.yml");
            if (!file.exists()) plugin.saveResource("bosses.yml", false);

            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = config.getConfigurationSection("bosses");
            if (section == null) return;

            for (String key : section.getKeys(false)) {
                BossData boss = new BossData();
                boss.id = key;
                boss.mythicMob = section.getString(key + ".mythic-mob");
                boss.displayName = section.getString(key + ".display-name", key);
                
                ConfigurationSection locSection = section.getConfigurationSection(key + ".location");
                World world = Bukkit.getWorld(locSection.getString("world"));
                if (world == null) continue;

                boss.spawnLocation = new Location(
                        world,
                        locSection.getDouble("x"), locSection.getDouble("y"), locSection.getDouble("z"),
                        (float) locSection.getDouble("yaw"), (float) locSection.getDouble("pitch")
                );

                boss.spawnTimes = section.getStringList(key + ".spawn-times");
                boss.days = section.getStringList(key + ".days");
                boss.preSpawnMinutes = section.getInt(key + ".hologram.pre-spawn-minutes", 60);
                boss.offsetY = section.getDouble(key + ".hologram.offset-y", 3.0);
                boss.hologramLines = section.getStringList(key + ".hologram.lines");
                
                boss.hologramScale = (float) section.getDouble(key + ".hologram.scale", 1.0);
                boss.hologramShadow = section.getBoolean(key + ".hologram.shadow", false);
                boss.hologramSeeThrough = section.getBoolean(key + ".hologram.see-through", false);
                boss.hologramAlignment = TextDisplay.TextAlignment.valueOf(section.getString(key + ".hologram.alignment", "CENTER").toUpperCase());
                boss.hologramBackgroundColor = parseColor(section.getString(key + ".hologram.background-color", "NONE"));
                boss.hologramBackgroundAlpha = section.getInt(key + ".hologram.background-alpha", -1);

                boss.useEventTime = section.getBoolean(key + ".event-time.enabled", false);
                boss.eventTimeMinutes = section.getInt(key + ".event-time.duration", 30);
                boss.escapeMessage = section.getString(key + ".event-time.escape-message", "&e%boss_name% &ftelah melarikan diri!");
                boss.escapeBroadcastType = section.getString(key + ".event-time.broadcast-type", "GLOBAL").toUpperCase();
                boss.escapeRadius = section.getDouble(key + ".event-time.broadcast-radius", 50.0);
                boss.eventHologramLines = section.getStringList(key + ".event-time.hologram-lines");
                
                boss.useTrackedMob = section.getBoolean(key + ".tracked-mob.enabled", false);
                boss.trackedMobInternalName = section.getString(key + ".tracked-mob.internal-name");
                
                ConfigurationSection lbSection = section.getConfigurationSection(key + ".leaderboard");
                if (lbSection != null) {
                    boss.useLeaderboard = lbSection.getBoolean("enabled", false);
                    boss.leaderboardBroadcastType = lbSection.getString("broadcast-type", "GLOBAL").toUpperCase();
                    boss.leaderboardRadius = lbSection.getDouble("broadcast-radius", 50.0);
                    boss.leaderboardLines = lbSection.getStringList("lines");
                }
                
                ConfigurationSection rewardsSection = section.getConfigurationSection(key + ".rewards");
                if (rewardsSection != null) {
                    boss.useRewards = rewardsSection.getBoolean("enabled", false);
                    boss.topPlayersReward = rewardsSection.getInt("top-players", 1);
                    boss.rewardSection = rewardsSection.getConfigurationSection("ranks");
                }
                
                ConfigurationSection deathSection = section.getConfigurationSection(key + ".death");
                if (deathSection != null) {
                    boss.deathMessage = deathSection.getString("message");
                    boss.deathSound = deathSection.getString("sound");
                    boss.deathBroadcastType = deathSection.getString("broadcast-type", "GLOBAL").toUpperCase();
                    boss.deathRadius = deathSection.getDouble("broadcast-radius", 50.0);
                }
                
                ConfigurationSection annSection = section.getConfigurationSection(key + ".announcements");
                if (annSection != null) {
                    for (String minKey : annSection.getKeys(false)) {
                        try {
                            AnnouncementData ad = new AnnouncementData();
                            ad.message = annSection.getString(minKey + ".message");
                            ad.sound = annSection.getString(minKey + ".sound");
                            ad.broadcastType = annSection.getString(minKey + ".broadcast-type", "GLOBAL");
                            ad.radius = annSection.getDouble(minKey + ".broadcast-radius", 100.0);
                            boss.announcements.put(Integer.parseInt(minKey), ad);
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid announcement minute key: " + minKey + " for boss " + key);
                        }
                    }
                }
                
                bosses.add(boss);
                forceCleanupAtLocation(boss.spawnLocation, true);
            }
            
            // Re-sync active events with new config data
            syncActiveEvents();
            
            plugin.getLogger().info("Bosses loaded and synced: " + bosses.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void syncActiveEvents() {
        for (Map.Entry<String, ActiveEventData> entry : activeEvents.entrySet()) {
            BossData boss = getBossById(entry.getKey());
            if (boss != null) {
                ActiveEventData data = entry.getValue();
                // Recalculate expiry based on potentially new eventTimeMinutes
                data.expiryTime = data.startTime.plusMinutes(boss.eventTimeMinutes);
                plugin.getLogger().info("[Event] Resynced " + boss.id + ". New expiry: " + data.expiryTime.toLocalTime());
            }
        }
    }

    private void forceCleanupAtLocation(Location loc, boolean forceLoad) {
        if (loc == null || loc.getWorld() == null) return;
        boolean isLoaded = loc.getChunk().isLoaded();
        if (!isLoaded && !forceLoad) return;

        if (!isLoaded) loc.getChunk().load();
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 7.0, 15.0, 7.0)) {
            if (entity.hasMetadata("cyayo_hologram")) entity.remove();
        }
        if (!isLoaded) loc.getChunk().unload();
    }

    public void checkBosses() {
        cleanupHologramsOnly();
        if (Bukkit.getOnlinePlayers().isEmpty()) return;

        ZonedDateTime now = ZonedDateTime.now(java.time.ZoneId.of(plugin.getConfigManager().getTimezone()));
        String currentDay = now.getDayOfWeek().name();
        String dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        handleActiveEvents(now);

        for (BossData boss : bosses) {
            if (!boss.days.contains(currentDay)) continue;

            boolean inPreSpawnRange = false;
            for (String timeStr : boss.spawnTimes) {
                try {
                    LocalTime spawnTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
                    long secondsUntil = now.toLocalTime().until(spawnTime, ChronoUnit.SECONDS);
                    String spawnSlotKey = boss.id + "_" + dateStr + "_" + timeStr;

                    if (secondsUntil <= 0 && secondsUntil > -60) {
                        if (!lastSpawnedTimeSlot.containsKey(spawnSlotKey)) {
                            spawnBoss(boss, dateStr, timeStr, spawnSlotKey, now);
                            lastSpawnedTimeSlot.put(spawnSlotKey, "SPAWNED");
                        }
                        inPreSpawnRange = true;
                    } else if (secondsUntil > 0 && secondsUntil <= boss.preSpawnMinutes * 60L) {
                        if (!activeEvents.containsKey(boss.id)) {
                            updateInternalHologram(boss, spawnTime, now, dateStr, timeStr);
                        }
                        inPreSpawnRange = true;
                    }
                } catch (Exception ignored) {}
            }
            if (!inPreSpawnRange && !activeEvents.containsKey(boss.id)) {
                removeInternalHologram(boss.id);
            }
        }
    }

    private void handleActiveEvents(ZonedDateTime now) {
        Iterator<Map.Entry<String, ActiveEventData>> it = activeEvents.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ActiveEventData> entry = it.next();
            String bossId = entry.getKey();
            ActiveEventData eventData = entry.getValue();
            BossData boss = getBossById(bossId);

            if (boss == null) {
                it.remove();
                continue;
            }

            // Grace period check
            long aliveSeconds = eventData.startTime.until(now, ChronoUnit.SECONDS);
            if (aliveSeconds < 5) {
                updateEventHologram(boss, eventData.expiryTime, now);
                continue;
            }

            // Death check
            if (isBossDead(boss, eventData)) {
                handleBossDeath(bossId, boss, eventData, "Unknown");
                it.remove();
                continue;
            }

            // Time's up check
            if (now.isAfter(eventData.expiryTime)) {
                despawnBoss(boss, eventData.mobObject);
                sendEscapeMessage(boss);
                removeInternalHologram(bossId);
                it.remove();
                continue;
            }

            updateEventHologram(boss, eventData.expiryTime, now);
        }
    }

    private boolean isBossDead(BossData boss, ActiveEventData eventData) {
        Object spawnedMob = eventData.mobObject;
        // 1. Check if the initial spawned mob is still alive
        boolean spawnedAlive = false;
        if (spawnedMob != null) {
            try {
                spawnedAlive = !(boolean) spawnedMob.getClass().getMethod("isDead").invoke(spawnedMob);
            } catch (Exception ignored) {}
        }

        if (spawnedAlive) return false;

        // 2. If spawned mob is dead/despawned, check if we need to track a different mob
        if (boss.useTrackedMob && boss.trackedMobInternalName != null) {
            // Search for the tracked mob nearby
            double radius = 32.0; // Search radius
            for (Entity entity : boss.spawnLocation.getWorld().getNearbyEntities(boss.spawnLocation, radius, radius, radius)) {
                if (MythicBukkit.inst().getMobManager().isActiveMob(entity.getUniqueId())) {
                    ActiveMob am = MythicBukkit.inst().getMobManager().getMythicMobInstance(entity);
                    if (am != null && am.getType().getInternalName().equalsIgnoreCase(boss.trackedMobInternalName)) {
                        // Found the tracked mob, update our reference and return alive
                        eventData.mobObject = am;
                        // Apply metadata to the new tracked mob
                        am.getEntity().getBukkitEntity().setMetadata("cyayo_boss_id", new FixedMetadataValue(plugin, boss.id));
                        return false; 
                    }
                }
            }
        }

        return !spawnedAlive;
    }

    private void despawnBoss(BossData boss, Object mob) {
        if (mob != null) {
            try {
                mob.getClass().getMethod("despawn").invoke(mob);
            } catch (Exception ignored) {}
        }
        
        // Safety check: if tracked mob is enabled, also search and despawn it nearby
        if (boss.useTrackedMob && boss.trackedMobInternalName != null) {
            double radius = 32.0;
            for (Entity entity : boss.spawnLocation.getWorld().getNearbyEntities(boss.spawnLocation, radius, radius, radius)) {
                if (MythicBukkit.inst().getMobManager().isActiveMob(entity.getUniqueId())) {
                    ActiveMob am = MythicBukkit.inst().getMobManager().getMythicMobInstance(entity);
                    if (am != null && am.getType().getInternalName().equalsIgnoreCase(boss.trackedMobInternalName)) {
                        am.despawn();
                    }
                }
            }
        }
    }

    private void spawnBoss(BossData boss, String date, String time, String slotKey, ZonedDateTime now) {
        handleAnnouncement(boss, 0, date, time);
        boss.spawnLocation.getChunk().load();
        
        Object am = MythicBukkit.inst().getMobManager().spawnMob(boss.mythicMob, boss.spawnLocation);
        
        if (am == null) {
            plugin.getLogger().warning("Failed to spawn MythicMob " + boss.mythicMob + " for boss " + boss.id);
            removeInternalHologram(boss.id);
            return;
        }

        // Apply metadata for damage tracking
        if (am instanceof ActiveMob activeMob) {
            activeMob.getEntity().getBukkitEntity().setMetadata("cyayo_boss_id", new FixedMetadataValue(plugin, boss.id));
        }

        if (boss.useEventTime) {
            ZonedDateTime expiry = now.plusMinutes(boss.eventTimeMinutes);
            activeEvents.put(boss.id, new ActiveEventData(am, now, expiry));
            plugin.getLogger().info("[Event] Boss " + boss.id + " spawned. Event duration: " + boss.eventTimeMinutes + "m");
        } else {
            removeInternalHologram(boss.id);
        }
    }

    private void sendEscapeMessage(BossData boss) {
        if (boss.escapeMessage == null || boss.escapeMessage.isEmpty()) return;
        String msg = plugin.getConfigManager().getPrefix() + boss.escapeMessage.replace("%boss_name%", boss.displayName);
        
        String broadcastType = boss.escapeBroadcastType != null ? boss.escapeBroadcastType : "GLOBAL";
        
        if (broadcastType.equals("RADIUS")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(boss.spawnLocation.getWorld()) && 
                    p.getLocation().distance(boss.spawnLocation) <= boss.escapeRadius) {
                    plugin.getAdventure().player(p).sendMessage(ColorUtils.parseToComponent(msg));
                }
            }
        } else {
            plugin.getAdventure().all().sendMessage(ColorUtils.parseToComponent(msg));
        }
    }

    private void updateEventHologram(BossData boss, ZonedDateTime expiry, ZonedDateTime now) {
        long secondsLeft = now.until(expiry, ChronoUnit.SECONDS);
        String countdown = formatTime(secondsLeft);
        
        List<String> rawLines = boss.eventHologramLines;
        if (rawLines == null || rawLines.isEmpty()) {
            rawLines = Arrays.asList("&6&lEVENT SEDANG BERLANGSUNG", "&e%boss_name% &fada di lokasi!", "&fSisa Waktu: &b%countdown%");
        }

        List<String> lines = new ArrayList<>();
        for (String line : rawLines) {
            lines.add(ColorUtils.translateLegacy(line
                    .replace("%boss_name%", boss.displayName)
                    .replace("%countdown%", countdown)
                    .replace("%x%", String.valueOf((int)boss.spawnLocation.getX()))
                    .replace("%y%", String.valueOf((int)boss.spawnLocation.getY()))
                    .replace("%z%", String.valueOf((int)boss.spawnLocation.getZ()))));
        }
        
        Hologram hologram = getOrCreateHologram(boss);
        if (hologram != null) {
            TextHologramData data = (TextHologramData) hologram.getData();
            data.setText(lines);
            hologram.forceUpdate();
        }
    }

    private void updateInternalHologram(BossData boss, LocalTime spawnTime, ZonedDateTime now, String date, String time) {
        long secondsLeft = now.toLocalTime().until(spawnTime, ChronoUnit.SECONDS);
        long minutesLeft = (secondsLeft + 59) / 60;

        if (minutesLeft > 0) {
            handleAnnouncement(boss, (int) minutesLeft, date, time);
        }
        
        Hologram hologram = getOrCreateHologram(boss);
        if (hologram != null) {
            String countdown = formatTime(secondsLeft);
            List<String> lines = new ArrayList<>();
            for (String line : boss.hologramLines) {
                lines.add(ColorUtils.translateLegacy(line
                    .replace("%boss_name%", boss.displayName)
                    .replace("%countdown%", countdown)
                    .replace("%x%", String.valueOf((int)boss.spawnLocation.getX()))
                    .replace("%y%", String.valueOf((int)boss.spawnLocation.getY()))
                    .replace("%z%", String.valueOf((int)boss.spawnLocation.getZ()))));
            }
            TextHologramData data = (TextHologramData) hologram.getData();
            data.setText(lines);
            hologram.forceUpdate();
        }
    }

    private Hologram getOrCreateHologram(BossData boss) {
        Hologram hologram = activeHolograms.get(boss.id);
        if (hologram != null) {
            return hologram;
        }

        Location loc = boss.spawnLocation.clone().add(0, boss.offsetY, 0);
        
        // Use FancyHolograms API to create hologram
        String holoName = "cyayo_timer_" + boss.id;
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        
        // Remove existing if any (to prevent stacking)
        manager.getHologram(holoName).ifPresent(h -> {
            manager.removeHologram(h);
        });

        TextHologramData data = new TextHologramData(holoName, loc);
        data.setPersistent(false); // We handle lifecycle
        
        hologram = manager.create(data);
        applyHologramSettings(hologram, boss);
        
        manager.addHologram(hologram);
        activeHolograms.put(boss.id, hologram);
        
        return hologram;
    }

    private void applyHologramSettings(Hologram hologram, BossData boss) {
        if (!(hologram.getData() instanceof TextHologramData data)) return;
        
        data.setTextShadow(boss.hologramShadow);
        data.setSeeThrough(boss.hologramSeeThrough);
        data.setTextAlignment(boss.hologramAlignment);
        
        org.bukkit.Color bgColor = boss.hologramBackgroundColor;
        if (boss.hologramBackgroundAlpha != -1 && bgColor != null) {
            bgColor = org.bukkit.Color.fromARGB(boss.hologramBackgroundAlpha, bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue());
        }
        data.setBackground(bgColor);
        data.setScale(new Vector3f(boss.hologramScale, boss.hologramScale, boss.hologramScale));
    }

    private org.bukkit.Color parseColor(String str) {
        if (str == null || str.isEmpty() || str.equalsIgnoreCase("NONE")) {
            return org.bukkit.Color.fromARGB(0, 0, 0, 0);
        }
        if (str.equalsIgnoreCase("DEFAULT")) {
            return org.bukkit.Color.fromARGB(64, 0, 0, 0); // Default MC background
        }
        try {
            if (str.startsWith("#")) {
                int rgb = Integer.parseInt(str.substring(1), 16);
                return org.bukkit.Color.fromRGB(rgb);
            }
            // Support ARGB hex
            if (str.startsWith("0x")) {
                long argb = Long.parseLong(str.substring(2), 16);
                return org.bukkit.Color.fromARGB((int)(argb >> 24) & 0xFF, (int)(argb >> 16) & 0xFF, (int)(argb >> 8) & 0xFF, (int)argb & 0xFF);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid color format: " + str);
        }
        return org.bukkit.Color.fromARGB(0, 0, 0, 0);
    }

    private boolean isAnyPlayerNear(Location loc, double radius) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(loc.getWorld()) && p.getLocation().distanceSquared(loc) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    private void handleAnnouncement(BossData boss, int minutesLeft, String date, String time) {
        String key = (date != null) ? boss.id + "_" + date + "_" + time + "_" + minutesLeft : null;
        if (key != null && lastAnnouncedKeys.containsKey(key)) return;

        AnnouncementData ad = boss.announcements.get(minutesLeft);
        
        // Fallback to recurring announcement if enabled and no specific announcement exists
        if (ad == null && recurringEnabled && minutesLeft > 0 && minutesLeft <= recurringStartMinutes) {
            ad = defaultAnnouncement;
        }

        if (ad != null && ad.message != null && !ad.message.isEmpty()) {
            String countdown = formatTime(minutesLeft * 60L);
            String msg = plugin.getConfigManager().getPrefix() + ad.message
                    .replace("%boss_name%", boss.displayName)
                    .replace("%countdown%", countdown);

            // Handle Message & Sound Broadcasting
            if (ad.broadcastType.equalsIgnoreCase("RADIUS")) {
                double radiusSq = ad.radius * ad.radius;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().equals(boss.spawnLocation.getWorld())) {
                        // Use 2D distance (XZ) to be more lenient
                        double dx = p.getLocation().getX() - boss.spawnLocation.getX();
                        double dz = p.getLocation().getZ() - boss.spawnLocation.getZ();
                        double distSq = (dx * dx) + (dz * dz);
                        
                        if (distSq <= radiusSq) {
                            plugin.getAdventure().player(p).sendMessage(ColorUtils.parseToComponent(msg));
                            if (ad.sound != null) playCustomSoundFor(p, ad.sound);
                        }
                    }
                }
            } else {
                plugin.getAdventure().all().sendMessage(ColorUtils.parseToComponent(msg));
                if (ad.sound != null) {
                    for (Player p : Bukkit.getOnlinePlayers()) playCustomSoundFor(p, ad.sound);
                }
            }
            if (key != null) lastAnnouncedKeys.put(key, System.currentTimeMillis());
        }
    }

    private void playCustomSoundFor(Player p, String soundData) {
        try {
            String[] parts = soundData.split(";");
            p.playSound(p.getLocation(), org.bukkit.Sound.valueOf(parts[0].toUpperCase()), 
                       parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f, 
                       parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f);
        } catch (Exception ignored) {}
    }

    private void removeInternalHologram(String bossId) {
        Hologram hologram = activeHolograms.remove(bossId);
        if (hologram != null) {
            FancyHologramsPlugin.get().getHologramManager().removeHologram(hologram);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!victim.hasMetadata("cyayo_boss_id")) return;

        Player damager = null;
        if (event.getDamager() instanceof Player p) {
            damager = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            damager = p;
        }

        if (damager == null) return;

        String bossId = victim.getMetadata("cyayo_boss_id").get(0).asString();
        ActiveEventData data = activeEvents.get(bossId);
        
        if (data != null) {
            // Use raw damage instead of final damage for more intuitive leaderboard
            double damage = event.getDamage();
            
            if (damage > 0) {
                data.damageMap.merge(damager.getUniqueId(), damage, Double::sum);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDeath(EntityDeathEvent event) {
        Entity victim = event.getEntity();
        
        String toRemove = null;
        for (Map.Entry<String, ActiveEventData> entry : activeEvents.entrySet()) {
            if (isSameMob(victim, entry.getValue().mobObject)) {
                toRemove = entry.getKey();
                break;
            }
        }
        
        if (toRemove != null) {
            ActiveEventData data = activeEvents.remove(toRemove);
            BossData boss = getBossById(toRemove);
            if (data != null && boss != null) {
                String killerName = event.getEntity().getKiller() != null ? event.getEntity().getKiller().getName() : "Unknown";
                
                // Fallback: If killer is unknown, use top damager
                if ((killerName == null || killerName.equals("Unknown")) && !data.damageMap.isEmpty()) {
                    UUID topUUID = data.damageMap.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey).orElse(null);
                    if (topUUID != null) {
                        String topName = Bukkit.getOfflinePlayer(topUUID).getName();
                        if (topName != null) killerName = topName;
                    }
                }
                
                handleBossDeath(toRemove, boss, data, killerName);
            }
        }
    }

    private void handleBossDeath(String bossId, BossData boss, ActiveEventData data, String killerName) {
        sendDeathMessage(boss, killerName);
        sendLeaderboard(boss, data);
        giveBossRewards(boss, data);
        removeInternalHologram(bossId);
    }

    private void giveBossRewards(BossData boss, ActiveEventData eventData) {
        if (!boss.useRewards || eventData.damageMap.isEmpty() || boss.rewardSection == null) return;

        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(eventData.damageMap.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        for (int i = 1; i <= boss.topPlayersReward; i++) {
            if (i > sorted.size()) break;

            Map.Entry<UUID, Double> entry = sorted.get(i - 1);
            Player player = Bukkit.getPlayer(entry.getKey());
            
            if (player != null && player.isOnline()) {
                ConfigurationSection rankActions = boss.rewardSection.getConfigurationSection(String.valueOf(i));
                if (rankActions != null) {
                    plugin.getRewardManager().giveActions(player, rankActions, true);
                }
            }
        }
    }

    private void sendDeathMessage(BossData boss, String killerName) {
        if (boss.deathMessage == null || boss.deathMessage.isEmpty()) return;

        String msg = plugin.getConfigManager().getPrefix() + boss.deathMessage
                .replace("%boss_name%", boss.displayName)
                .replace("%killer%", killerName);

        if (boss.deathBroadcastType.equals("RADIUS")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(boss.spawnLocation.getWorld()) && 
                    p.getLocation().distance(boss.spawnLocation) <= boss.deathRadius) {
                    plugin.getAdventure().player(p).sendMessage(ColorUtils.parseToComponent(msg));
                    if (boss.deathSound != null) playCustomSoundFor(p, boss.deathSound);
                }
            }
        } else {
            plugin.getAdventure().all().sendMessage(ColorUtils.parseToComponent(msg));
            if (boss.deathSound != null) {
                for (Player p : Bukkit.getOnlinePlayers()) playCustomSoundFor(p, boss.deathSound);
            }
        }
    }

    private boolean isSameMob(Entity entity, Object mobObj) {
        if (mobObj == null) return false;
        if (mobObj instanceof ActiveMob am) {
            return am.getUniqueId().equals(entity.getUniqueId());
        }
        if (mobObj instanceof Entity e) return e.getUniqueId().equals(entity.getUniqueId());
        
        try {
            UUID uuid = (UUID) mobObj.getClass().getMethod("getUniqueId").invoke(mobObj);
            return uuid.equals(entity.getUniqueId());
        } catch (Exception ignored) {}
        
        return false;
    }

    private void sendLeaderboard(BossData boss, ActiveEventData eventData) {
        if (!boss.useLeaderboard || eventData.damageMap.isEmpty()) return;

        double totalDamage = eventData.damageMap.values().stream().mapToDouble(Double::doubleValue).sum();
        
        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(eventData.damageMap.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        List<String> finalMessages = new ArrayList<>();
        for (String line : boss.leaderboardLines) {
            String processed = line.replace("%boss_name%", boss.displayName);
            
            // Process placeholders for ranks 1-10
            for (int i = 1; i <= 10; i++) {
                String playerKey = "%player_" + i + "%";
                String damageKey = "%damage_" + i + "%";
                String percentKey = "%percentage_" + i + "%";
                
                if (processed.contains(playerKey) || processed.contains(damageKey) || processed.contains(percentKey)) {
                    if (i <= sorted.size()) {
                        Map.Entry<UUID, Double> entry = sorted.get(i - 1);
                        String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                        double dmg = entry.getValue();
                        double pct = (dmg / totalDamage) * 100.0;
                        
                        processed = processed.replace(playerKey, name != null ? name : "Unknown")
                                           .replace(damageKey, String.format("%.1f", dmg))
                                           .replace(percentKey, String.format("%.1f", pct));
                    } else {
                        // Fill empty slots
                        processed = processed.replace(playerKey, "---")
                                           .replace(damageKey, "0")
                                           .replace(percentKey, "0");
                    }
                }
            }
            finalMessages.add(processed);
        }

        // Broadcast
        if (boss.leaderboardBroadcastType.equals("RADIUS")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(boss.spawnLocation.getWorld()) && 
                    p.getLocation().distance(boss.spawnLocation) <= boss.leaderboardRadius) {
                    for (String m : finalMessages) {
                        plugin.getAdventure().player(p).sendMessage(ColorUtils.parseToComponent(m));
                    }
                }
            }
        } else {
            for (String m : finalMessages) {
                plugin.getAdventure().all().sendMessage(ColorUtils.parseToComponent(m));
            }
        }
    }

    public void cleanupHologramsOnly() {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        
        // 1. Collect all hologram names we are currently tracking
        Set<String> trackedNames = new HashSet<>();
        for (Hologram h : activeHolograms.values()) {
            trackedNames.add(h.getData().getName());
        }

        // 2. Brute force: Remove any hologram starting with our prefix that is NOT in our tracked list
        List<Hologram> toRemove = new ArrayList<>();
        for (Hologram h : manager.getHolograms()) {
            String name = h.getData().getName();
            if (name.startsWith("cyayo_timer_") && !trackedNames.contains(name)) {
                toRemove.add(h);
            }
        }
        
        for (Hologram h : toRemove) {
            manager.removeHologram(h);
        }
    }

    public void fullCleanup() {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        for (Hologram h : activeHolograms.values()) {
            manager.removeHologram(h);
        }
        activeHolograms.clear();
        
        // Final sweep
        List<Hologram> toRemove = new ArrayList<>();
        for (Hologram h : manager.getHolograms()) {
            if (h.getData().getName().startsWith("cyayo_timer_")) {
                toRemove.add(h);
            }
        }
        for (Hologram h : toRemove) {
            manager.removeHologram(h);
        }
        
        lastAnnouncedKeys.clear();
    }

    public void cleanup() {
        fullCleanup();
        lastSpawnedTimeSlot.clear();
        activeEvents.clear();
    }

    private BossData getBossById(String id) {
        for (BossData b : bosses) if (b.id.equals(id)) return b;
        return null;
    }

    private String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format("%02d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }

    private static class BossData {
        String id;
        String mythicMob;
        String displayName;
        Location spawnLocation;
        List<String> spawnTimes;
        List<String> days;
        int preSpawnMinutes;
        double offsetY;
        List<String> hologramLines;
        boolean useEventTime;
        int eventTimeMinutes;
        String escapeMessage;
        String escapeBroadcastType;
        double escapeRadius;
        List<String> eventHologramLines;
        
        boolean useLeaderboard;
        String leaderboardBroadcastType;
        double leaderboardRadius;
        List<String> leaderboardLines;
        
        boolean useRewards;
        int topPlayersReward;
        ConfigurationSection rewardSection;

        String deathMessage;
        String deathSound;
        String deathBroadcastType;
        double deathRadius;
        
        float hologramScale;
        boolean hologramShadow;
        boolean hologramSeeThrough;
        TextDisplay.TextAlignment hologramAlignment;
        org.bukkit.Color hologramBackgroundColor;
        int hologramBackgroundAlpha;
        
        Map<Integer, AnnouncementData> announcements = new HashMap<>();

        boolean useTrackedMob;
        String trackedMobInternalName;
    }

    private static class AnnouncementData {
        String message;
        String sound;
        String broadcastType;
        double radius;
    }

    private static class ActiveEventData {
        Object mobObject;
        ZonedDateTime startTime;
        ZonedDateTime expiryTime;
        Map<UUID, Double> damageMap = new HashMap<>();

        ActiveEventData(Object mobObject, ZonedDateTime startTime, ZonedDateTime expiryTime) {
            this.mobObject = mobObject;
            this.startTime = startTime;
            this.expiryTime = expiryTime;
        }
    }
}
