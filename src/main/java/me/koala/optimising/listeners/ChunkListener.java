package me.koala.optimising.listeners;

import me.koala.optimising.KoalasOptimising;
import me.koala.optimising.managers.ChunkThrottleManager;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class ChunkListener implements Listener {

    private final KoalasOptimising plugin;
    private final ChunkThrottleManager chunkThrottleManager;

    public ChunkListener(KoalasOptimising plugin, ChunkThrottleManager chunkThrottleManager) {
        this.plugin = plugin;
        this.chunkThrottleManager = chunkThrottleManager;
    }

    /**
     * When a NEW chunk is generated (not a pre-existing one being loaded),
     * submit it through the throttle manager.
     *
     * Note: We cannot easily cancel chunk loads from players moving,
     * but we can track and report on generation spikes.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;

        // New chunk generation — track it
        Chunk chunk = event.getChunk();
        boolean debug = plugin.getConfig().getBoolean("debug.log-chunk-throttle", false);

        if (debug) {
            plugin.getLogger().info(String.format("[ChunkThrottle] New chunk generated at %d,%d (queue: %d)",
                    chunk.getX(), chunk.getZ(), chunkThrottleManager.getQueueSize()));
        }

        // Submit any post-generation work to the throttle queue
        chunkThrottleManager.submitChunkLoad(() -> {
            // Post-chunk-generation work (e.g. custom spawning, decoration) goes here
            // Currently a no-op hook for extension
        });
    }
}
