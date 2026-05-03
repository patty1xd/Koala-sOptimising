package me.koala.optimising.managers;

import me.koala.optimising.KoalasOptimising;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Enforces entity-per-chunk limits by scanning loaded chunks
 * and removing excess entities (items, XP, mobs, projectiles).
 */
public class EntityLimiterManager {

    private final KoalasOptimising plugin;
    private BukkitTask task;

    private int maxPerChunk;
    private int maxItems;
    private int maxXP;
    private int maxMobs;
    private int maxProjectiles;
    private int checkInterval;

    // Stats
    private long totalCulled = 0;

    public EntityLimiterManager(KoalasOptimising plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        maxPerChunk    = plugin.getConfig().getInt("entity-limiter.max-per-chunk", 30);
        maxItems       = plugin.getConfig().getInt("entity-limiter.max-items-per-chunk", 20);
        maxXP          = plugin.getConfig().getInt("entity-limiter.max-xp-per-chunk", 25);
        maxMobs        = plugin.getConfig().getInt("entity-limiter.max-mobs-per-chunk", 15);
        maxProjectiles = plugin.getConfig().getInt("entity-limiter.max-projectiles-per-chunk", 10);
        checkInterval  = plugin.getConfig().getInt("entity-limiter.check-interval", 40);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("entity-limiter.enabled", true)) return;

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkAllChunks,
                checkInterval, checkInterval);
    }

    private void checkAllChunks() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                processChunk(chunk);
            }
        }
    }

    private void processChunk(Chunk chunk) {
        Entity[] entities = chunk.getEntities();
        if (entities.length <= maxPerChunk) return;

        // Categorise
        List<Item> items = new ArrayList<>();
        List<ExperienceOrb> orbs = new ArrayList<>();
        List<LivingEntity> mobs = new ArrayList<>();
        List<Projectile> projectiles = new ArrayList<>();

        for (Entity e : entities) {
            if (e instanceof Player) continue;
            if (e instanceof Item i) items.add(i);
            else if (e instanceof ExperienceOrb o) orbs.add(o);
            else if (e instanceof Projectile p) projectiles.add(p);
            else if (e instanceof LivingEntity le) mobs.add(le);
        }

        boolean debug = plugin.getConfig().getBoolean("debug.log-entity-culls", false);

        // Cull excess — oldest first (lowest entity ID = spawned earlier)
        totalCulled += cull(items, maxItems, "items", chunk, debug);
        totalCulled += cull(orbs, maxXP, "xp orbs", chunk, debug);
        totalCulled += cull(projectiles, maxProjectiles, "projectiles", chunk, debug);
        totalCulled += cull(mobs, maxMobs, "mobs", chunk, debug);
    }

    private int cull(List<? extends Entity> list, int max, String type, Chunk chunk, boolean debug) {
        if (list.size() <= max) return 0;
        // Sort by entity ID ascending (older = lower ID)
        list.sort(Comparator.comparingInt(Entity::getEntityId));
        int toRemove = list.size() - max;
        for (int i = 0; i < toRemove; i++) {
            Entity e = list.get(i);
            if (!e.isDead()) {
                e.remove();
                if (debug) {
                    plugin.getLogger().info(String.format("[EntityLimiter] Culled %s at chunk %d,%d (had %d/%d)",
                            type, chunk.getX(), chunk.getZ(), list.size(), max));
                }
            }
        }
        return toRemove;
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public long getTotalCulled() { return totalCulled; }

    /** Returns entity counts for a specific chunk */
    public Map<String, Integer> getChunkStats(Chunk chunk) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        int items = 0, xp = 0, mobs = 0, proj = 0, other = 0;
        for (Entity e : chunk.getEntities()) {
            if (e instanceof Item) items++;
            else if (e instanceof ExperienceOrb) xp++;
            else if (e instanceof Projectile) proj++;
            else if (e instanceof LivingEntity && !(e instanceof Player)) mobs++;
            else other++;
        }
        stats.put("items", items);
        stats.put("xp", xp);
        stats.put("mobs", mobs);
        stats.put("projectiles", proj);
        stats.put("other", other);
        return stats;
    }
}
