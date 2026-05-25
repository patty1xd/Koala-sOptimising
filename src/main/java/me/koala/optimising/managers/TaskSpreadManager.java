package me.koala.optimising.managers;

import me.koala.optimising.KoalasOptimising;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages a queue of non-critical tasks and spreads them across multiple ticks.
 * If MSPT exceeds the defer threshold, tasks are delayed further.
 * Use submitTask() to enqueue anything non-critical (e.g. stats saves, checks).
 */
public class TaskSpreadManager {

    private final KoalasOptimising plugin;
    private final MSPTManager msptManager;
    private BukkitTask task;

    private int spreadTicks;
    private double deferThreshold;
    private int deferDelay;
    private int drainBudget;

    private final Deque<NamedTask> queue = new ArrayDeque<>();
    private final AtomicLong tasksRun = new AtomicLong(0);
    private final AtomicLong tasksDeferred = new AtomicLong(0);

    private long tickCounter = 0;

    public record NamedTask(String name, Runnable task, long scheduledAt) {}

    public TaskSpreadManager(KoalasOptimising plugin, MSPTManager msptManager) {
        this.plugin = plugin;
        this.msptManager = msptManager;
        reload();
    }

    public void reload() {
        spreadTicks    = plugin.getConfig().getInt("tick-stability.task-spread-ticks", 5);
        deferThreshold = plugin.getConfig().getDouble("tick-stability.defer-threshold", 35.0);
        deferDelay     = plugin.getConfig().getInt("tick-stability.defer-delay", 40);
        drainBudget    = Math.max(1, plugin.getConfig().getInt("tick-stability.drain-budget", 4));
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("tick-stability.enabled", true)) return;

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            tickCounter++;

            // Under spike: defer all tasks this tick
            if (msptManager.getAverageMSPT() > deferThreshold) {
                tasksDeferred.addAndGet(queue.size());
                return;
            }

            // Only process tasks on spread boundary ticks
            if (tickCounter % spreadTicks != 0) return;

            // Drain up to drainBudget tasks per spread cycle so a growing
            // queue under sustained load actually clears instead of falling
            // further behind one task at a time.
            for (int i = 0; i < drainBudget; i++) {
                NamedTask namedTask = queue.poll();
                if (namedTask == null) return;
                try {
                    namedTask.task().run();
                    tasksRun.incrementAndGet();
                } catch (Exception e) {
                    plugin.getLogger().warning("[TaskSpread] Task '" + namedTask.name() + "' threw: " + e.getMessage());
                }
            }

        }, 1L, 1L);
    }

    /**
     * Submit a non-critical task to be spread across ticks.
     */
    public void submitTask(String name, Runnable runnable) {
        queue.addLast(new NamedTask(name, runnable, tickCounter));
    }

    /**
     * Submit a task that runs after a minimum delay, only when server isn't spiking.
     */
    public void submitDelayed(String name, Runnable runnable, int minDelayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (msptManager.getAverageMSPT() > deferThreshold) {
                // Still spiking — push to queue for later
                submitTask(name, runnable);
            } else {
                runnable.run();
                tasksRun.incrementAndGet();
            }
        }, minDelayTicks);
    }

    public void stop() {
        if (task != null) task.cancel();
        queue.clear();
    }

    public int getQueueSize()       { return queue.size(); }
    public long getTasksRun()       { return tasksRun.get(); }
    public long getTasksDeferred()  { return tasksDeferred.get(); }
}
