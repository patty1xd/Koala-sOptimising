package me.koala.optimising.commands;

import me.koala.optimising.KoalasOptimising;
import me.koala.optimising.managers.EntityLimiterManager;
import me.koala.optimising.managers.LagResponseManager;
import me.koala.optimising.managers.MSPTManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Chunk;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class OptCommand implements CommandExecutor, TabCompleter {

    private final KoalasOptimising plugin;
    private final MSPTManager msptManager;
    private final EntityLimiterManager entityLimiterManager;
    private final LagResponseManager lagResponseManager;

    private static final String PREFIX = "§8[§b§lKopt§8] §r";

    public OptCommand(KoalasOptimising plugin, MSPTManager msptManager,
                      EntityLimiterManager entityLimiterManager, LagResponseManager lagResponseManager) {
        this.plugin = plugin;
        this.msptManager = msptManager;
        this.entityLimiterManager = entityLimiterManager;
        this.lagResponseManager = lagResponseManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("koalasopt.admin")) {
            sender.sendMessage(PREFIX + "§cNo permission.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "mspt"     -> sendMSPT(sender);
            case "entities" -> sendEntities(sender, args);
            case "reload"   -> doReload(sender);
            case "debug"    -> toggleDebug(sender);
            case "reset"    -> doReset(sender);
            default -> sender.sendMessage(PREFIX + "§eUsage: /kopt <status|mspt|entities|reload|debug|reset>");
        }
        return true;
    }

    private void sendStatus(CommandSender sender) {
        double mspt = msptManager.getAverageMSPT();
        double tps  = msptManager.getEstimatedTPS();
        String status = msptManager.getStatus();
        String lagLevel = lagResponseManager.getCurrentLevel().name();

        String msptColor = switch (status) {
            case "CRITICAL" -> "§c";
            case "WARNING"  -> "§e";
            default         -> "§a";
        };

        sender.sendMessage("");
        sender.sendMessage("§b§l╔══════ KoalasOptimising ══════╗");
        sender.sendMessage("§b§l║ §fStatus: " + msptColor + status);
        sender.sendMessage("§b§l║ §fMSPT (avg): " + msptColor + String.format("%.2f ms", mspt));
        sender.sendMessage("§b§l║ §fEst. TPS:   " + msptColor + String.format("%.1f", tps));
        sender.sendMessage("§b§l║ §fLag Level:  §e" + lagLevel);
        sender.sendMessage("§b§l║ §fPeak MSPT:  §d" + String.format("%.2f ms", msptManager.getPeakMSPT()));
        sender.sendMessage("§b§l║ §fSpikes:     §d" + msptManager.getSpikesDetected());
        sender.sendMessage("§b§l║ §fEntities culled: §7" + entityLimiterManager.getTotalCulled());
        sender.sendMessage("§b§l║ §fChunk queue: §7" + plugin.getChunkThrottleManager().getQueueSize());
        sender.sendMessage("§b§l║ §fTask queue:  §7" + plugin.getTaskSpreadManager().getQueueSize());
        sender.sendMessage("§b§l╚══════════════════════════════╝");
        sender.sendMessage("");
    }

    private void sendMSPT(CommandSender sender) {
        double cur = msptManager.getCurrentMSPT();
        double avg = msptManager.getAverageMSPT();
        double peak = msptManager.getPeakMSPT();

        sender.sendMessage(PREFIX + "§fMSPT — Current: §b" + String.format("%.2f", cur) +
                "ms §f| Avg: §a" + String.format("%.2f", avg) +
                "ms §f| Peak: §c" + String.format("%.2f", peak) + "ms");
        sender.sendMessage(PREFIX + "§fEst. TPS: §b" + String.format("%.1f", msptManager.getEstimatedTPS())
                + " §7| Status: " + coloredStatus());
    }

    private void sendEntities(CommandSender sender, String[] args) {
        if (sender instanceof Player player) {
            Chunk chunk = player.getLocation().getChunk();
            Map<String, Integer> stats = entityLimiterManager.getChunkStats(chunk);
            int total = stats.values().stream().mapToInt(Integer::intValue).sum();

            sender.sendMessage(PREFIX + "§fEntities in your chunk §7(" + chunk.getX() + "," + chunk.getZ() + ")§f:");
            stats.forEach((type, count) ->
                    sender.sendMessage("  §7" + type + ": §b" + count));
            sender.sendMessage("  §7Total: §e" + total);
            sender.sendMessage(PREFIX + "§7Lifetime culled: §c" + entityLimiterManager.getTotalCulled());
            sender.sendMessage(PREFIX + "§7Item merges: §a" + plugin.getMergeManager().getTotalItemMerges()
                    + " §7| XP merges: §a" + plugin.getMergeManager().getTotalXpMerges());
        } else {
            sender.sendMessage(PREFIX + "§cThis command requires a player.");
        }
    }

    private void doReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getMsptManager().reload();
        plugin.getLagResponseManager().reload();
        plugin.getEntityLimiterManager().reload();
        plugin.getMobAIManager().reload();
        plugin.getMergeManager().reload();
        plugin.getChunkThrottleManager().reload();
        plugin.getPacketOptManager().reload();
        plugin.getTaskSpreadManager().reload();
        plugin.getXpCoalesceManager().reload();
        sender.sendMessage(PREFIX + "§aConfiguration reloaded!");
    }

    private void toggleDebug(CommandSender sender) {
        boolean current = plugin.getConfig().getBoolean("debug.enabled", false);
        plugin.getConfig().set("debug.enabled", !current);
        plugin.saveConfig();
        sender.sendMessage(PREFIX + "§fDebug mode: " + (!current ? "§aON" : "§cOFF"));
    }

    private void doReset(CommandSender sender) {
        msptManager.resetPeak();
        sender.sendMessage(PREFIX + "§aPeak MSPT and spike counter reset.");
    }

    private String coloredStatus() {
        return switch (msptManager.getStatus()) {
            case "CRITICAL" -> "§cCRITICAL";
            case "WARNING"  -> "§eWARNING";
            default         -> "§aGOOD";
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("status", "mspt", "entities", "reload", "debug", "reset");
        }
        return List.of();
    }
}
