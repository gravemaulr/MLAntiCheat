package com.wnteam.mlanticheat.command;

import com.wnteam.mlanticheat.MLAntiCheat;
import com.wnteam.mlanticheat.config.TextConfig;
import com.wnteam.mlanticheat.data.NameCache;
import com.wnteam.mlanticheat.data.PlayerData;
import com.wnteam.mlanticheat.data.PlayerDataManager;
import com.wnteam.mlanticheat.data.PlayerStatsStore;
import com.wnteam.mlanticheat.display.TagDisplayManager;
import com.wnteam.mlanticheat.entity.DummyManager;
import com.wnteam.mlanticheat.gui.AdminGui;
import com.wnteam.mlanticheat.ml.EnsembleModel;
import com.wnteam.mlanticheat.ml.TrainingManager;
import com.wnteam.mlanticheat.report.ReportManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MLACCommand implements CommandExecutor, TabCompleter {
    private static final String VIEW = "mlac.view";
    private static final List<String> STAFF_ROOT = List.of("gui", "inspect", "stats", "report", "reports", "alerts", "tags", "dummy", "train", "model", "reload");
    private static final List<String> PLAYER_ROOT = List.of("report");

    private final MLAntiCheat plugin;
    private final PlayerDataManager data;
    private final PlayerStatsStore store;
    private final TrainingManager training;
    private final EnsembleModel model;
    private final NameCache names;
    private final AdminGui gui;
    private final TagDisplayManager tags;
    private final DummyManager dummies;
    private final ReportManager reports;
    private final TextConfig messages;

    public MLACCommand(MLAntiCheat plugin, PlayerDataManager data, PlayerStatsStore store,
                       TrainingManager training, EnsembleModel model, NameCache names,
                       AdminGui gui, TagDisplayManager tags, DummyManager dummies,
                       ReportManager reports, TextConfig messages) {
        this.plugin = plugin;
        this.data = data;
        this.store = store;
        this.training = training;
        this.model = model;
        this.names = names;
        this.gui = gui;
        this.tags = tags;
        this.dummies = dummies;
        this.reports = reports;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender.hasPermission(VIEW)) {
                openGui(sender, args);
            } else {
                help(sender);
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gui" -> openGui(sender, args);
            case "inspect" -> inspect(sender, args, true);
            case "stats" -> inspect(sender, args, false);
            case "report" -> report(sender, args);
            case "reports" -> reports(sender, args);
            case "alerts" -> toggleAlerts(sender);
            case "tags" -> toggleTags(sender);
            case "dummy" -> toggleDummy(sender);
            case "train" -> train(sender, args);
            case "model" -> model(sender, args);
            case "reload" -> reload(sender);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        if (sender.hasPermission(VIEW)) {
            send(sender, "command.help", Map.of());
        } else if (sender.hasPermission(ReportManager.PERMISSION_USE)) {
            send(sender, "command.help-player", Map.of());
        } else {
            send(sender, "command.no-permission", Map.of());
        }
    }

    private void openGui(CommandSender sender, String[] args) {
        if (!require(sender, VIEW)) return;
        if (!(sender instanceof Player player)) {
            send(sender, "command.players-only", Map.of());
            return;
        }
        gui.open(player, args.length > 1 ? args[1] : "");
    }

    private void report(CommandSender sender, String[] args) {
        if (!require(sender, ReportManager.PERMISSION_USE)) return;
        if (args.length != 2) {
            send(sender, "command.report-usage", Map.of());
            return;
        }
        UUID uuid = names.resolve(args[1]);
        if (uuid == null) {
            send(sender, "command.unknown-player", Map.of());
            return;
        }
        Player online = Bukkit.getPlayer(uuid);
        PlayerData targetData = data.find(uuid);
        double score = targetData == null ? 0.0 : targetData.getLastPrediction();
        ReportManager.Result result = reports.submit(uuid, online == null ? names.nameOf(uuid) : online.getName(), score);
        Map<String, Object> values = vars("player", result.player(), "count", result.count(),
                "max", result.max(), "left", Math.max(0, result.max() - result.count()),
                "seconds", result.seconds(), "rule", "report", "score", format(score));
        boolean notified = sender instanceof Player && sender.hasPermission(ReportManager.PERMISSION_NOTIFY)
                && (result.status() == ReportManager.Status.PUNISHED
                || result.status() == ReportManager.Status.ACCEPTED && plugin.getSettings().reportNotifyProgress);
        switch (result.status()) {
            case DISABLED -> send(sender, "report.disabled", values);
            case COOLDOWN -> send(sender, "report.cooldown", values);
            case ACCEPTED -> {
                if (!notified) send(sender, "report.accepted", values);
            }
            case PUNISHED -> {
                if (!notified) send(sender, "report.reached", values);
            }
        }
    }

    private void reports(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.equals("reset")) {
            if (!require(sender, ReportManager.PERMISSION_MANAGE)) return;
            if (args.length < 3) {
                send(sender, "command.reports-usage", Map.of());
                return;
            }
            UUID uuid = names.resolve(args[2]);
            if (uuid == null) {
                send(sender, "command.unknown-player", Map.of());
                return;
            }
            send(sender, reports.reset(uuid) ? "report.reset" : "report.nothing-to-reset", vars("player", names.nameOf(uuid)));
            return;
        }
        if (action.equals("clear")) {
            if (!require(sender, ReportManager.PERMISSION_MANAGE)) return;
            send(sender, "report.cleared", vars("count", reports.clear()));
            return;
        }
        if (!require(sender, VIEW)) return;
        if (args.length < 2) {
            send(sender, "command.reports-usage", Map.of());
            return;
        }
        UUID uuid = names.resolve(args[1]);
        if (uuid == null) {
            send(sender, "command.unknown-player", Map.of());
            return;
        }
        send(sender, "report.status", vars("player", names.nameOf(uuid), "count", reports.count(uuid),
                "max", reports.maximum(), "punishments", reports.punishments(uuid)));
    }

    private void toggleAlerts(CommandSender sender) {
        if (!require(sender, "mlac.alerts")) return;
        if (sender instanceof Player player) {
            send(sender, plugin.getAlertDispatcher().toggle(player)
                    ? "command.alerts-enabled" : "command.alerts-disabled", Map.of());
        } else {
            send(sender, "command.players-only", Map.of());
        }
    }

    private void toggleTags(CommandSender sender) {
        if (!require(sender, plugin.getSettings().displayPermission)) return;
        if (sender instanceof Player player) {
            send(sender, tags.toggle(player) ? "command.tags-enabled" : "command.tags-disabled", Map.of());
        } else {
            send(sender, "command.players-only", Map.of());
        }
    }

    private void toggleDummy(CommandSender sender) {
        if (!require(sender, "mlac.dummy")) return;
        if (sender instanceof Player player) {
            send(sender, dummies.toggle(player) ? "command.dummy-spawned" : "command.dummy-removed", Map.of());
        } else {
            send(sender, "command.players-only", Map.of());
        }
    }

    private void model(CommandSender sender, String[] args) {
        if (!require(sender, VIEW)) return;
        if (args.length > 1 && args[1].equalsIgnoreCase("reset")) {
            if (!require(sender, "mlac.train")) return;
            model.reset();
            send(sender, "command.model-reset", Map.of());
            return;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("rebalance")) {
            if (!require(sender, "mlac.train")) return;
            long start = System.currentTimeMillis();
            int pairs = training.rebalance();
            send(sender, "command.model-rebalanced", vars("pairs", pairs, "samples", pairs * 2,
                    "ms", System.currentTimeMillis() - start));
            return;
        }
        send(sender, "command.model", vars(
                "ready", model.isReady(),
                "samples", model.getTrainedSamples(),
                "cheat", training.bufferedCheatSamples(),
                "legit", training.bufferedCleanSamples(),
                "pairs", training.balancedPairs(),
                "surplus", training.queuedSamples(),
                "precision", format(model.precision()),
                "recall", format(model.recall()),
                "fpr", format(model.falsePositiveRate())));
    }

    private void reload(CommandSender sender) {
        if (!require(sender, "mlac.reload")) return;
        plugin.reloadRuntime();
        send(sender, "command.reloaded", Map.of());
    }

    private void inspect(CommandSender sender, String[] args, boolean openGui) {
        if (!require(sender, VIEW)) return;
        if (args.length < 2) {
            send(sender, openGui ? "command.inspect-usage" : "command.stats-usage", Map.of());
            return;
        }
        UUID uuid = names.resolve(args[1]);
        if (uuid == null) {
            send(sender, "command.unknown-player", Map.of());
            return;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (openGui && sender instanceof Player viewer) {
            gui.inspect(viewer, uuid);
            return;
        }
        PlayerData live = data.find(uuid);
        if (live != null) {
            scores(sender, online == null ? args[1] : online.getName(), uuid, live.snapshotScores(),
                    live.getAnalyses(), live.getAlerts(), live.getCombinedAverage(), live.getCombinedMax());
            return;
        }
        PlayerStatsStore.Snapshot saved = store.find(uuid);
        if (saved == null) {
            send(sender, "command.no-statistics", Map.of());
            return;
        }
        scores(sender, saved.name(), uuid, saved.scores(), saved.analyses(), saved.alerts(),
                saved.average(), saved.maximum());
    }

    private void scores(CommandSender sender, String name, UUID uuid, double[] scores, long analyses,
                        long alerts, double average, double maximum) {
        Map<String, Object> values = vars(
                "player", name, "prec", format(scores[0]), "dyn", format(scores[1]),
                "pat", format(scores[2]), "trk", format(scores[3]), "ml", format(scores[4]),
                "analyses", analyses, "alerts", alerts, "average", format(average),
                "maximum", format(maximum), "count", reports.count(uuid), "max", reports.maximum(),
                "punishments", reports.punishments(uuid));
        send(sender, "command.stats-title", values);
        send(sender, "command.stats-scores", values);
        send(sender, "command.stats-summary", values);
        send(sender, "command.stats-reports", values);
    }

    private void train(CommandSender sender, String[] args) {
        if (!require(sender, "mlac.train")) return;
        if (args.length < 3) {
            send(sender, "command.train-usage", Map.of());
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            send(sender, "command.player-online-required", Map.of());
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "legit" -> training.setLabel(target.getUniqueId(), 0.0);
            case "cheat" -> training.setLabel(target.getUniqueId(), 1.0);
            case "stop" -> training.clearLabel(target.getUniqueId());
            default -> {
                send(sender, "command.invalid-label", Map.of());
                return;
            }
        }
        send(sender, "command.training-updated", vars("player", target.getName()));
    }

    private boolean require(CommandSender sender, String permission) {
        if (permission == null || permission.isBlank() || sender.hasPermission(permission)) return true;
        send(sender, "command.no-permission", Map.of());
        return false;
    }

    private void send(CommandSender sender, String path, Map<String, Object> values) {
        Map<String, Object> merged = new HashMap<>(values);
        merged.put("prefix", plugin.getSettings().notifyPrefix);
        merged.put("command", plugin.getSettings().commandName);
        sender.sendMessage(messages.component(path, path, merged));
    }

    private String format(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private static Map<String, Object> vars(Object... values) {
        Map<String, Object> result = new HashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> roots = sender.hasPermission(VIEW) ? STAFF_ROOT : PLAYER_ROOT;
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return roots.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (root) {
                case "train" -> {
                    return filter(List.of("legit", "cheat", "stop"), args[1]);
                }
                case "model" -> {
                    return filter(List.of("rebalance", "reset"), args[1]);
                }
                case "reports" -> {
                    List<String> options = new ArrayList<>(players(args[1]));
                    if (sender.hasPermission(ReportManager.PERMISSION_MANAGE)) {
                        options.addAll(filter(List.of("reset", "clear"), args[1]));
                    }
                    return options;
                }
                default -> {
                    return players(args[1]);
                }
            }
        }
        if (args.length == 3 && (root.equals("train") || root.equals("reports"))) {
            return players(args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String needle = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.startsWith(needle)).toList();
    }

    private List<String> players(String prefix) {
        String needle = prefix.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(needle)).toList();
    }
}
