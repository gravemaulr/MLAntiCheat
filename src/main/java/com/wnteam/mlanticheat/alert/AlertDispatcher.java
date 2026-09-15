package com.wnteam.mlanticheat.alert;

import com.wnteam.mlanticheat.config.Settings;
import com.wnteam.mlanticheat.config.TextConfig;
import com.wnteam.mlanticheat.data.PlayerData;
import com.wnteam.mlanticheat.report.ReportManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class AlertDispatcher {
    private final JavaPlugin plugin;
    private final EvidenceRecorder evidence;
    private final TextConfig messages;
    private final Set<UUID> disabled = ConcurrentHashMap.newKeySet();
    private final AtomicLong dispatched = new AtomicLong(), suppressed = new AtomicLong();
    private volatile ReportManager reports;

    public AlertDispatcher(JavaPlugin plugin, EvidenceRecorder evidence, TextConfig messages) { this.plugin = plugin; this.evidence = evidence; this.messages = messages; }
    public void forget(UUID uuid) { disabled.remove(uuid); }
    public long getDispatched() { return dispatched.get(); }
    public long getSuppressed() { return suppressed.get(); }
    public boolean toggle(Player player) { if (!disabled.add(player.getUniqueId())) { disabled.remove(player.getUniqueId()); return true; } return false; }
    public boolean enabled(Player player) { return !disabled.contains(player.getUniqueId()); }

    public void setReports(ReportManager reports) { this.reports = reports; }

    public void handle(Player player, PlayerData data, Settings settings) { handle(player, data, settings, false); }

    public void handle(Player player, PlayerData data, Settings settings, boolean training) {
        double score = data.getLastPrediction();
        for (Settings.ActionRule rule : settings.actionRules) {
            if (!rule.enabled()) continue;
            int confirmations = data.updateRule(rule.id(), score >= rule.threshold());
            if (score < rule.threshold() || confirmations < rule.confirmations()) { suppressed.incrementAndGet(); continue; }
            if (!data.canRunRule(rule.id(), rule.cooldownMs())) continue;
            execute(player, data, settings, rule, confirmations, training);
        }
    }

    private void execute(Player player, PlayerData data, Settings settings, Settings.ActionRule rule, int confirmations, boolean training) {
        String prefix = settings.shadowMode ? settings.notifyPrefix + " shadow" : settings.notifyPrefix;
        Map<String, Object> values = Map.of("prefix", prefix, "player", player.getName(), "score", format(data.getLastPrediction()), "raw_score", format(data.getRawScore()), "ping", data.getLastPing(), "tps", format(data.getLastTps()), "rule", rule.id(), "confirmations", confirmations, "required", rule.confirmations());
        data.recordDetection(rule.id());
        if (rule.staffAlert()) {
            Component message = messages.component("alert.staff", "%prefix% %player%", values)
                    .clickEvent(ClickEvent.runCommand("/" + settings.commandName + " inspect " + player.getName()))
                    .hoverEvent(HoverEvent.showText(messages.component("alert.hover", "open player card", values)));
            data.recordAlert(); dispatched.incrementAndGet();
            for (Player staff : Bukkit.getOnlinePlayers()) if (staff.hasPermission("mlac.alerts") && enabled(staff)) staff.sendMessage(message);
        }
        if (rule.console()) plugin.getLogger().warning(messages.plain("alert.console", "%player% | %rule%", values));
        if (rule.evidence()) evidence.dumpAsync(player.getName(), data, data.getLastPrediction(), data.getRawScore(), rule.id());
        if (rule.report() && !training && reports != null) reports.report(player, rule.id(), data.getLastPrediction());
        if (settings.shadowMode || training) return;
        for (String command : rule.commands()) {
            String line = placeholders(command, player, data);
            if (Bukkit.isPrimaryThread()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
            else Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line));
        }
    }

    public boolean shouldCancel(Player player, PlayerData data, Settings settings) { return shouldCancel(player, data, settings, false); }

    public boolean shouldCancel(Player player, PlayerData data, Settings settings, boolean training) {
        if (settings.shadowMode || training) return false;
        for (Settings.ActionRule rule : settings.actionRules) if (rule.enabled() && rule.cancelHit() && data.getLastPrediction() >= rule.threshold()) return true;
        return false;
    }

    private String placeholders(String value, Player player, PlayerData data) {
        return value.replace("%player%", player.getName()).replace("%player_name%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%score%", format(data.getLastPrediction())).replace("%raw_score%", format(data.getRawScore()))
                .replace("%ping%", Integer.toString(data.getLastPing())).replace("%tps%", format(data.getLastTps()));
    }
    private String format(double value) { return String.format(Locale.US, "%.3f", value); }
}
