package me.koala.optimising.listeners;

import me.koala.optimising.KoalasOptimising;
import me.koala.optimising.managers.EntityLimiterManager;
import org.bukkit.Chunk;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;

import java.util.Map;

public class EntityListener implements Listener {

    private final KoalasOptimising plugin;
    private final EntityLimiterManager entityLimiterManager;

    public EntityListener(KoalasOptimising plugin, EntityLimiterManager entityLimiterManager) {
        this.plugin = plugin;
        this.entityLimiterManager = entityLimiterManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();

        // Never touch players or NPCs of any kind
        if (entity instanceof Player) return;
        if (isNPC(entity)) return;

        Chunk chunk = entity.getLocation().getChunk();
        Map<String, Integer> stats = entityLimiterManager.getChunkStats(chunk);

        int maxItems = plugin.getConfig().getInt("entity-limiter.max-items-per-chunk", 20);
        int maxXP    = plugin.getConfig().getInt("entity-limiter.max-xp-per-chunk", 25);
        int maxMobs  = plugin.getConfig().getInt("entity-limiter.max-mobs-per-chunk", 15);
        int maxProj  = plugin.getConfig().getInt("entity-limiter.max-projectiles-per-chunk", 10);
        int maxTotal = plugin.getConfig().getInt("entity-limiter.max-per-chunk", 30);

        boolean cancel = false;

        if      (entity instanceof Item          && stats.getOrDefault("items", 0)        >= maxItems) cancel = true;
        else if (entity instanceof ExperienceOrb && stats.getOrDefault("xp", 0)           >= maxXP)   cancel = true;
        else if (entity instanceof Projectile    && stats.getOrDefault("projectiles", 0)  >= maxProj)  cancel = true;
        else if (entity instanceof Mob           && stats.getOrDefault("mobs", 0)         >= maxMobs)  cancel = true;

        int total = stats.values().stream().mapToInt(Integer::intValue).sum();
        if (total >= maxTotal) cancel = true;

        if (cancel) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDrop(ItemSpawnEvent event) {
        // Hook for future extension — MergeManager handles cleanup
    }

    /**
     * Returns true if this entity is an NPC and should never be touched.
     *
     * Covers:
     *  - Citizens 2 / Sentinel / any Citizens addon  → "NPC" metadata key
     *  - Any entity with a custom name (renamed villagers, armour-stand NPCs, etc.)
     *
     * This means the limiter will NEVER cancel a Citizens NPC spawn or cull one
     * during periodic chunk scans.
     */
    private boolean isNPC(Entity entity) {
        // Citizens 2 and its addons always set "NPC" metadata on the entity
        if (entity.hasMetadata("NPC")) return true;

        // Fallback: anything with a custom name is assumed to be intentional
        if (entity instanceof LivingEntity le && le.getCustomName() != null) return true;

        return false;
    }
}
