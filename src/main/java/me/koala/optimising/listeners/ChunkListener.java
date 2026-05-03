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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;

        Chunk chunk = event.getChunk();

        // Report the new chunk and pass any post-generation work through the throttle.
        // We can't cancel generation here (already done), but we CAN:
        //  a) track the rate so the monitor knows how bad exploration is
        //  b) defer any post-gen work (entity population, custom spawning, etc.)
        boolean underBudget = chunkThrottleManager.onNewChunkGenerated(
            chunk.getWorld(),
            chunk.getX(),
            chunk.getZ(),
            null // no post-gen work needed currently — hook is here for future use
        );

        if (plugin.getConfig().getBoolean("debug.log-chunk-throttle", false)) {
            plugin.getLogger().info(String.format(
                "[ChunkThrottle] New chunk %d,%d | this tick: %d | queued: %d | %s",
                chunk.getX(), chunk.getZ(),
                chunkThrottleManager.getNewChunksThisTick(),
                chunkThrottleManager.getQueueSize(),
                underBudget ? "OK" : "THROTTLED"
            ));
        }
    }
}
