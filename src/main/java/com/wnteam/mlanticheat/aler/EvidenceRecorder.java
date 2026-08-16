package com.wnteam.mlanticheat.alert;

import com.wnteam.mlanticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

public final class EvidenceRecorder {

    private final JavaPlugin plugin;
    private final File folder;

    public EvidenceRecorder(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "evidence");
    }

    public void dumpAsync(String playerName, PlayerData data, double prediction, double anomaly, String source) {
        String report = build(playerName, data, prediction, anomaly, source);
        String fileName = playerName + "-" + System.currentTimeMillis() + ".txt";
        Runnable task = () -> store(fileName, report);
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            task.run();
        }
    }

    public String dumpNow(String playerName, PlayerData data, double prediction, double anomaly, String source) {
        String fileName = playerName + "-" + System.currentTimeMillis() + ".txt";
        return store(fileName, build(playerName, data, prediction, anomaly, source)) ? fileName : null;
    }

    private boolean store(String fileName, String report) {
        try {
            if (!folder.exists() && !folder.mkdirs()) {
                return false;
            }
            Files.writeString(new File(folder, fileName).toPath(), report, StandardCharsets.UTF_8);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to write evidence dump: " + exception.getMessage());
            return false;
        }
    }

    private String build(String playerName, PlayerData data, double prediction, double anomaly, String source) {
        StringBuilder builder = new StringBuilder(4096);
        builder.append("MLAntiCheat evidence dump").append(System.lineSeparator());
        builder.append("player=").append(playerName).append(System.lineSeparator());
        builder.append("uuid=").append(data.getUuid()).append(System.lineSeparator());
        builder.append("created=").append(Instant.now()).append(System.lineSeparator());
        builder.append("source=").append(source).append(System.lineSeparator());
        builder.append(String.format(Locale.US, "model=%.4f analyses=%d alerts=%d confirmations=%d",
                prediction, data.getAnalyses(), data.getAlerts(), data.getConfirmations()));
        builder.append(System.lineSeparator());
        double[] scores = data.snapshotScores();
        builder.append("scores=");
        for (int i = 0; i < scores.length; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(PlayerData.SCORE_NAMES[i]).append(':').append(String.format(Locale.US, "%.3f", scores[i]));
        }
        builder.append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("attacks time angle reach blocked target").append(System.lineSeparator());
        List<PlayerData.AttackSample> attacks = data.attackSnapshot();
        for (PlayerData.AttackSample attack : attacks) {
            builder.append(String.format(Locale.US, "%d %.2f %.3f %s %s",
                    attack.time(), attack.angle(), attack.reach(), attack.blocked(), attack.target()));
            builder.append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());
        builder.append("rotations tick time deltaYaw deltaPitch").append(System.lineSeparator());
        List<PlayerData.RotationSample> rotations = data.rotationSnapshot();
        for (PlayerData.RotationSample rotation : rotations) {
            builder.append(String.format(Locale.US, "%d %d %.5f %.5f",
                    rotation.tick(), rotation.time(), rotation.deltaYaw(), rotation.deltaPitch()));
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }
}
