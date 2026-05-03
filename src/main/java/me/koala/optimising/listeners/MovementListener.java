package me.koala.optimising.listeners;

import me.koala.optimising.KoalasOptimising;
import me.koala.optimising.managers.PacketOptManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;

public class MovementListener implements Listener {

    private final KoalasOptimising plugin;
    private final PacketOptManager packetOptManager;

    private long tickCounter = 0;

    public MovementListener(KoalasOptimising plugin, PacketOptManager packetOptManager) {
        this.plugin = plugin;
        this.packetOptManager = packetOptManager;

        // Increment tick counter independently
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tickCounter++, 1L, 1L);
    }

    /**
     * Filter movement events for distant players.
     * If a player hasn't moved to a new block, cancel the event to avoid
     * unnecessary downstream processing.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only filter micro-movements (same block position)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            // Pure rotation — check if we should broadcast
            if (!packetOptManager.shouldBroadcastMove(event.getPlayer(), tickCounter)) {
                event.setCancelled(true);
            }
        }
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
