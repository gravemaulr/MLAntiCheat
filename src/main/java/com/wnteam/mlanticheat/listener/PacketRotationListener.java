package com.wnteam.mlanticheat.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.wnteam.mlanticheat.MLAntiCheat;
import com.wnteam.mlanticheat.check.CheckManager;
import com.wnteam.mlanticheat.data.PlayerData;
import com.wnteam.mlanticheat.data.PlayerDataManager;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketRotationListener extends PacketListenerAbstract {

    private final MLAntiCheat plugin;
    private final PlayerDataManager dataManager;
    private final CheckManager checkManager;
    private final Map<UUID, float[]> lastRotation = new ConcurrentHashMap<>();

    public PacketRotationListener(MLAntiCheat plugin, PlayerDataManager dataManager, CheckManager checkManager) {
        super(PacketListenerPriority.MONITOR);
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.checkManager = checkManager;
    }

    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
        plugin.getLogger().info("PacketEvents rotation pipeline active.");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_ROTATION
                && event.getPacketType() != PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (wrapper.getLocation() == null) {
            return;
        }
        float yaw = wrapper.getLocation().getYaw();
        float pitch = wrapper.getLocation().getPitch();
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        float[] last = lastRotation.put(uuid, new float[]{yaw, pitch});
        if (last == null) {
            return;
        }
        float deltaYaw = wrapDegrees(yaw - last[0]);
        float deltaPitch = pitch - last[1];
        if (deltaYaw == 0.0f && deltaPitch == 0.0f) {
            return;
        }
        PlayerData data = dataManager.getByUuid(uuid);
        data.setPacketRotations(true);
        checkManager.handleRotation(player, deltaYaw, deltaPitch, pitch, plugin.currentTick());
    }

    public void forget(UUID uuid) {
        lastRotation.remove(uuid);
    }

    public void shutdown() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        lastRotation.clear();
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
