package com.wnteam.mlanticheat.entity;

import com.wnteam.mlanticheat.MLAntiCheat;
import net.kyori.adventure.text.Component;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DummyManager implements Listener {
    private final MLAntiCheat plugin;
    private final Map<UUID, UUID> dummies = new HashMap<>();

    public DummyManager(MLAntiCheat plugin) {
        this.plugin = plugin;
    }

    public boolean toggle(Player player) {
        UUID current = dummies.remove(player.getUniqueId());
        if (current != null) {
            Entity entity = plugin.getServer().getEntity(current);
            if (entity != null) entity.remove();
            return false;
        }
        Location location = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(2));
        ArmorStand stand = player.getWorld().spawn(location, ArmorStand.class, entity -> {
            entity.customName(Component.text("MLAC Dummy"));
            entity.setCustomNameVisible(true);
            entity.setArms(true);
            entity.setBasePlate(true);
            entity.setGravity(true);
            entity.setPersistent(false);
            entity.setCollidable(true);
            entity.setCanPickupItems(false);
            entity.getPersistentDataContainer().set(plugin.dummyKey(), PersistentDataType.BYTE, (byte) 1);
        });
        dummies.put(player.getUniqueId(), stand.getUniqueId());
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!isDummy(event.getEntity())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !isDummy(event.getEntity())) return;
        Vector direction = event.getEntity().getLocation().toVector().subtract(player.getLocation().toVector());
        direction.setY(0);
        if (direction.lengthSquared() == 0) direction = player.getLocation().getDirection().setY(0);
        direction.normalize().multiply(0.45).setY(0.32);
        event.getEntity().playEffect(EntityEffect.HURT);
        event.getEntity().setVelocity(direction);
    }

    public boolean isDummy(Entity entity) {
        return entity.getPersistentDataContainer().has(plugin.dummyKey(), PersistentDataType.BYTE);
    }

    public void shutdown() {
        for (UUID uuid : dummies.values()) {
            Entity entity = plugin.getServer().getEntity(uuid);
            if (entity != null) entity.remove();
        }
        dummies.clear();
    }
}
