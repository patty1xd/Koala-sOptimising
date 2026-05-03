package me.koala.optimising.listeners;

import me.koala.optimising.KoalasOptimising;
import me.koala.optimising.managers.PacketOptManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.UUID;

public class MovementListener implements Listener {

    private final KoalasOptimising plugin;
    private final PacketOptManager packetOptManager;

    public MovementListener(KoalasOptimising plugin, PacketOptManager packetOptManager) {
        this.plugin = plugin;
        this.packetOptManager = packetOptManager;
    }

    /**
     * Deduplicate rapid hit events within the configured window.
     * Prevents double-hits and exploit-based hit spam from causing
     * excessive damage processing.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        UUID id = attacker.getUniqueId();
        if (!packetOptManager.shouldProcessHit(id)) {
            event.setCancelled(true);
        }
    }
}
