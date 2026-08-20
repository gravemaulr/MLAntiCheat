package com.wnteam.mlanticheat.display;

import com.wnteam.mlanticheat.MLAntiCheat;
import com.wnteam.mlanticheat.config.Settings;
import com.wnteam.mlanticheat.config.TextConfig;
import com.wnteam.mlanticheat.data.PlayerData;
import com.wnteam.mlanticheat.data.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TagDisplayManager {
    private final MLAntiCheat plugin;
    private final PlayerDataManager dataManager;
    private final NamespacedKey displayKey;
    private final Map<UUID, UUID> displays = new HashMap<>();
    private final Set<UUID> enabledViewers = new HashSet<>();
    private BukkitTask task;
    private boolean enabled;
    private volatile Settings settings;

    public TagDisplayManager(MLAntiCheat plugin, PlayerDataManager dataManager, Settings settings, TextConfig messages) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.settings = settings;
        this.enabled = settings.displayEnabled;
        this.displayKey = new NamespacedKey(plugin, "score_display");
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
        this.enabled = settings.displayEnabled;
    }

    public void start() {
        stopTask();
        removeOrphans();
        for (Player player : Bukkit.getOnlinePlayers()) attach(player);
        long interval = Math.max(1, settings.displayIntervalTicks);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void restart() {
        shutdownDisplays();
        start();
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        Settings config = settings;
        for (Player player : Bukkit.getOnlinePlayers()) {
            dataManager.get(player).decay(config.scoreDecay);
            TextDisplay display = findDisplay(player.getUniqueId());
            if (display == null || !display.isValid() || !player.equals(display.getVehicle())) {
                attach(player);
                display = findDisplay(player.getUniqueId());
            }
            if (display != null) {
                display.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .deserialize(buildText(player)));
                applyVisibility(display);
            }
        }
    }

    public void attach(Player player) {
        detach(player.getUniqueId());
        float offsetY = (float) settings.displayHeightOffset;
        TextDisplay display = player.getWorld().spawn(player.getLocation(), TextDisplay.class, entity -> {
            entity.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setSilent(true);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(false);
            entity.setShadowed(true);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setLineWidth(500);
            entity.setViewRange(1.0F);
            Transformation transformation = entity.getTransformation();
            transformation.getTranslation().set(new Vector3f(0.0F, offsetY, 0.0F));
            entity.setTransformation(transformation);
            entity.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize(buildText(player)));
        });
        player.addPassenger(display);
        displays.put(player.getUniqueId(), display.getUniqueId());
        applyVisibility(display);
    }

    public boolean toggle(Player viewer) {
        UUID uuid = viewer.getUniqueId();
        boolean visible;
        if (enabledViewers.remove(uuid)) {
            visible = false;
        } else {
            enabledViewers.add(uuid);
            visible = true;
        }
        refreshViewer(viewer);
        return visible;
    }

    public void forgetViewer(UUID uuid) {
        enabledViewers.remove(uuid);
    }

    public void detach(UUID uuid) {
        UUID displayId = displays.remove(uuid);
        if (displayId == null) return;
        Entity entity = Bukkit.getEntity(displayId);
        if (entity != null) entity.remove();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) enabledViewers.clear();
        refreshAllVisibility();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void shutdown() {
        stopTask();
        shutdownDisplays();
        removeOrphans();
        enabledViewers.clear();
    }

    private void shutdownDisplays() {
        for (UUID displayId : displays.values()) {
            Entity entity = Bukkit.getEntity(displayId);
            if (entity != null) entity.remove();
        }
        displays.clear();
    }

    private TextDisplay findDisplay(UUID playerId) {
        UUID displayId = displays.get(playerId);
        if (displayId == null) return null;
        Entity entity = Bukkit.getEntity(displayId);
        return entity instanceof TextDisplay display ? display : null;
    }

    private void applyVisibility(TextDisplay display) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (enabled && enabledViewers.contains(viewer.getUniqueId())) {
                viewer.showEntity(plugin, display);
            } else {
                viewer.hideEntity(plugin, display);
            }
        }
    }

    private void refreshViewer(Player viewer) {
        for (UUID displayId : displays.values()) {
            Entity entity = Bukkit.getEntity(displayId);
            if (!(entity instanceof TextDisplay display)) continue;
            if (enabled && enabledViewers.contains(viewer.getUniqueId())) {
                viewer.showEntity(plugin, display);
            } else {
                viewer.hideEntity(plugin, display);
            }
        }
    }

    private void refreshAllVisibility() {
        for (Player viewer : Bukkit.getOnlinePlayers()) refreshViewer(viewer);
    }

    private void removeOrphans() {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE)) display.remove();
            }
        }
    }

    private String buildText(Player player) {
        PlayerData data = dataManager.get(player);
        double[] scores = data.snapshotScores();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < scores.length; i++) {
            if (i > 0) line.append(" §8· ");
            line.append("§7").append(PlayerData.SCORE_NAMES[i]).append(" ")
                    .append(scoreColor(scores[i]))
                    .append(String.format(Locale.US, "%.2f", scores[i]));
        }
        line.append("\n§f").append(player.getName());
        return line.toString();
    }

    private String scoreColor(double score) {
        if (score >= 0.95) return "§4";
        if (score >= 0.80) return "§c";
        if (score >= 0.60) return "§6";
        if (score >= 0.40) return "§e";
        if (score >= 0.20) return "§2";
        return "§a";
    }
}
