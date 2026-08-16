package com.wnteam.mlanticheat.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextConfig {
    private static final Pattern HEX = Pattern.compile("(?i)[&§]#([0-9a-f]{6})");
    private static final Pattern LEGACY = Pattern.compile("(?i)[&§]([0-9a-fk-or])");
    private static final Map<Character, String> CODES = new LinkedHashMap<>();

    static {
        String[] names = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
        for (int i = 0; i < names.length; i++) CODES.put("0123456789abcdef".charAt(i), names[i]);
        CODES.put('k', "obfuscated"); CODES.put('l', "bold"); CODES.put('m', "strikethrough");
        CODES.put('n', "underlined"); CODES.put('o', "italic"); CODES.put('r', "reset");
    }

    private final JavaPlugin plugin;
    private final String fileName;
    private YamlConfiguration config;

    public TextConfig(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.isFile()) plugin.saveResource(fileName, false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public String string(String path, String fallback) {
        return config.getString(path, fallback);
    }

    public int integer(String path, int fallback) {
        return config.getInt(path, fallback);
    }

    public long longValue(String path, long fallback) {
        return config.getLong(path, fallback);
    }

    public double decimal(String path, double fallback) {
        return config.getDouble(path, fallback);
    }

    public boolean bool(String path, boolean fallback) {
        return config.getBoolean(path, fallback);
    }

    public List<String> list(String path) {
        return config.getStringList(path);
    }

    public Component component(String path, String fallback, Map<String, ?> values) {
        return parse(string(path, fallback), values);
    }

    public List<Component> components(String path, Map<String, ?> values) {
        return list(path).stream().map(line -> parse(line, values)).toList();
    }

    public Component parse(String input, Map<String, ?> values) {
        String value = replace(input, values);
        Matcher hex = HEX.matcher(value);
        StringBuffer converted = new StringBuffer();
        while (hex.find()) hex.appendReplacement(converted, "<#" + hex.group(1) + ">");
        hex.appendTail(converted);
        Matcher legacy = LEGACY.matcher(converted.toString());
        converted = new StringBuffer();
        while (legacy.find()) legacy.appendReplacement(converted, "<" + CODES.get(Character.toLowerCase(legacy.group(1).charAt(0))) + ">");
        legacy.appendTail(converted);
        try {
            return MiniMessage.miniMessage().deserialize(converted.toString());
        } catch (Exception exception) {
            plugin.getLogger().warning("Invalid text in " + fileName + ": " + exception.getMessage());
            return Component.text(value);
        }
    }

    public String plain(String path, String fallback, Map<String, ?> values) {
        return replace(string(path, fallback), values);
    }

    private String replace(String input, Map<String, ?> values) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, ?> entry : values.entrySet()) result = result.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        return result;
    }
}
