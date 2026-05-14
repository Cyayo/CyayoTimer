package com.cyayo.timer.task;

import com.cyayo.timer.CyayoTimer;
import org.bukkit.scheduler.BukkitRunnable;

public class TimerTask extends BukkitRunnable {

    private final CyayoTimer plugin;

    public TimerTask(CyayoTimer plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Check Boss Schedules
        plugin.getBossManager().checkBosses();
        
        // You could also add other periodic checks here
    }
}
