package me.koala.optimising.managers;

import me.koala.optimising.KoalasOptimising;
import org.bukkit.Chunk;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Throttles chunk loading/generation to prevent exploration spikes.
 * Queues chunk requests and processes them at a controlled rate.
 * Tightens limits automatically under lag.
 */
public class ChunkThrottleManager {

    private final KoalasOptimising plugin;
    private final LagResponseManager lagResponseManager;
    private BukkitTask task;

    private int maxChunksPerTick;
    private int queueThreshold;
    private int delayTicks;

    private final Deque<Runnable> chunkQueue = new ArrayDeque<>();
    private long chunksLoadedThisTick = 0;
    private long totalThrottled = 0;

    public ChunkThrottleManager(KoalasOptimising plugin, LagResponseManager lagResponseManager) {
        this.plugin = plugin;
        this.lagResponseManager = lagResponseManager;
        reload();
    }

    public void reload() {
        maxChunksPerTick = plugin.getConfig().getInt("chunk-throttle.max-chunks-per-tick", 2);
        queueThreshold   = plugin.getConfig().getInt("chunk-throttle.queue-threshold", 10);
        delayTicks       = plugin.getConfig().getInt("chunk-throttle.delay-ticks", 5);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("chunk-throttle.enabled", true)) return;

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            chunksLoadedThisTick = 0;

            // Reduce throughput under lag
            int limit = maxChunksPerTick;
            LagResponseManager.LagLevel level = lagResponseManager.getCurrentLevel();
            if (level == LagResponseManager.LagLevel.CRITICAL) limit = 1;
            else if (level == LagResponseManager.LagLevel.SEVERE) limit = Math.max(1, limit - 1);

            // Drain queue up to limit
            int drained = 0;
            while (!chunkQueue.isEmpty() && drained < limit) {
                Runnable r = chunkQueue.poll();
                if (r != null) {
                    r.run();
                    drained++;
                }
            }
            chunksLoadedThisTick = drained;

        }, 1L, 1L);
    }

    /**
     * Submit a chunk load task for throttled processing.
     * If queue is small, runs immediately; otherwise queues.
     */
    public void submitChunkLoad(Runnable loadTask) {
        if (chunkQueue.size() < queueThreshold) {
            loadTask.run();
        } else {
            chunkQueue.addLast(loadTask);
            totalThrottled++;

            boolean debug = plugin.getConfig().getBoolean("debug.log-chunk-throttle", false);
            if (debug) {
                plugin.getLogger().info("[ChunkThrottle] Queue size: " + chunkQueue.size() + " (throttled: " + totalThrottled + ")");
            }
        }
    }

    public void stop() {
        if (task != null) task.cancel();
        chunkQueue.clear();
    }

    public int getQueueSize()       { return chunkQueue.size(); }
    public long getTotalThrottled() { return totalThrottled; }
    public long getLoadedThisTick() { return chunksLoadedThisTick; }
}
