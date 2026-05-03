package me.koala.optimising.managers;

import me.koala.optimising.KoalasOptimising;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tracks server tick times (MSPT) with a rolling average.
 * Uses the server's built-in tick time array via Paper API.
 */
public class MSPTManager {

    private final KoalasOptimising plugin;
    private BukkitTask task;

    private final Deque<Double> samples = new ArrayDeque<>();
    private int sampleSize;
    private int sampleInterval;

    private double currentMSPT = 0.0;
    private double averageMSPT = 0.0;
    private double peakMSPT = 0.0;
    private long totalTicks = 0;

    // Spike tracking
    private double lastMSPT = 0.0;
    private int spikesDetected = 0;

    public MSPTManager(KoalasOptimising plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        sampleSize = plugin.getConfig().getInt("mspt-monitor.sample-size", 100);
        sampleInterval = plugin.getConfig().getInt("mspt-monitor.sample-interval", 20);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("mspt-monitor.enabled", true)) return;

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long[] tickTimes = plugin.getServer().getTickTimes();
            if (tickTimes == null || tickTimes.length == 0) return;

            // Average last N nanosecond tick times → convert to ms
            double sum = 0;
            for (long t : tickTimes) sum += t;
            double mspt = (sum / tickTimes.length) / 1_000_000.0;

            currentMSPT = mspt;
            totalTicks++;

            // Spike detection (>10ms jump)
            if (Math.abs(mspt - lastMSPT) > 10.0) {
                spikesDetected++;
            }
            lastMSPT = mspt;

            // Peak tracking
            if (mspt > peakMSPT) peakMSPT = mspt;

            // Rolling average
            samples.addLast(mspt);
            if (samples.size() > sampleSize) samples.pollFirst();

            double avg = 0;
            for (double s : samples) avg += s;
            averageMSPT = avg / samples.size();

        }, sampleInterval, sampleInterval);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public double getCurrentMSPT() { return currentMSPT; }
    public double getAverageMSPT() { return averageMSPT; }
    public double getPeakMSPT() { return peakMSPT; }
    public int getSpikesDetected() { return spikesDetected; }
    public long getTotalTicks() { return totalTicks; }
    public int getSampleCount() { return samples.size(); }

    /** Estimated TPS from current MSPT */
    public double getEstimatedTPS() {
        if (currentMSPT <= 0) return 20.0;
        return Math.min(20.0, 1000.0 / currentMSPT);
    }

    /** Returns "GOOD", "WARNING", or "CRITICAL" */
    public String getStatus() {
        double warn = plugin.getConfig().getDouble("mspt-monitor.warning-threshold", 40.0);
        double crit = plugin.getConfig().getDouble("mspt-monitor.critical-threshold", 50.0);
        if (averageMSPT >= crit) return "CRITICAL";
        if (averageMSPT >= warn) return "WARNING";
        return "GOOD";
    }

    public void resetPeak() {
        peakMSPT = 0;
        spikesDetected = 0;
    }
}
