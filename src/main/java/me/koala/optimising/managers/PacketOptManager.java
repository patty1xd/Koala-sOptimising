package me.koala.optimising.managers;

import me.koala.optimising.KoalasOptimising;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reduces unnecessary movement update broadcasts for distant players
 * and deduplicates rapid hit/damage events within a short window.
 *
 * Note: Full packet-level interception requires ProtocolLib or similar.
 * This implementation works at the Bukkit event layer to reduce
 * unnecessary processing and event firing.
 */
public class PacketOptManager {

    private final KoalasOptimising plugin;
    private BukkitTask task;

    private boolean distantUpdateReduction;
    private double distanceThresholdSq;
    private int distantUpdateInterval;
    private long hitDedupWindowMs;

    // Tracks last position broadcast tick per player pair
    private final Map<UUID, Long> lastMoveUpdate = new HashMap<>();
    // Tracks last hit time per attacker
    private final Map<UUID, Long> lastHitTime = new HashMap<>();

    public PacketOptManager(KoalasOptimising plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        distantUpdateReduction = plugin.getConfig().getBoolean("packet-optimisation.distant-update-reduction", true);
        double dist = plugin.getConfig().getDouble("packet-optimisation.distance-threshold", 32.0);
        distanceThresholdSq = dist * dist;
        distantUpdateInterval = plugin.getConfig().getInt("packet-optimisation.distant-update-interval", 3);
        hitDedupWindowMs = plugin.getConfig().getLong("packet-optimisation.hit-dedup-window-ms", 50);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("packet-optimisation.enabled", true)) return;

        // Periodic cleanup of stale entries
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            lastHitTime.entrySet().removeIf(e -> now - e.getValue() > 5000);
            lastMoveUpdate.entrySet().removeIf(e -> now - e.getValue() > 5000);
        }, 400L, 400L);
    }

    /**
     * Check if a movement update for this player should be broadcast this tick.
     * Returns true if the update should proceed.
     */
    public boolean shouldBroadcastMove(Player player, long currentTick) {
        if (!distantUpdateReduction) return true;

        UUID id = player.getUniqueId();
        Long lastTick = lastMoveUpdate.get(id);

        // Check if any nearby player is within threshold
        boolean hasNearbyPlayer = false;
        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player)) continue;
            if (player.getLocation().distanceSquared(other.getLocation()) <= distanceThresholdSq) {
                hasNearbyPlayer = true;
                break;
            }
        }

        if (hasNearbyPlayer) {
            lastMoveUpdate.remove(id);
            return true; // Full updates for nearby players
        }

        // Distant: only update every N ticks
        if (lastTick == null || currentTick - lastTick >= distantUpdateInterval) {
            lastMoveUpdate.put(id, currentTick);
            return true;
        }
        return false;
    }

    /**
     * Check if a hit from this attacker should be processed (dedup).
     * Returns true if the hit should be processed.
     */
    public boolean shouldProcessHit(UUID attackerId) {
        long now = System.currentTimeMillis();
        Long last = lastHitTime.get(attackerId);

        if (last != null && now - last < hitDedupWindowMs) {
            return false; // Duplicate within window
        }

        lastHitTime.put(attackerId, now);
        return true;
    }

    public void stop() {
        if (task != null) task.cancel();
        lastMoveUpdate.clear();
        lastHitTime.clear();
    }
}
