package com.wnteam.mlanticheat.listener;

import com.wnteam.mlanticheat.MLAntiCheat;
import com.wnteam.mlanticheat.check.CheckManager;
import com.wnteam.mlanticheat.data.PlayerData;
import com.wnteam.mlanticheat.data.PlayerDataManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class RotationListener implements Listener {

    private final MLAntiCheat plugin;
    private final PlayerDataManager dataManager;
    private final CheckManager checkManager;

    public RotationListener(MLAntiCheat plugin, PlayerDataManager dataManager, CheckManager checkManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.checkManager = checkManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PlayerData data = dataManager.get(player);
        if (data.isPacketRotations()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        float deltaYaw = wrapDegrees(to.getYaw() - from.getYaw());
        float deltaPitch = to.getPitch() - from.getPitch();
        if (deltaYaw == 0.0f && deltaPitch == 0.0f) {
            return;
        }
        checkManager.handleRotation(player, deltaYaw, deltaPitch, to.getPitch(), plugin.currentTick());
    }

    private float wrapDegrees(float value) {
        float wrapped = value % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }
}
