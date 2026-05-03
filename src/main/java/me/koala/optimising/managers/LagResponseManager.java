package me.koala.optimising.managers;

import me.koala.optimising.KoalasOptimising;
import org.bukkit.scheduler.BukkitTask;

/**
 * Monitors MSPT and escalates server-wide lag responses:
 *  NORMAL  → no changes
 *  MILD    → reduce AI ranges slightly
 *  SEVERE  → heavy AI reduction, chunk throttle
 *  CRITICAL→ redstone reduction, entity cull, max throttle
 */
public class LagResponseManager {

    public enum LagLevel { NORMAL, MILD, SEVERE, CRITICAL }

    private final KoalasOptimising plugin;
    private final MSPTManager msptManager;
    private BukkitTask task;

    private LagLevel currentLevel = LagLevel.NORMAL;
    private int consecutiveBadSamples = 0;
    private int cooldownTicks = 0;

    private double mildThreshold;
    private double severeThreshold;
    private double criticalThreshold;
    private int triggerCount;
    private int cooldownMax;

    public LagResponseManager(KoalasOptimising plugin, MSPTManager msptManager) {
        this.plugin = plugin;
        this.msptManager = msptManager;
        reload();
    }

    public void reload() {
        mildThreshold     = plugin.getConfig().getDouble("lag-response.mild-threshold", 35.0);
        severeThreshold   = plugin.getConfig().getDouble("lag-response.severe-threshold", 45.0);
        criticalThreshold = plugin.getConfig().getDouble("lag-response.critical-threshold", 55.0);
        triggerCount      = plugin.getConfig().getInt("lag-response.trigger-count", 3);
        cooldownMax       = plugin.getConfig().getInt("lag-response.cooldown-ticks", 200);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("lag-response.enabled", true)) return;

        // Check every second (20 ticks)
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (cooldownTicks > 0) {
                cooldownTicks--;
                return;
            }

            double mspt = msptManager.getAverageMSPT();
            LagLevel detected = classify(mspt);

            if (detected != LagLevel.NORMAL) {
                consecutiveBadSamples++;
            } else {
                consecutiveBadSamples = Math.max(0, consecutiveBadSamples - 1);
            }

            LagLevel previous = currentLevel;

            if (consecutiveBadSamples >= triggerCount) {
                currentLevel = detected;
            } else if (consecutiveBadSamples == 0) {
                currentLevel = LagLevel.NORMAL;
            }

            if (currentLevel != previous) {
                onLevelChange(previous, currentLevel, mspt);
                cooldownTicks = cooldownMax;
            }

        }, 20L, 20L);
    }

    private LagLevel classify(double mspt) {
        if (mspt >= criticalThreshold) return LagLevel.CRITICAL;
        if (mspt >= severeThreshold)   return LagLevel.SEVERE;
        if (mspt >= mildThreshold)     return LagLevel.MILD;
        return LagLevel.NORMAL;
    }

    private void onLevelChange(LagLevel from, LagLevel to, double mspt) {
        boolean debug = plugin.getConfig().getBoolean("debug.log-lag-responses", true);
        if (debug) {
            plugin.getLogger().info(String.format(
                "[LagResponse] %s → %s (MSPT: %.2f)", from, to, mspt
            ));
        }
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public LagLevel getCurrentLevel() { return currentLevel; }
    public boolean isMildOrWorse()    { return currentLevel != LagLevel.NORMAL; }
    public boolean isSevereOrWorse()  { return currentLevel == LagLevel.SEVERE || currentLevel == LagLevel.CRITICAL; }
    public boolean isCritical()       { return currentLevel == LagLevel.CRITICAL; }
}
