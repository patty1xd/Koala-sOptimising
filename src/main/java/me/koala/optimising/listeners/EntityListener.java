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

    /**
     * Cap items and projectiles only.
     *
     * XP orbs are NEVER cancelled here — XpCoalesceManager merges them at
     * spawn time so they conserve experience instead of being deleted. The
     * old behavior cancelled new XP orbs once a chunk hit max-xp-per-chunk,
     * which silently lost XP from kills (the "random XP" bug).
     *
     * Mobs are handled by onCreatureSpawn — never touched here.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) return;
        if (entity instanceof LivingEntity) return;     // mobs handled separately
        if (entity instanceof ExperienceOrb) return;    // XP handled by XpCoalesceManager

        Chunk chunk = entity.getLocation().getChunk();
        Map<String, Integer> stats = entityLimiterManager.getChunkStats(chunk);

        int maxItems = plugin.getConfig().getInt("entity-limiter.max-items-per-chunk", 20);
        int maxProj  = plugin.getConfig().getInt("entity-limiter.max-projectiles-per-chunk", 10);

        if      (entity instanceof Item       && stats.getOrDefault("items",       0) >= maxItems) event.setCancelled(true);
        else if (entity instanceof Projectile && stats.getOrDefault("projectiles", 0) >= maxProj)  event.setCancelled(true);
    }

    /**
     * Cap NATURAL mob spawns only.
     *
     * Citizens uses SpawnReason.CUSTOM. /summon uses COMMAND.
     * Both are whitelisted — your NPCs will NEVER be blocked here.
     * Only ambient/natural spawns are counted against the cap.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();

        switch (reason) {
            case CUSTOM:
            case COMMAND:
            case SPAWNER_EGG:
            case DISPENSE_EGG:
            case BUILD_IRONGOLEM:
            case BUILD_SNOWMAN:
            case BUILD_WITHER:
            case BREEDING:
            case METAMORPHOSIS:
                return; // always allow
            default:
                break;
        }

        // Belt-and-suspenders: never block a Citizens NPC regardless of reason
        if (event.getEntity().hasMetadata("NPC")) return;

        Chunk chunk = event.getEntity().getLocation().getChunk();
        Map<String, Integer> stats = entityLimiterManager.getChunkStats(chunk);

        int maxMobs = plugin.getConfig().getInt("entity-limiter.max-mobs-per-chunk", 15);
        if (stats.getOrDefault("mobs", 0) >= maxMobs) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDrop(ItemSpawnEvent event) {
        // Hook for future extension — MergeManager handles cleanup
    }
}
