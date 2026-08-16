package com.wnteam.mlanticheat.listener;

import com.wnteam.mlanticheat.MLAntiCheat;
import com.wnteam.mlanticheat.data.NameCache;
import com.wnteam.mlanticheat.data.PlayerData;
import com.wnteam.mlanticheat.data.PlayerDataManager;
import com.wnteam.mlanticheat.data.PlayerStatsStore;
import com.wnteam.mlanticheat.display.TagDisplayManager;
import com.wnteam.mlanticheat.ml.TrainingManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

public final class ConnectionListener implements Listener {
    private final MLAntiCheat plugin;
    private final PlayerDataManager data;
    private final PlayerStatsStore store;
    private final TagDisplayManager tags;
    private final TrainingManager training;
    private final NameCache names;

    public ConnectionListener(MLAntiCheat plugin, PlayerDataManager data, PlayerStatsStore store,
                              TagDisplayManager tags, TrainingManager training, NameCache names) {
        this.plugin = plugin; this.data = data; this.store = store; this.tags = tags; this.training = training; this.names = names;
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        data.get(event.getPlayer()); names.remember(event.getPlayer()); tags.attach(event.getPlayer());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer(); UUID uuid = player.getUniqueId(); PlayerData current = data.find(uuid);
        if (current != null) { store.save(player.getName(), current); store.flush(); }
        tags.detach(uuid); tags.forgetViewer(uuid); data.remove(uuid); training.forget(uuid);
        if (plugin.getPacketListener() != null) plugin.getPacketListener().forget(uuid);
        if (plugin.getTargetScanner() != null) plugin.getTargetScanner().forget(uuid);
        if (plugin.getCheckManager() != null) plugin.getCheckManager().forget(uuid);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) { data.get(event.getPlayer()).reset(); tags.attach(event.getPlayer()); }
}
