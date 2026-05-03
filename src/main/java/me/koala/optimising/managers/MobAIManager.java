package me.koala.optimising.managers;

import me.koala.optimising.KoalasOptimising;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.List;

/**
 * Dynamically reduces mob AI update frequency based on:
 *  1. Distance from nearest player
 *  2. Current lag level from LagResponseManager
 *
 * Uses Paper's setAware / setShouldTickInventory or simply skipping
 * AI activation radius via manipulation of mob's AI flag.
 */
public class MobAIManager {

    private final KoalasOptimising plugin;
    private final LagResponseManager lagResponseManager;
    private BukkitTask task;

    private int fullAiRange;
    private int reducedAiRange;
    private int reducedAiInterval;
    private int minimalAiInterval;

    private long tickCounter = 0;
    private int modsApplied = 0;

    public MobAIManager(KoalasOptimising plugin, LagResponseManager lagResponseManager) {
        this.plugin = plugin;
        this.lagResponseManager = lagResponseManager;
        reload();
    }

    public void reload() {
        fullAiRange        = plugin.getConfig().getInt("mob-ai.full-ai-range", 24);
        reducedAiRange     = plugin.getConfig().getInt("mob-ai.reduced-ai-range", 48);
        reducedAiInterval  = plugin.getConfig().getInt("mob-ai.reduced-ai-interval", 4);
        minimalAiInterval  = plugin.getConfig().getInt("mob-ai.minimal-ai-interval", 10);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("mob-ai.enabled", true)) return;

        // Run every 4 ticks - iterating all entities every single tick is itself a lag source
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            tickCounter += 4;
            LagResponseManager.LagLevel level = lagResponseManager.getCurrentLevel();

            for (World world : plugin.getServer().getWorlds()) {
                List<Player> players = world.getPlayers();
                if (players.isEmpty()) continue;

                Collection<Entity> entities = world.getEntities();
                for (Entity entity : entities) {
                    if (!(entity instanceof Mob mob)) continue;
                    // Never touch Citizens NPCs or custom-named entities
                    if (entity.hasMetadata("NPC")) continue;
                    if (mob.getCustomName() != null) continue;

                    double nearestDist = nearestPlayerDistanceSq(mob.getLocation(), players);
                    applyAiPolicy(mob, nearestDist, level);
                }
            }
        }, 4L, 4L);
    }

    private void applyAiPolicy(Mob mob, double nearestDistSq, LagResponseManager.LagLevel level) {
        double fullRangeSq    = (double) fullAiRange * fullAiRange;
        double reducedRangeSq = (double) reducedAiRange * reducedAiRange;

        // During CRITICAL lag, tighten ranges
        if (level == LagResponseManager.LagLevel.CRITICAL) {
            fullRangeSq    = fullRangeSq * 0.5;
            reducedRangeSq = reducedRangeSq * 0.5;
        } else if (level == LagResponseManager.LagLevel.SEVERE) {
            fullRangeSq    = fullRangeSq * 0.75;
        }

        if (nearestDistSq <= fullRangeSq) {
            // Full AI — always enabled
            if (!mob.hasAI()) {
                mob.setAI(true);
                modsApplied++;
            }
        } else if (nearestDistSq <= reducedRangeSq) {
            // Reduced AI — enable every N ticks
            boolean shouldTick = (tickCounter % reducedAiInterval == 0);
            if (mob.hasAI() != shouldTick) {
                mob.setAI(shouldTick);
                modsApplied++;
            }
        } else {
            // Far away — minimal AI
            int interval = (level == LagResponseManager.LagLevel.CRITICAL)
                    ? minimalAiInterval * 2 : minimalAiInterval;
            boolean shouldTick = (tickCounter % interval == 0);
            if (mob.hasAI() != shouldTick) {
                mob.setAI(shouldTick);
                modsApplied++;
            }
        }
    }

    private double nearestPlayerDistanceSq(Location loc, List<Player> players) {
        double min = Double.MAX_VALUE;
        for (Player p : players) {
            if (!p.getWorld().equals(loc.getWorld())) continue;
            double d = p.getLocation().distanceSquared(loc);
            if (d < min) min = d;
        }
        return min;
    }

    public void stop() {
        if (task != null) task.cancel();
        // Re-enable AI for all mobs on shutdown
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e instanceof Mob mob && !mob.hasAI()) {
                    mob.setAI(true);
                }
            }
        }
    }

    public int getModsApplied() { return modsApplied; }
}
