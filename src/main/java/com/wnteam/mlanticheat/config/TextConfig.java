package com.wnteam.mlanticheat.config;

import com.wnteam.mlanticheat.util.TextColors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;

public final class TextConfig {

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
        try {
            return MiniMessage.miniMessage().deserialize(TextColors.miniMessage(value));
        } catch (Exception exception) {
            plugin.getLogger().warning("Invalid text in " + fileName + ": " + exception.getMessage());
            return TextColors.legacy(value);
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
