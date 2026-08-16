package com.wnteam.mlanticheat.display;

import com.wnteam.mlanticheat.MLAntiCheat;
import com.wnteam.mlanticheat.config.Settings;
import com.wnteam.mlanticheat.config.TextConfig;
import com.wnteam.mlanticheat.data.PlayerData;
import com.wnteam.mlanticheat.data.PlayerDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TagDisplayManager {

    private final MLAntiCheat plugin;
    private final PlayerDataManager dataManager;
    private final TextConfig messages;
    private final Map<UUID, TextDisplay> displays = new ConcurrentHashMap<>();
    private final Set<UUID> hiddenViewers = ConcurrentHashMap.newKeySet();
    private BukkitTask task;
    private boolean enabled;
    private volatile Settings settings;

    public TagDisplayManager(MLAntiCheat plugin, PlayerDataManager dataManager, Settings settings, TextConfig messages) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.messages = messages;
        this.settings = settings;
        this.enabled = settings.displayEnabled;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public void start() {
        stop();
        long interval = Math.max(1, settings.displayIntervalTicks);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void restart() {
        start();
    }

    private void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        Settings config = settings;
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = dataManager.get(player);
            data.decay(config.scoreDecay);
            if (!enabled) {
                continue;
            }
            TextDisplay display = displays.get(player.getUniqueId());
            if (display == null || !display.isValid()) {
                attach(player);
                continue;
            }
            display.teleport(anchor(player, config));
            display.text(buildLine(data, config));
            syncViewers(player.getUniqueId(), display, config);
        }
    }

    public void attach(Player player) {
        if (!enabled) {
            return;
        }
        Settings config = settings;
        detach(player.getUniqueId());
        TextDisplay display = player.getWorld().spawn(anchor(player, config), TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
            entity.setShadowed(false);
            entity.setSeeThrough(false);
            entity.setPersistent(false);
            entity.setViewRange(0.9f);
            entity.setVisibleByDefault(false);
            entity.setTeleportDuration(Math.max(1, config.displayIntervalTicks));
            applyTransformation(entity, config);
            entity.text(Component.empty());
        });
        displays.put(player.getUniqueId(), display);
        syncViewers(player.getUniqueId(), display, config);
    }

    private Location anchor(Player player, Settings config) {
        Location location = player.getLocation();
        location.setY(location.getY() + player.getHeight() + 0.55 + config.displayHeightOffset);
        location.setPitch(0.0f);
        location.setYaw(0.0f);
        return location;
    }

    private void applyTransformation(TextDisplay display, Settings config) {
        float scale = (float) config.displayScale;
        display.setTransformation(new Transformation(
                new Vector3f(0.0f, 0.0f, 0.0f),
                new AxisAngle4f(),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()));
    }

    private void syncViewers(UUID owner, TextDisplay display, Settings config) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean permitted = !config.displayStaffOnly || viewer.hasPermission("mlac.tags");
            boolean visible = permitted && !hiddenViewers.contains(viewer.getUniqueId()) && !viewer.getUniqueId().equals(owner);
            if (visible) {
                viewer.showEntity(plugin, display);
            } else {
                viewer.hideEntity(plugin, display);
            }
        }
    }

    public boolean toggle(Player viewer) {
        UUID uuid = viewer.getUniqueId();
        boolean visible;
        if (hiddenViewers.remove(uuid)) {
            visible = true;
        } else {
            hiddenViewers.add(uuid);
            visible = false;
        }
        syncViewer(viewer);
        return visible;
    }

    public void forgetViewer(UUID uuid) {
        hiddenViewers.remove(uuid);
    }

    private void syncViewer(Player viewer) {
        Settings config = settings;
        for (Map.Entry<UUID, TextDisplay> entry : displays.entrySet()) {
            TextDisplay display = entry.getValue();
            if (display == null || !display.isValid()) {
                continue;
            }
            boolean permitted = !config.displayStaffOnly || viewer.hasPermission("mlac.tags");
            boolean visible = permitted && !hiddenViewers.contains(viewer.getUniqueId()) && !viewer.getUniqueId().equals(entry.getKey());
            if (visible) {
                viewer.showEntity(plugin, display);
            } else {
                viewer.hideEntity(plugin, display);
            }
        }
    }

    public void detach(UUID uuid) {
        TextDisplay display = displays.remove(uuid);
        if (display != null) {
            display.remove();
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            for (UUID uuid : displays.keySet().toArray(new UUID[0])) {
                detach(uuid);
            }
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            attach(player);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void shutdown() {
        stop();
        for (UUID uuid : displays.keySet().toArray(new UUID[0])) {
            detach(uuid);
        }
        hiddenViewers.clear();
    }

    private Component buildLine(PlayerData data, Settings config) {
        double[] scores = data.snapshotScores();
        Component line = Component.empty();
        for (int i = 0; i < scores.length; i++) {
            if (i > 0) line = line.append(messages.component("tag.separator", " ", Map.of()));
            Map<String, Object> values = Map.of("name", PlayerData.SCORE_NAMES[i], "value", String.format(Locale.US, "%.2f", scores[i]));
            line = line.append(messages.component("tag.label", "%name% ", values)).append(messages.component("tag.value", "%value%", values));
        }
        return line;
    }

    private TextColor gradient(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        int red = (int) Math.round(90 + clamped * 165);
        int green = (int) Math.round(230 - clamped * 200);
        return TextColor.color(red, green, 90);
    }
}
