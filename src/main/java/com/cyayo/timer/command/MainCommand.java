package com.cyayo.timer.command;

import com.cyayo.timer.CyayoTimer;
import com.cyayo.timer.util.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MainCommand implements CommandExecutor, TabCompleter {

    private final CyayoTimer plugin;

    public MainCommand(CyayoTimer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("claim")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command!");
                return true;
            }
            plugin.getRewardGUI().open((Player) sender);
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("cyayotimer.admin")) {
                    sender.sendMessage(ColorUtils.translateLegacy("&cYou don't have permission!"));
                    return true;
                }
                plugin.getConfigManager().loadConfigs();
                plugin.getRewardManager().loadData();
                plugin.getBossManager().loadBosses();
                sender.sendMessage(ColorUtils.translateLegacy("&aCyayoTimer configurations reloaded!"));
                break;

            case "claim":
                if (!sender.hasPermission("cyayotimer.admin")) {
                    sender.sendMessage(ColorUtils.translateLegacy("&cYou don't have permission!"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.translateLegacy("&cUsage: /ctm claim <player>"));
                    return true;
                }
                Player targetPlayer = Bukkit.getPlayer(args[1]);
                if (targetPlayer == null) {
                    sender.sendMessage(ColorUtils.translateLegacy("&cPlayer not found!"));
                    return true;
                }
                plugin.getRewardGUI().open(targetPlayer);
                sender.sendMessage(ColorUtils.translateLegacy("&aOpened reward menu for &e" + targetPlayer.getName()));
                break;

            case "reset":
                if (!sender.hasPermission("cyayotimer.admin")) {
                    sender.sendMessage(ColorUtils.translateLegacy("&cYou don't have permission!"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ColorUtils.translateLegacy("&cUsage: /ctm reset <player> <reward_id>"));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                String rewardId = args[2];
                
                plugin.getRewardManager().resetReward(target.getUniqueId(), rewardId);
                sender.sendMessage(ColorUtils.translateLegacy("&aSuccessfully reset &e" + rewardId + " &afor &e" + target.getName()));
                break;

            case "cleanup":
                if (!sender.hasPermission("cyayotimer.admin")) {
                    sender.sendMessage(ColorUtils.translateLegacy("&cYou don't have permission!"));
                    return true;
                }
                plugin.getBossManager().cleanup();
                sender.sendMessage(ColorUtils.translateLegacy("&aSuccessfully cleared all CyayoTimer holograms!"));
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtils.translateLegacy("&6&lCyayoTimer &7- Version 1.0"));
        sender.sendMessage(ColorUtils.translateLegacy("&e/claim &7- Open reward menu"));
        if (sender.hasPermission("cyayotimer.admin")) {
            sender.sendMessage(ColorUtils.translateLegacy("&e/ctm reload &7- Reload configurations"));
            sender.sendMessage(ColorUtils.translateLegacy("&e/ctm claim <player> &7- Open menu for player"));
            sender.sendMessage(ColorUtils.translateLegacy("&e/ctm reset <player> <reward_id> &7- Reset player reward claim"));
            sender.sendMessage(ColorUtils.translateLegacy("&e/ctm cleanup &7- Clear all boss holograms"));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("cyayotimer")) {
            if (!sender.hasPermission("cyayotimer.admin")) return completions;
            if (args.length == 1) {
                completions.add("reload");
                completions.add("reset");
                completions.add("claim");
                completions.add("cleanup");
                return completions.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("claim"))) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
                return plugin.getConfigManager().getRewards().getConfigurationSection("rewards").getKeys(false).stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}
