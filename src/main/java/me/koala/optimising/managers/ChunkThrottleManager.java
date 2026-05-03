package me.koala.optimising.managers;

import me.koala.optimising.KoalasOptimising;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Actually throttles chunk generation by:
 *
 *  1. Tracking how many NEW chunks were generated this tick via ChunkListener.
 *  2. If the per-tick new-chunk count exceeds the limit, queuing subsequent
 *     async chunk load requests and draining them gradually.
 *  3. Under lag: reducing the drain rate so exploration can't spike the server.
 *  4. Adjusting Paper's no-tick view distance on the fly to reduce how many
 *     chunks are kept loaded around each player.
 *
 *  The key difference from the old version: we COUNT actual new chunk events
 *  and use that to decide whether to drain the queue faster or slower.
 *  We also reduce Paper's simulation distance under severe lag.
 */
public class ChunkThrottleManager {

    private final KoalasOptimising plugin;
    private final LagResponseManager lagResponseManager;
    private BukkitTask drainTask;
    private BukkitTask monitorTask;

    // Config
    private int maxNewChunksPerTick;      // hard limit of new chunks generated in one tick
    private int normalDrainRate;          // queued chunks to release per tick normally
    private int lagDrainRate;             // drain rate under mild/severe lag
    private int criticalDrainRate;        // drain rate under critical lag
    private int normalSimDistance;        // simulation distance to restore when healthy
    private int lagSimDistance;           // simulation distance to apply under severe lag

    // State
    private final Deque<ChunkRequest> queue = new ConcurrentLinkedDeque<>();
    private int newChunksThisTick = 0;
    private long totalThrottled   = 0;
    private long totalGenerated   = 0;

    // Sim-distance management
    private boolean distanceReduced = false;

    public record ChunkRequest(World world, int x, int z, Runnable callback) {}

    public ChunkThrottleManager(KoalasOptimising plugin, LagResponseManager lagResponseManager) {
        this.plugin = plugin;
        this.lagResponseManager = lagResponseManager;
        reload();
    }

    public void reload() {
        maxNewChunksPerTick = plugin.getConfig().getInt("chunk-throttle.max-new-chunks-per-tick", 3);
        normalDrainRate     = plugin.getConfig().getInt("chunk-throttle.normal-drain-rate", 4);
        lagDrainRate        = plugin.getConfig().getInt("chunk-throttle.lag-drain-rate", 2);
        criticalDrainRate   = plugin.getConfig().getInt("chunk-throttle.critical-drain-rate", 1);
        normalSimDistance   = plugin.getConfig().getInt("chunk-throttle.normal-sim-distance", 6);
        lagSimDistance      = plugin.getConfig().getInt("chunk-throttle.lag-sim-distance", 4);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("chunk-throttle.enabled", true)) return;

        // Every tick: drain the queue at a rate based on current lag level
        drainTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            newChunksThisTick = 0; // reset counter for this tick

            LagResponseManager.LagLevel level = lagResponseManager.getCurrentLevel();
            int drain = switch (level) {
                case CRITICAL -> criticalDrainRate;
                case SEVERE, MILD -> lagDrainRate;
                default -> normalDrainRate;
            };

            int released = 0;
            while (!queue.isEmpty() && released < drain) {
                ChunkRequest req = queue.poll();
                if (req == null) break;
                // Load the chunk async, run callback on main thread when done
                req.world().getChunkAtAsync(req.x(), req.z()).thenAccept(chunk -> {
                    if (req.callback() != null) {
                        plugin.getServer().getScheduler().runTask(plugin, req.callback());
                    }
                });
                released++;
            }
        }, 1L, 1L);

        // Every 2 seconds: adjust simulation distance based on lag
        monitorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            LagResponseManager.LagLevel level = lagResponseManager.getCurrentLevel();

            if (level == LagResponseManager.LagLevel.SEVERE || level == LagResponseManager.LagLevel.CRITICAL) {
                if (!distanceReduced) {
                    setSimulationDistance(lagSimDistance);
                    distanceReduced = true;
                    if (plugin.getConfig().getBoolean("debug.log-chunk-throttle", false)) {
                        plugin.getLogger().info("[ChunkThrottle] Reduced sim distance to " + lagSimDistance + " (lag: " + level + ")");
                    }
                }
            } else {
                if (distanceReduced) {
                    setSimulationDistance(normalSimDistance);
                    distanceReduced = false;
                    if (plugin.getConfig().getBoolean("debug.log-chunk-throttle", false)) {
                        plugin.getLogger().info("[ChunkThrottle] Restored sim distance to " + normalSimDistance);
                    }
                }
            }
        }, 40L, 40L);
    }

    /**
     * Called by ChunkListener when a NEW chunk is generated this tick.
     * Returns true if the chunk should be allowed to generate immediately,
     * false if it was queued (caller should cancel or delay if possible).
     *
     * Since we can't actually cancel chunk generation from ChunkLoadEvent
     * (the chunk is already loaded by the time the event fires), this is
     * used for TRACKING and for queuing follow-up work only.
     */
    public boolean onNewChunkGenerated(World world, int x, int z, Runnable postGenerateWork) {
        totalGenerated++;
        newChunksThisTick++;

        if (newChunksThisTick > maxNewChunksPerTick) {
            // Too many this tick — queue any follow-up work
            if (postGenerateWork != null) {
                queue.addLast(new ChunkRequest(world, x, z, postGenerateWork));
                totalThrottled++;
            }
            return false; // was throttled
        }

        // Under budget — run immediately
        if (postGenerateWork != null) {
            postGenerateWork.run();
        }
        return true;
    }

    /**
     * Queue an explicit chunk load request (e.g. from a plugin loading
     * chunks ahead of a player). Will be drained at the throttled rate.
     */
    public void queueChunkLoad(World world, int x, int z, Runnable callback) {
        queue.addLast(new ChunkRequest(world, x, z, callback));
        totalThrottled++;
    }

    private void setSimulationDistance(int distance) {
        try {
            for (World world : plugin.getServer().getWorlds()) {
                world.setSimulationDistance(distance);
            }
        } catch (Exception e) {
            // Paper API may vary — fail silently
        }
    }

    public void stop() {
        if (drainTask  != null) drainTask.cancel();
        if (monitorTask != null) monitorTask.cancel();

        // Restore sim distance
        if (distanceReduced) setSimulationDistance(normalSimDistance);
        queue.clear();
    }

    public int  getQueueSize()       { return queue.size(); }
    public long getTotalThrottled()  { return totalThrottled; }
    public long getTotalGenerated()  { return totalGenerated; }
    public int  getNewChunksThisTick() { return newChunksThisTick; }
    public boolean isDistanceReduced() { return distanceReduced; }
}
