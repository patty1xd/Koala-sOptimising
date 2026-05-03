package me.koala.optimising.managers;

import me.koala.optimising.KoalasOptimising;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Periodically scans loaded worlds to merge nearby items and XP orbs,
 * significantly reducing entity count without removing any resources.
 */
public class MergeManager {

    private final KoalasOptimising plugin;
    private BukkitTask task;

    private double itemMergeRadiusSq;
    private double xpMergeRadiusSq;
    private int mergeInterval;
    private int minAgeTicks;

    private long totalItemMerges = 0;
    private long totalXpMerges   = 0;

    public MergeManager(KoalasOptimising plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        double itemR = plugin.getConfig().getDouble("item-merging.item-merge-radius", 3.0);
        double xpR   = plugin.getConfig().getDouble("item-merging.xp-merge-radius", 4.0);
        itemMergeRadiusSq = itemR * itemR;
        xpMergeRadiusSq   = xpR * xpR;
        mergeInterval = plugin.getConfig().getInt("item-merging.merge-interval", 20);
        minAgeTicks   = plugin.getConfig().getInt("item-merging.min-age-ticks", 20);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("item-merging.enabled", true)) return;

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (World world : plugin.getServer().getWorlds()) {
                mergeItems(world);
                mergeXP(world);
            }
        }, mergeInterval, mergeInterval);
    }

    private void mergeItems(World world) {
        List<Item> items = world.getEntitiesByClass(Item.class)
                .stream()
                .filter(i -> !i.isDead() && i.getTicksLived() >= minAgeTicks)
                .toList();

        Set<Item> merged = new HashSet<>();

        for (int i = 0; i < items.size(); i++) {
            Item base = items.get(i);
            if (merged.contains(base) || base.isDead()) continue;

            ItemStack baseStack = base.getItemStack();

            for (int j = i + 1; j < items.size(); j++) {
                Item other = items.get(j);
                if (merged.contains(other) || other.isDead()) continue;

                // Must be same item type
                if (!baseStack.isSimilar(other.getItemStack())) continue;

                // Must be close enough
                if (base.getLocation().distanceSquared(other.getLocation()) > itemMergeRadiusSq) continue;

                // Check stack size won't overflow
                int combined = baseStack.getAmount() + other.getItemStack().getAmount();
                if (combined > baseStack.getMaxStackSize()) continue;

                // Merge
                baseStack.setAmount(combined);
                base.setItemStack(baseStack);
                other.remove();
                merged.add(other);
                totalItemMerges++;
            }
        }
    }

    private void mergeXP(World world) {
        List<ExperienceOrb> orbs = world.getEntitiesByClass(ExperienceOrb.class)
                .stream()
                .filter(o -> !o.isDead() && o.getTicksLived() >= minAgeTicks)
                .toList();

        Set<ExperienceOrb> merged = new HashSet<>();

        for (int i = 0; i < orbs.size(); i++) {
            ExperienceOrb base = orbs.get(i);
            if (merged.contains(base) || base.isDead()) continue;

            for (int j = i + 1; j < orbs.size(); j++) {
                ExperienceOrb other = orbs.get(j);
                if (merged.contains(other) || other.isDead()) continue;

                if (base.getLocation().distanceSquared(other.getLocation()) > xpMergeRadiusSq) continue;

                // Merge XP into base orb
                base.setExperience(base.getExperience() + other.getExperience());
                other.remove();
                merged.add(other);
                totalXpMerges++;
            }
        }
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public long getTotalItemMerges() { return totalItemMerges; }
    public long getTotalXpMerges()   { return totalXpMerges; }
}
