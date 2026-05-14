package com.cyayo.timer.util;

import com.cyayo.timer.CyayoTimer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CyayoPlaceholderExpansion extends PlaceholderExpansion {

    private final CyayoTimer plugin;

    public CyayoPlaceholderExpansion(CyayoTimer plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cyayotimer";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Cyayo";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        switch (params.toLowerCase()) {
            case "playtime_daily":
                return String.valueOf(plugin.getRewardManager().getPlayerPlaytime(player, "DAILY"));
            case "playtime_weekly":
                return String.valueOf(plugin.getRewardManager().getPlayerPlaytime(player, "WEEKLY"));
            case "playtime_monthly":
                return String.valueOf(plugin.getRewardManager().getPlayerPlaytime(player, "MONTHLY"));
        }

        return null;
    }
}
