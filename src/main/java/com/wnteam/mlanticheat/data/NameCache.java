package com.wnteam.mlanticheat.data;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NameCache {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, UUID> byName = new ConcurrentHashMap<>();
    private final Map<UUID, String> byUuid = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public NameCache(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    public void remember(Player player) {
        remember(player.getUniqueId(), player.getName());
    }

    public void remember(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) {
            return;
        }
        String previous = byUuid.put(uuid, name);
        if (previous != null && !previous.equalsIgnoreCase(name)) {
            byName.remove(previous.toLowerCase(Locale.ROOT));
        }
        byName.put(name.toLowerCase(Locale.ROOT), uuid);
        if (previous == null || !previous.equals(name)) {
            dirty = true;
        }
    }

    public UUID resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            remember(online);
            return online.getUniqueId();
        }
        UUID cached = byName.get(name.toLowerCase(Locale.ROOT));
        if (cached != null) {
            return cached;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline == null) {
            return null;
        }
        remember(offline.getUniqueId(), offline.getName());
        return offline.getUniqueId();
    }

    public String nameOf(UUID uuid) {
        String cached = byUuid.get(uuid);
        if (cached != null) {
            return cached;
        }
        return uuid == null ? "unknown" : uuid.toString();
    }

    public List<String> suggest(String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (String name : byUuid.values()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(needle)) {
                names.add(name);
            }
            if (names.size() >= 40) {
                break;
            }
        }
        return names;
    }

    public int size() {
        return byUuid.size();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = configuration.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String name = section.getString(key);
                if (name != null && !name.isBlank()) {
                    byUuid.put(uuid, name);
                    byName.put(name.toLowerCase(Locale.ROOT), uuid);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        if (!dirty) {
            return;
        }
        YamlConfiguration configuration = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : byUuid.entrySet()) {
            configuration.set("players." + entry.getKey(), entry.getValue());
        }
        try {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                return;
            }
            configuration.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to save player name cache: " + exception.getMessage());
        }
    }
}
