package com.wnteam.mlanticheat.display;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.wnteam.mlanticheat.MLAntiCheat;
import com.wnteam.mlanticheat.config.Settings;
import com.wnteam.mlanticheat.config.TextConfig;
import com.wnteam.mlanticheat.data.PlayerData;
import com.wnteam.mlanticheat.data.PlayerDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TagDisplayManager {
    private static final int STATE_CHECK_CYCLES = 3;

    private final MLAntiCheat plugin;
    private final PlayerDataManager dataManager;
    private final NamespacedKey displayKey;
    private final Map<UUID, Tag> tags = new ConcurrentHashMap<>();
    private final Set<UUID> enabledViewers = ConcurrentHashMap.newKeySet();
    private BukkitTask task;
    private boolean enabled;
    private volatile Settings settings;
    private int cycle;

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
        purgeWorlds();
        for (Player player : Bukkit.getOnlinePlayers()) attach(player);
        long interval = Math.max(1, settings.displayIntervalTicks);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void restart() {
        removeAll();
        start();
    }

    public void shutdown() {
        stopTask();
        removeAll();
        purgeWorlds();
        enabledViewers.clear();
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        Settings config = settings;
        boolean verify = ++cycle % STATE_CHECK_CYCLES == 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            dataManager.get(player).decay(config.scoreDecay);
            Tag tag = tags.get(player.getUniqueId());
            if (tag == null || !tag.display.isValid() || !player.equals(tag.display.getVehicle())
                    || !player.getWorld().equals(tag.display.getWorld())) {
                attach(player);
                continue;
            }
            if (verify && tag.skin != skinFingerprint(player)) {
                attach(player);
                continue;
            }
            tag.display.text(render(player));
        }
    }

    public void attach(Player player) {
        UUID uuid = player.getUniqueId();
        detach(uuid);
        if (!player.isOnline() || player.isDead()) return;
        float offsetY = (float) settings.displayHeightOffset;
        Component text = render(player);
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
            entity.text(text);
        });
        if (!player.addPassenger(display)) {
            display.remove();
            return;
        }
        tags.put(uuid, new Tag(display, skinFingerprint(player)));
        applyVisibility(display);
        refreshViewer(player);
    }

    public void refresh(Player player) {
        UUID uuid = player.getUniqueId();
        detach(uuid);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) attach(online);
        });
    }

    public void detach(UUID uuid) {
        Tag tag = tags.remove(uuid);
        if (tag == null) return;
        tag.display.remove();
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

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) enabledViewers.clear();
        for (Player viewer : Bukkit.getOnlinePlayers()) refreshViewer(viewer);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void purgeUntracked(List<Entity> entities) {
        Set<UUID> tracked = trackedIds();
        for (Entity entity : entities) {
            if (entity instanceof TextDisplay display && isOwned(display) && !tracked.contains(display.getUniqueId())) {
                display.remove();
            }
        }
    }

    private void removeAll() {
        for (Tag tag : tags.values()) tag.display.remove();
        tags.clear();
    }

    private void purgeWorlds() {
        Set<UUID> tracked = trackedIds();
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : new ArrayList<>(world.getEntitiesByClass(TextDisplay.class))) {
                if (isOwned(display) && !tracked.contains(display.getUniqueId())) display.remove();
            }
        }
    }

    private Set<UUID> trackedIds() {
        Set<UUID> tracked = new HashSet<>();
        for (Tag tag : tags.values()) tracked.add(tag.display.getUniqueId());
        return tracked;
    }

    private boolean isOwned(TextDisplay display) {
        return display.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE);
    }

    private void applyVisibility(TextDisplay display) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (visibleFor(viewer)) {
                viewer.showEntity(plugin, display);
            } else {
                viewer.hideEntity(plugin, display);
            }
        }
    }

    private void refreshViewer(Player viewer) {
        boolean visible = visibleFor(viewer);
        for (Tag tag : tags.values()) {
            if (visible) {
                viewer.showEntity(plugin, tag.display);
            } else {
                viewer.hideEntity(plugin, tag.display);
            }
        }
    }

    private boolean visibleFor(Player viewer) {
        return enabled && enabledViewers.contains(viewer.getUniqueId());
    }

    private int skinFingerprint(Player player) {
        int fingerprint = 0;
        for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
            if (property.getName().equals("textures")) {
                fingerprint = 31 * fingerprint + property.getValue().hashCode();
            }
        }
        return fingerprint;
    }

    private Component render(Player player) {
        return LegacyComponentSerializer.legacySection().deserialize(buildText(player));
    }

    private String buildText(Player player) {
        PlayerData data = dataManager.get(player);
        double[] scores = data.snapshotScores();
        List<String> lines = settings.displayLines;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) text.append('\n');
            text.append(formatLine(lines.get(i), player, scores));
        }
        return text.toString();
    }

    private String formatLine(String line, Player player, double[] scores) {
        String result = line
                .replace("%player_name%", player.getName())
                .replace("%player%", player.getName());
        for (int i = 0; i < scores.length && i < PlayerData.SCORE_NAMES.length; i++) {
            String name = PlayerData.SCORE_NAMES[i].toLowerCase(Locale.ROOT);
            result = result.replace("%" + name + "%", String.format(Locale.US, "%.2f", scores[i]));
            result = result.replace("%" + name + "_color%", scoreColor(scores[i]));
        }
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    private String scoreColor(double score) {
        if (score >= 0.95) return "\u00a74";
        if (score >= 0.80) return "\u00a7c";
        if (score >= 0.60) return "\u00a76";
        if (score >= 0.40) return "\u00a7e";
        if (score >= 0.20) return "\u00a72";
        return "\u00a7a";
    }

    private static final class Tag {
        private final TextDisplay display;
        private final int skin;

        private Tag(TextDisplay display, int skin) {
            this.display = display;
            this.skin = skin;
        }
    }
}
