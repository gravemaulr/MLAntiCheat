package com.wnteam.mlanticheat.report;

import com.wnteam.mlanticheat.MLAntiCheat;
import com.wnteam.mlanticheat.config.Settings;
import com.wnteam.mlanticheat.config.TextConfig;
import com.wnteam.mlanticheat.data.NameCache;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReportManager {

    public static final String PERMISSION_USE = "mlac.report";
    public static final String PERMISSION_NOTIFY = "mlac.report.notify";
    public static final String PERMISSION_BYPASS = "mlac.report.bypass";
    public static final String PERMISSION_MANAGE = "mlac.report.manage";

    public enum Status { DISABLED, COOLDOWN, ACCEPTED, PUNISHED }

    public record Result(Status status, String player, int count, int max, long seconds) {
    }

    private static final class Entry {
        private int count;
        private int punishments;
        private long updated;
        private long lastReport;
    }

    private final MLAntiCheat plugin;
    private final TextConfig messages;
    private final NameCache names;
    private final File file;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private volatile long lastReset = System.currentTimeMillis();
    private volatile boolean dirty;

    public ReportManager(MLAntiCheat plugin, TextConfig messages, NameCache names) {
        this.plugin = plugin;
        this.messages = messages;
        this.names = names;
        this.file = new File(plugin.getDataFolder(), "reports.yml");
        load();
    }

    public void report(Player target, String rule, double score) {
        if (!Bukkit.isPrimaryThread()) {
            UUID uuid = target.getUniqueId();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    report(online, rule, score);
                }
            });
            return;
        }
        if (target.hasPermission(PERMISSION_BYPASS)) {
            return;
        }
        issue(target.getUniqueId(), target.getName(), rule, score, false);
    }

    public Result submit(UUID target, String targetName, double score) {
        return issue(target, targetName, "report", score, true);
    }

    private Result issue(UUID target, String targetName, String rule, double score, boolean forced) {
        Settings settings = plugin.getSettings();
        int max = settings.reportMaximum;
        if (!settings.reportsEnabled) {
            return new Result(Status.DISABLED, targetName, count(target), max, 0);
        }
        tick();
        long now = System.currentTimeMillis();
        Entry entry = entries.computeIfAbsent(target, ignored -> new Entry());
        int count;
        int punishments;
        boolean reached;
        synchronized (entry) {
            if (!forced && settings.reportCooldownMs > 0
                    && now - entry.lastReport < settings.reportCooldownMs) {
                long seconds = (settings.reportCooldownMs - (now - entry.lastReport) + 999) / 1000;
                return new Result(Status.COOLDOWN, targetName, entry.count, max, seconds);
            }
            entry.lastReport = now;
            entry.updated = now;
            entry.count++;
            count = entry.count;
            reached = count >= max;
            if (reached) {
                entry.punishments++;
                if (settings.reportResetOnPunish) {
                    entry.count = 0;
                }
            }
            punishments = entry.punishments;
            dirty = true;
        }
        if (reached) {
            punish(target, targetName, rule, score, count, max, punishments);
            return new Result(Status.PUNISHED, targetName, count, max, 0);
        }
        if (settings.reportNotifyProgress) {
            broadcast("report.notify", "%prefix% <gray>Anticheat issued a report to</gray> %player% <gray>%count%/%max%</gray>",
                    values(targetName, rule, score, count, max, punishments));
        }
        return new Result(Status.ACCEPTED, targetName, count, max, 0);
    }

    public void tick() {
        long interval = plugin.getSettings().reportResetMs;
        if (System.currentTimeMillis() - lastReset < interval) {
            return;
        }
        lastReset = System.currentTimeMillis();
        resetAll();
    }

    public void resetAll() {
        for (Entry entry : entries.values()) {
            synchronized (entry) {
                entry.count = 0;
            }
        }
    }

    public int count(UUID uuid) {
        Entry entry = entries.get(uuid);
        return entry == null ? 0 : entry.count;
    }

    public int punishments(UUID uuid) {
        Entry entry = entries.get(uuid);
        return entry == null ? 0 : entry.punishments;
    }

    public int maximum() {
        return plugin.getSettings().reportMaximum;
    }

    public boolean reset(UUID uuid) {
        Entry entry = entries.get(uuid);
        if (entry == null || entry.count == 0) {
            return false;
        }
        synchronized (entry) {
            entry.count = 0;
        }
        return true;
    }

    public int clear() {
        int size = entries.size();
        entries.clear();
        dirty = true;
        return size;
    }

    private void punish(UUID uuid, String name, String rule, double score, int count, int max, int punishments) {
        Settings settings = plugin.getSettings();
        Map<String, Object> values = values(name, rule, score, count, max, punishments);
        broadcast("report.punish", "%prefix% <gray>Anticheat issued a report to</gray> %player% <gray>and the limit was reached:</gray> %count%/%max%", values);
        plugin.getLogger().warning(messages.plain("report.console", "%player% reached %max% reports (%punishments% total)", values));
        if (settings.shadowMode) {
            return;
        }
        for (String line : settings.reportCommands) {
            dispatch(apply(line, name, uuid, rule, count, max));
        }
    }

    private void dispatch(String line) {
        String command = line.startsWith("/") ? line.substring(1) : line;
        if (command.isBlank()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        }
    }

    private String apply(String line, String name, UUID uuid, String rule, int count, int max) {
        return line.replace("%player_name%", name)
                .replace("%player%", name)
                .replace("%uuid%", uuid.toString())
                .replace("%rule%", rule)
                .replace("%count%", String.valueOf(count))
                .replace("%max%", String.valueOf(max));
    }

    private void broadcast(String path, String fallback, Map<String, Object> values) {
        Component component = messages.component(path, fallback, values);
        if (Bukkit.isPrimaryThread()) {
            send(component);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> send(component));
        }
    }

    private void send(Component component) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(PERMISSION_NOTIFY)) {
                staff.sendMessage(component);
            }
        }
    }

    private Map<String, Object> values(String name, String rule, double score, int count, int max, int punishments) {
        Map<String, Object> values = new HashMap<>();
        values.put("prefix", plugin.getSettings().notifyPrefix);
        values.put("player", name);
        values.put("rule", rule);
        values.put("score", String.format(Locale.US, "%.3f", score));
        values.put("count", count);
        values.put("max", max);
        values.put("left", Math.max(0, max - count));
        values.put("punishments", punishments);
        return values;
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("reports");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                Entry entry = new Entry();
                entry.punishments = Math.max(0, section.getInt("punishments"));
                entry.updated = section.getLong("updated");
                entries.put(UUID.fromString(key), entry);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        if (!dirty) {
            return;
        }
        YamlConfiguration configuration = new YamlConfiguration();
        for (Map.Entry<UUID, Entry> stored : entries.entrySet()) {
            Entry entry = stored.getValue();
            if (entry.punishments == 0) {
                continue;
            }
            String path = "reports." + stored.getKey();
            synchronized (entry) {
                configuration.set(path + ".name", names.nameOf(stored.getKey()));
                configuration.set(path + ".punishments", entry.punishments);
                configuration.set(path + ".updated", entry.updated);
            }
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                return;
            }
            configuration.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to save reports: " + exception.getMessage());
        }
    }
}
