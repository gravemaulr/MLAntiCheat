package com.wnteam.mlanticheat.check;

import com.wnteam.mlanticheat.MLAntiCheat;
import com.wnteam.mlanticheat.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TargetScanner {

    private final MLAntiCheat plugin;
    private final CheckManager checkManager;
    private final Set<UUID> aiming = new HashSet<>();
    private BukkitTask task;
    private volatile Settings settings;

    public TargetScanner(MLAntiCheat plugin, CheckManager checkManager, Settings settings) {
        this.plugin = plugin;
        this.checkManager = checkManager;
        this.settings = settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public void start() {
        stop();
        long period = Math.max(1, settings.scannerIntervalTicks);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::scan, 20L, period);
    }

    public void restart() {
        start();
    }

    private void scan() {
        Settings config = settings;
        if (!config.scannerEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
                aiming.remove(uuid);
                continue;
            }
            long lastAttack = plugin.getDataManager().getByUuid(uuid).getLastAttack();
            boolean recentlyFought = lastAttack > 0 && now - lastAttack <= config.scannerIdleMs;
            if (!recentlyFought && !isWeapon(player.getInventory().getItemInMainHand().getType())) {
                aiming.remove(uuid);
                continue;
            }
            RayTraceResult result = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(),
                    player.getEyeLocation().getDirection(),
                    config.scannerRange,
                    0.25,
                    entity -> entity instanceof LivingEntity && !entity.equals(player));
            boolean onTarget = result != null && result.getHitEntity() != null;
            if (onTarget) {
                if (aiming.add(uuid)) {
                    checkManager.handleTargetVisible(player);
                }
            } else {
                aiming.remove(uuid);
            }
        }
    }

    private boolean isWeapon(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || name.equals("TRIDENT") || name.equals("BOW")
                || name.equals("CROSSBOW") || name.equals("MACE");
    }

    public void forget(UUID uuid) {
        aiming.remove(uuid);
    }

    private void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stop();
        aiming.clear();
    }
}
