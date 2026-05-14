package com.cyayo.timer.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    public static Component parseToComponent(String input) {
        if (input == null) return Component.empty();
        
        // Handle hybrid by converting all legacy to MiniMessage tags first
        // This ensures gradients and bold work together correctly.
        String mmString = replaceLegacyWithTags(input);
        return MINI_MESSAGE.deserialize(mmString);
    }

    public static String replaceLegacyWithTags(String input) {
        if (input == null) return "";
        String result = input;
        
        // Handle Legacy Hex (§x§r§r§g§g§b§b)
        Pattern hexPattern = Pattern.compile("[§&]x[§&]([0-9a-fA-F])[§&]([0-9a-fA-F])[§&]([0-9a-fA-F])[§&]([0-9a-fA-F])[§&]([0-9a-fA-F])[§&]([0-9a-fA-F])");
        Matcher matcher = hexPattern.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = "#" + matcher.group(1) + matcher.group(2) + matcher.group(3) + matcher.group(4) + matcher.group(5) + matcher.group(6);
            matcher.appendReplacement(sb, "<color:" + hex + ">");
        }
        matcher.appendTail(sb);
        result = sb.toString();

        // Standard colors - We prepend <reset> because in legacy Minecraft, 
        // a color code (0-f) ALWAYS resets formatting (bold, italic, etc.)
        result = result.replace("&0", "<reset><black>").replace("&1", "<reset><dark_blue>").replace("&2", "<reset><dark_green>")
                .replace("&3", "<reset><dark_aqua>").replace("&4", "<reset><dark_red>").replace("&5", "<reset><dark_purple>")
                .replace("&6", "<reset><gold>").replace("&7", "<reset><gray>").replace("&8", "<reset><dark_gray>")
                .replace("&9", "<reset><blue>").replace("&a", "<reset><green>").replace("&b", "<reset><aqua>")
                .replace("&c", "<reset><red>").replace("&d", "<reset><light_purple>").replace("&e", "<reset><yellow>")
                .replace("&f", "<reset><white>");
        
        result = result.replace("§0", "<reset><black>").replace("§1", "<reset><dark_blue>").replace("§2", "<reset><dark_green>")
                .replace("§3", "<reset><dark_aqua>").replace("§4", "<reset><dark_red>").replace("§5", "<reset><dark_purple>")
                .replace("§6", "<reset><gold>").replace("§7", "<reset><gray>").replace("§8", "<reset><dark_gray>")
                .replace("§9", "<reset><blue>").replace("§a", "<reset><green>").replace("§b", "<reset><aqua>")
                .replace("§c", "<reset><red>").replace("§d", "<reset><light_purple>").replace("§e", "<reset><yellow>")
                .replace("§f", "<reset><white>");

        // Formatting - We use <reset> before formatting to emulate legacy behavior
        // (Legacy color codes reset formatting, but MiniMessage tags nest)
        // To fix "bold bleeding", we make sure formatting tags are clearly defined
        result = result.replace("&l", "<bold>").replace("§l", "<bold>")
                .replace("&m", "<strikethrough>").replace("§m", "<strikethrough>")
                .replace("&n", "<underlined>").replace("§n", "<underlined>")
                .replace("&o", "<italic>").replace("§o", "<italic>")
                .replace("&r", "<reset>").replace("§r", "<reset>");
        
        return result;
    }

    public static String translateLegacy(String input) {
        if (input == null) return "";
        
        // If it looks like MiniMessage or contains legacy codes we want to handle as MM
        if ((input.contains("<") && input.contains(">")) || input.contains("&") || input.contains("§")) {
            return SECTION_SERIALIZER.serialize(parseToComponent(input));
        }
        
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public static List<String> translateLegacyList(List<String> input) {
        List<String> result = new ArrayList<>();
        if (input == null) return result;
        for (String s : input) {
            result.add(translateLegacy(s));
        }
        return result;
    }
}
