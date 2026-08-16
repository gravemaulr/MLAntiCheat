package com.wnteam.mlanticheat.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PlayerStatsStore {
    public record Snapshot(UUID uuid, String name, double[] scores, double rawScore, long analyses, long alerts,
                           double average, double maximum, int ping, double tps, long lastSeen,
                           List<PlayerData.Detection> detections) {}
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    public PlayerStatsStore(JavaPlugin plugin) { this.plugin = plugin; file = new File(plugin.getDataFolder(), "players.yml"); yaml = YamlConfiguration.loadConfiguration(file); }
    public synchronized void save(String name, PlayerData data) {
        String path = "players." + data.getUuid();
        yaml.set(path + ".name", name); yaml.set(path + ".scores", toList(data.snapshotScores())); yaml.set(path + ".raw-score", data.getRawScore());
        yaml.set(path + ".analyses", data.getAnalyses()); yaml.set(path + ".alerts", data.getAlerts()); yaml.set(path + ".average", data.getCombinedAverage());
        yaml.set(path + ".maximum", data.getCombinedMax()); yaml.set(path + ".ping", data.getLastPing()); yaml.set(path + ".tps", data.getLastTps());
        yaml.set(path + ".last-seen", Math.max(System.currentTimeMillis(), data.getLastSeen()));
        List<String> history = new ArrayList<>();
        for (PlayerData.Detection detection : data.detectionSnapshot()) history.add(String.format(Locale.US, "%d|%s|%.6f|%.6f|%d|%.3f", detection.time(), detection.rule(), detection.rawScore(), detection.score(), detection.ping(), detection.tps()));
        yaml.set(path + ".history", history);
    }
    public synchronized Snapshot find(UUID uuid) { return read(uuid, yaml.getConfigurationSection("players." + uuid)); }
    public synchronized Snapshot findByName(String name) { return all().stream().filter(value -> value.name().equalsIgnoreCase(name)).findFirst().orElse(null); }
    public synchronized List<Snapshot> search(String query) { String value = query.toLowerCase(Locale.ROOT); return all().stream().filter(snapshot -> snapshot.name().toLowerCase(Locale.ROOT).contains(value)).toList(); }
    public synchronized List<Snapshot> all() {
        List<Snapshot> result = new ArrayList<>(); ConfigurationSection root = yaml.getConfigurationSection("players"); if (root == null) return result;
        for (String key : root.getKeys(false)) try { Snapshot snapshot = read(UUID.fromString(key), root.getConfigurationSection(key)); if (snapshot != null) result.add(snapshot); } catch (IllegalArgumentException ignored) {}
        return result;
    }
    private Snapshot read(UUID uuid, ConfigurationSection section) {
        if (section == null) return null; List<Double> list = section.getDoubleList("scores"); double[] scores = new double[5]; for (int i = 0; i < Math.min(scores.length, list.size()); i++) scores[i] = list.get(i);
        List<PlayerData.Detection> history = new ArrayList<>();
        for (String line : section.getStringList("history")) try { String[] p = line.split("\\|"); history.add(new PlayerData.Detection(Long.parseLong(p[0]), p[1], Double.parseDouble(p[2]), Double.parseDouble(p[3]), Integer.parseInt(p[4]), Double.parseDouble(p[5]))); } catch (RuntimeException ignored) {}
        return new Snapshot(uuid, section.getString("name", uuid.toString()), scores, section.getDouble("raw-score"), section.getLong("analyses"), section.getLong("alerts"), section.getDouble("average"), section.getDouble("maximum"), section.getInt("ping"), section.getDouble("tps", 20), section.getLong("last-seen"), List.copyOf(history));
    }
    public synchronized void flush() { try { if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs(); yaml.save(file); } catch (IOException exception) { plugin.getLogger().warning("Could not save player statistics: " + exception.getMessage()); } }
    private List<Double> toList(double[] values) { List<Double> list = new ArrayList<>(values.length); for (double value : values) list.add(value); return list; }
}
