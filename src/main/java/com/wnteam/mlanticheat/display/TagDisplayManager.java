package com.wnteam.mlanticheat.display;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.wnteam.mlanticheat.MLAntiCheat;
import com.wnteam.mlanticheat.config.Settings;
import com.wnteam.mlanticheat.config.TextConfig;
import com.wnteam.mlanticheat.data.PlayerData;
import com.wnteam.mlanticheat.data.PlayerDataManager;
import com.wnteam.mlanticheat.util.TextColors;
import net.kyori.adventure.text.Component;
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
    private final Set<UUID> overrides = ConcurrentHashMap.newKeySet();
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
        overrides.clear();
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
            if (verify) refreshViewer(player);
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
        applyVisibility(uuid, display);
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
        if (!overrides.remove(uuid)) overrides.add(uuid);
        refreshViewer(viewer);
        return visibleFor(viewer, null);
    }

    public void forgetViewer(UUID uuid) {
        overrides.remove(uuid);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) overrides.clear();
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

    private void applyVisibility(UUID owner, TextDisplay display) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (visibleFor(viewer, owner)) {
                viewer.showEntity(plugin, display);
            } else {
                viewer.hideEntity(plugin, display);
            }
        }
    }

    private void refreshViewer(Player viewer) {
        for (Map.Entry<UUID, Tag> entry : tags.entrySet()) {
            if (visibleFor(viewer, entry.getKey())) {
                viewer.showEntity(plugin, entry.getValue().display);
            } else {
                viewer.hideEntity(plugin, entry.getValue().display);
            }
        }
    }

    private boolean visibleFor(Player viewer, UUID owner) {
        Settings config = settings;
        if (!enabled) return false;
        if (owner != null && config.displayHideOwn && owner.equals(viewer.getUniqueId())) return false;
        String permission = config.displayPermission;
        if (permission != null && !permission.isBlank() && !viewer.hasPermission(permission)) return false;
        return config.displayDefaultVisible != overrides.contains(viewer.getUniqueId());
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
        return TextColors.legacy(buildText(player));
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
        return result;
    }

    private String scoreColor(double score) {
        if (score >= 0.95) return "&#8B0000";
        if (score >= 0.80) return "&#FF4C4C";
        if (score >= 0.60) return "&#FF9D2E";
        if (score >= 0.40) return "&#FFE04C";
        if (score >= 0.20) return "&#5BD75B";
        return "&#08CF78";
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
