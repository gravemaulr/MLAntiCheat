package com.wnteam.mlanticheat.listener;

import com.wnteam.mlanticheat.MLAntiCheat;
import com.wnteam.mlanticheat.check.CheckManager;
import com.wnteam.mlanticheat.config.Settings;
import com.wnteam.mlanticheat.data.PlayerData;
import com.wnteam.mlanticheat.data.PlayerDataManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class CombatListener implements Listener {

    private final MLAntiCheat plugin;
    private final PlayerDataManager dataManager;
    private final CheckManager checkManager;

    public CombatListener(MLAntiCheat plugin, PlayerDataManager dataManager, CheckManager checkManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.checkManager = checkManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageCancel(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        Settings settings = plugin.getSettings();
        if (attacker.hasPermission("mlac.bypass")) {
            return;
        }
        PlayerData data = dataManager.find(attacker.getUniqueId());
        if (data != null && plugin.getAlertDispatcher().shouldCancel(attacker, data, settings)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        checkManager.handleAttack(attacker, target);
    }
}
