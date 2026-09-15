package com.wnteam.mlanticheat.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class Settings {
    public record ActionRule(String id, double threshold, int confirmations, long cooldownMs,
                             boolean staffAlert, boolean console, boolean evidence,
                             boolean report, boolean cancelHit, boolean enabled, List<String> commands) {}

    public final double learningRate;
    public final double l2;
    public final double holdoutRatio;
    public final long evaluationIntervalMs;
    public final int minWindowSize;
    public final long combatWindowMs;
    public final int warmupHits;
    public final long warmupMs;
    public final int warmupRotations;
    public final double smoothing;
    public final double alertThreshold;
    public final int confirmationWindows;
    public final double scoreDecay;
    public final boolean scannerEnabled;
    public final int scannerIntervalTicks;
    public final double scannerRange;
    public final long scannerIdleMs;
    public final String commandName;
    public final List<String> commandAliases;
    public final boolean displayEnabled;
    public final boolean displayDefaultVisible;
    public final boolean displayHideOwn;
    public final String displayPermission;
    public final int displayIntervalTicks;
    public final double displayHeightOffset;
    public final double displayScale;
    public final List<String> displayLines;
    public final boolean shadowMode;
    public final String notifyPrefix;
    public final double pingStart;
    public final double pingFull;
    public final double pingMaxReduction;
    public final double tpsStart;
    public final double tpsFull;
    public final double tpsMaxReduction;
    public final double totalMaxReduction;
    public final boolean reportsEnabled;
    public final int reportMaximum;
    public final long reportCooldownMs;
    public final long reportResetMs;
    public final boolean reportResetOnPunish;
    public final boolean reportNotifyProgress;
    public final List<String> reportCommands;
    public final int datasetCapacity;
    public final boolean rebalanceOnStart;
    public final List<ActionRule> actionRules;

    public Settings(FileConfiguration config) {
        learningRate = config.getDouble("model.learning-rate", 0.035);
        l2 = config.getDouble("model.l2", 0.00002);
        holdoutRatio = config.getDouble("model.holdout-ratio", 0.15);
        evaluationIntervalMs = config.getLong("model.evaluation-interval-ms", 220);
        minWindowSize = config.getInt("model.minimum-rotations", 24);
        combatWindowMs = config.getLong("model.combat-window-ms", 2500);
        warmupHits = Math.max(0, config.getInt("model.warmup-hits", 4));
        warmupMs = Math.max(0L, config.getLong("model.warmup-ms", 900L));
        warmupRotations = Math.max(0, config.getInt("model.warmup-rotations", 40));
        smoothing = config.getDouble("model.smoothing", 0.82);
        scoreDecay = config.getDouble("model.idle-decay", 0.985);
        scannerEnabled = config.getBoolean("model.target-scanner", true);
        scannerIntervalTicks = 1;
        scannerRange = 4.0;
        scannerIdleMs = 30000;
        commandName = label(config.getString("command.name", "mlac"), "mlac");
        commandAliases = aliases(config.getStringList("command.aliases"), commandName);
        displayEnabled = config.getBoolean("display.enabled", true);
        displayDefaultVisible = config.getBoolean("display.default-visible", true);
        displayHideOwn = config.getBoolean("display.hide-own-tag", true);
        displayPermission = config.getString("display.permission", "mlac.tags");
        displayIntervalTicks = Math.max(1, config.getInt("display.update-ticks", 4));
        displayHeightOffset = config.getDouble("display.height", 0.2);
        displayScale = config.getDouble("display.scale", 0.55);
        displayLines = loadDisplayLines(config);
        shadowMode = config.getBoolean("alerts.shadow-mode", true);
        notifyPrefix = config.getString("alerts.prefix", "&c[MLAC]&r");
        pingStart = config.getDouble("conditions.ping.start-ms", 150);
        pingFull = Math.max(pingStart + 1, config.getDouble("conditions.ping.full-ms", 300));
        pingMaxReduction = clamp(config.getDouble("conditions.ping.max-reduction", 0.15));
        tpsStart = config.getDouble("conditions.tps.start", 18.5);
        tpsFull = Math.min(tpsStart - 0.1, config.getDouble("conditions.tps.full", 15.0));
        tpsMaxReduction = clamp(config.getDouble("conditions.tps.max-reduction", 0.20));
        totalMaxReduction = clamp(config.getDouble("conditions.max-total-reduction", 0.30));
        reportsEnabled = config.getBoolean("reports.enabled", true);
        reportMaximum = Math.max(1, config.getInt("reports.maximum", 15));
        reportCooldownMs = Math.max(0, config.getLong("reports.cooldown-seconds", 10)) * 1000L;
        reportResetMs = Math.max(1, config.getLong("reports.reset-interval-minutes", 15)) * 60000L;
        reportResetOnPunish = config.getBoolean("reports.reset-after-punishment", true);
        reportNotifyProgress = config.getBoolean("reports.notify-progress", true);
        reportCommands = List.copyOf(config.getStringList("reports.commands"));
        datasetCapacity = Math.max(1000, config.getInt("training.dataset-capacity", 20000));
        rebalanceOnStart = config.getBoolean("training.rebalance-on-start", true);
        actionRules = loadRules(config);
        ActionRule first = actionRules.stream().filter(ActionRule::staffAlert).findFirst()
                .orElse(actionRules.get(0));
        alertThreshold = first.threshold();
        confirmationWindows = first.confirmations();
    }

    private String label(String value, String fallback) {
        String cleaned = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        cleaned = cleaned.replaceAll("[^a-z0-9_-]", "");
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private List<String> aliases(List<String> raw, String primary) {
        List<String> result = new ArrayList<>();
        for (String value : raw) {
            String cleaned = label(value, "");
            if (!cleaned.isEmpty() && !cleaned.equals(primary) && !result.contains(cleaned)) result.add(cleaned);
        }
        return List.copyOf(result);
    }

    private List<String> loadDisplayLines(FileConfiguration config) {
        List<String> lines = config.getStringList("display.lines");
        if (lines.isEmpty()) return List.of("&7PREC %prec_color%%prec% &8\u00b7 &7DYN %dyn_color%%dyn% &8\u00b7 &7PAT %pat_color%%pat% &8\u00b7 &7TRK %trk_color%%trk% &8\u00b7 &7ML %ml_color%%ml%", "&f%player_name%");
        return List.copyOf(lines);
    }

    private List<ActionRule> loadRules(FileConfiguration config) {
        List<ActionRule> result = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("actions");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String path = "actions." + id;
                result.add(new ActionRule(id, clamp(config.getDouble(path + ".threshold", 0.82)),
                        Math.max(1, config.getInt(path + ".confirmations", 4)),
                        Math.max(0, config.getLong(path + ".cooldown-ms", 5000)),
                        config.getBoolean(path + ".staff-alert", false),
                        config.getBoolean(path + ".console", false),
                        config.getBoolean(path + ".evidence", false),
                        config.getBoolean(path + ".report", false),
                        config.getBoolean(path + ".cancel-hit", false),
                        config.getBoolean(path + ".enabled", true),
                        List.copyOf(config.getStringList(path + ".commands"))));
            }
        }
        if (result.isEmpty()) result.add(new ActionRule("alert", 0.82, 4, 5000, true, true, true, true, false, true, List.of()));
        result.sort(Comparator.comparingDouble(ActionRule::threshold));
        return List.copyOf(result);
    }

    public double correction(double ping, double tps) {
        double pingPart = scale(ping, pingStart, pingFull) * pingMaxReduction;
        double tpsPart = scale(tpsStart - tps, 0, tpsStart - tpsFull) * tpsMaxReduction;
        return Math.min(totalMaxReduction, pingPart + tpsPart);
    }

    private double scale(double value, double start, double end) {
        if (end <= start) return value >= end ? 1 : 0;
        return Math.max(0, Math.min(1, (value - start) / (end - start)));
    }

    private double clamp(double value) { return Math.max(0, Math.min(1, value)); }
}
