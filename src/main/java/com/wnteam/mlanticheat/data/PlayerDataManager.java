package com.wnteam.mlanticheat.data;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDataManager {

    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();

    public PlayerData get(Player player) {
        return getByUuid(player.getUniqueId());
    }

    public PlayerData getByUuid(UUID uuid) {
        return data.computeIfAbsent(uuid, PlayerData::new);
    }

    public PlayerData find(UUID uuid) {
        return data.get(uuid);
    }

    public Collection<PlayerData> all() {
        return data.values();
    }

    public int size() {
        return data.size();
    }

    public void remove(UUID uuid) {
        data.remove(uuid);
    }

    public void clear() {
        data.clear();
    }
}
