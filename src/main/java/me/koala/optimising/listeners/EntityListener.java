package me.koala.optimising.listeners;

import me.koala.optimising.KoalasOptimising;
import me.koala.optimising.managers.EntityLimiterManager;
import me.koala.optimising.managers.PacketOptManager;
import org.bukkit.Chunk;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;

import java.util.Map;
import java.util.UUID;

public class EntityListener implements Listener {

    private final KoalasOptimising plugin;
    private final EntityLimiterManager entityLimiterManager;

    public EntityListener(KoalasOptimising plugin, EntityLimiterManager entityLimiterManager) {
        this.plugin = plugin;
        this.entityLimiterManager = entityLimiterManager;
    }

    /**
     * When a new entity spawns, immediately check if its chunk is over limit.
     * This prevents accumulation rather than just periodic cleanup.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();

        // Skip players and named entities
        if (entity instanceof Player) return;
        if (entity instanceof LivingEntity le && le.getCustomName() != null) return;

        Chunk chunk = entity.getLocation().getChunk();
        Map<String, Integer> stats = entityLimiterManager.getChunkStats(chunk);

        // Quick pre-check: cancel spawn if type is already over limit
        int maxItems = plugin.getConfig().getInt("entity-limiter.max-items-per-chunk", 20);
        int maxXP    = plugin.getConfig().getInt("entity-limiter.max-xp-per-chunk", 25);
        int maxMobs  = plugin.getConfig().getInt("entity-limiter.max-mobs-per-chunk", 15);
        int maxProj  = plugin.getConfig().getInt("entity-limiter.max-projectiles-per-chunk", 10);
        int maxTotal = plugin.getConfig().getInt("entity-limiter.max-per-chunk", 30);

        boolean cancel = false;

        if (entity instanceof Item && stats.getOrDefault("items", 0) >= maxItems) cancel = true;
        else if (entity instanceof ExperienceOrb && stats.getOrDefault("xp", 0) >= maxXP) cancel = true;
        else if (entity instanceof Projectile && stats.getOrDefault("projectiles", 0) >= maxProj) cancel = true;
        else if (entity instanceof Mob && stats.getOrDefault("mobs", 0) >= maxMobs) cancel = true;

        int total = stats.values().stream().mapToInt(Integer::intValue).sum();
        if (total >= maxTotal) cancel = true;

        if (cancel) {
            event.setCancelled(true);
        }
    }

    /**
     * Deduplicate rapid item merge events — don't spawn a new item stack
     * if an identical one is right next to the drop location.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDrop(ItemSpawnEvent event) {
        // This will be handled by MergeManager on the next cycle
        // Nothing to cancel here — just a hook for future extension
    }
}
