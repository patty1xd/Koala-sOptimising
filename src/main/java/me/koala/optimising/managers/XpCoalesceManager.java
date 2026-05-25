package me.koala.optimising.managers;

import me.koala.optimising.KoalasOptimising;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

/**
 * Guarantees XP consistency.
 *
 * When a new ExperienceOrb spawns, immediately look for an existing orb in the
 * same chunk within {@code xp-coalesce-radius} blocks. If one is found, the new
 * orb's experience is summed into it and the new orb's spawn is cancelled.
 * Otherwise the orb spawns normally.
 *
 * Net result:
 *   • XP is NEVER destroyed (every XP point is conserved on a surviving orb).
 *   • Chunks no longer accumulate large numbers of orbs after raid/grinder
 *     kills, so the entity limiter never has to delete XP to keep within
 *     per-chunk caps.
 *   • Players always get the same total XP from the same kills — no more
 *     random missing XP depending on how many orbs happen to be floating
 *     in the chunk at the time.
 */
public class XpCoalesceManager implements Listener {

    private final KoalasOptimising plugin;

    private boolean enabled;
    private double radiusSq;
    private int maxOrbValue;

    public XpCoalesceManager(KoalasOptimising plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("xp-coalesce.enabled", true);
        double radius = plugin.getConfig().getDouble("xp-coalesce.radius", 3.0);
        radiusSq = radius * radius;
        // Cap on a single orb's xp so a grinder doesn't end up with one
        // 50,000-xp orb that drains the level bar instantly when picked up.
        maxOrbValue = plugin.getConfig().getInt("xp-coalesce.max-orb-value", 2000);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (!enabled) return;
        if (!(event.getEntity() instanceof ExperienceOrb fresh)) return;

        int xp = fresh.getExperience();
        if (xp <= 0) return;

        Location loc = fresh.getLocation();
        Chunk chunk = loc.getChunk();

        // Find the OLDEST nearby orb that still has room for more xp.
        // Older = lower entityId = will be picked up first by the player, so
        // we top it up rather than spawn yet another orb.
        ExperienceOrb target = null;
        int lowestId = Integer.MAX_VALUE;
        for (Entity e : chunk.getEntities()) {
            if (e == fresh) continue;
            if (!(e instanceof ExperienceOrb existing)) continue;
            if (existing.isDead()) continue;
            if (existing.getExperience() + xp > maxOrbValue) continue;
            if (existing.getLocation().distanceSquared(loc) > radiusSq) continue;
            if (existing.getEntityId() < lowestId) {
                lowestId = existing.getEntityId();
                target = existing;
            }
        }

        if (target != null) {
            target.setExperience(target.getExperience() + xp);
            event.setCancelled(true);
        }
    }
}
