package com.wnteam.mlanticheat.command;

import com.wnteam.mlanticheat.MLAntiCheat;
import com.wnteam.mlanticheat.config.TextConfig;
import com.wnteam.mlanticheat.data.NameCache;
import com.wnteam.mlanticheat.data.PlayerData;
import com.wnteam.mlanticheat.data.PlayerDataManager;
import com.wnteam.mlanticheat.data.PlayerStatsStore;
import com.wnteam.mlanticheat.display.TagDisplayManager;
import com.wnteam.mlanticheat.gui.AdminGui;
import com.wnteam.mlanticheat.ml.EnsembleModel;
import com.wnteam.mlanticheat.ml.TrainingManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MLACCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT = List.of("gui", "inspect", "stats", "alerts", "tags", "train", "model", "reload");

    private final MLAntiCheat plugin;
    private final PlayerDataManager data;
    private final PlayerStatsStore store;
    private final TrainingManager training;
    private final EnsembleModel model;
    private final NameCache names;
    private final AdminGui gui;
    private final TagDisplayManager tags;
    private final TextConfig messages;

    public MLACCommand(MLAntiCheat plugin, PlayerDataManager data, PlayerStatsStore store,
                       TrainingManager training, EnsembleModel model, NameCache names,
                       AdminGui gui, TagDisplayManager tags, TextConfig messages) {
        this.plugin = plugin;
        this.data = data;
        this.store = store;
        this.training = training;
        this.model = model;
        this.names = names;
        this.gui = gui;
        this.tags = tags;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String root = args.length == 0 ? "gui" : args[0].toLowerCase(Locale.ROOT);
        switch (root) {
            case "gui" -> openGui(sender, args);
            case "inspect" -> inspect(sender, args, true);
            case "stats" -> inspect(sender, args, false);
            case "alerts" -> toggleAlerts(sender);
            case "tags" -> toggleTags(sender);
            case "train" -> train(sender, args);
            case "model" -> showModel(sender);
            case "reload" -> reload(sender);
            default -> send(sender, "command.help", Map.of());
        }
        return true;
    }

    private void openGui(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.players-only", Map.of());
            return;
        }
        gui.open(player, args.length > 1 ? args[1] : "");
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
        if (!require(sender, "mlac.tags")) return;
        if (sender instanceof Player player) {
            send(sender, tags.toggle(player) ? "command.tags-enabled" : "command.tags-disabled", Map.of());
        } else {
            send(sender, "command.players-only", Map.of());
        }
    }

    private void showModel(CommandSender sender) {
        send(sender, "command.model", vars(
                "ready", model.isReady(),
                "samples", model.getTrainedSamples(),
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
            scores(sender, online == null ? args[1] : online.getName(), live.snapshotScores(),
                    live.getAnalyses(), live.getAlerts(), live.getCombinedAverage(), live.getCombinedMax());
            return;
        }
        PlayerStatsStore.Snapshot saved = store.find(uuid);
        if (saved == null) {
            send(sender, "command.no-statistics", Map.of());
            return;
        }
        scores(sender, saved.name(), saved.scores(), saved.analyses(), saved.alerts(),
                saved.average(), saved.maximum());
    }

    private void scores(CommandSender sender, String name, double[] scores, long analyses,
                        long alerts, double average, double maximum) {
        Map<String, Object> values = vars(
                "player", name, "prec", format(scores[0]), "dyn", format(scores[1]),
                "pat", format(scores[2]), "trk", format(scores[3]), "ml", format(scores[4]),
                "analyses", analyses, "alerts", alerts, "average", format(average),
                "maximum", format(maximum));
        send(sender, "command.stats-title", values);
        send(sender, "command.stats-scores", values);
        send(sender, "command.stats-summary", values);
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
        if (sender.hasPermission(permission)) return true;
        send(sender, "command.no-permission", Map.of());
        return false;
    }

    private void send(CommandSender sender, String path, Map<String, Object> values) {
        sender.sendMessage(messages.component(path, path, values));
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
            return ROOT.stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("train")) {
            return List.of("legit", "cheat", "stop");
        }
        if (args.length == 2 || args.length == 3) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
