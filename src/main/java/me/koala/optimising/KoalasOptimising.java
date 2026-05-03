package me.koala.optimising;

import me.koala.optimising.commands.OptCommand;
import me.koala.optimising.listeners.EntityListener;
import me.koala.optimising.listeners.MovementListener;
import me.koala.optimising.listeners.ChunkListener;
import me.koala.optimising.managers.*;
import me.koala.optimising.tasks.*;
import org.bukkit.plugin.java.JavaPlugin;

public class KoalasOptimising extends JavaPlugin {

    private static KoalasOptimising instance;

    private MSPTManager msptManager;
    private EntityLimiterManager entityLimiterManager;
    private MobAIManager mobAIManager;
    private MergeManager mergeManager;
    private ChunkThrottleManager chunkThrottleManager;
    private LagResponseManager lagResponseManager;
    private PacketOptManager packetOptManager;
    private TaskSpreadManager taskSpreadManager;

    @Override
    public void onEnable() {
        instance = this;

        // Config
        saveDefaultConfig();

        // Managers
        msptManager = new MSPTManager(this);
        lagResponseManager = new LagResponseManager(this, msptManager);
        entityLimiterManager = new EntityLimiterManager(this);
        mobAIManager = new MobAIManager(this, lagResponseManager);
        mergeManager = new MergeManager(this);
        chunkThrottleManager = new ChunkThrottleManager(this, lagResponseManager);
        packetOptManager = new PacketOptManager(this);
        taskSpreadManager = new TaskSpreadManager(this, msptManager);

        // Start all managers
        msptManager.start();
        lagResponseManager.start();
        entityLimiterManager.start();
        mobAIManager.start();
        mergeManager.start();
        chunkThrottleManager.start();
        packetOptManager.start();
        taskSpreadManager.start();

        // Listeners
        getServer().getPluginManager().registerEvents(new EntityListener(this, entityLimiterManager), this);
        getServer().getPluginManager().registerEvents(new MovementListener(this, packetOptManager), this);
        getServer().getPluginManager().registerEvents(new ChunkListener(this, chunkThrottleManager), this);

        // Commands
        getCommand("kopt").setExecutor(new OptCommand(this, msptManager, entityLimiterManager, lagResponseManager));
        getCommand("kopt").setTabCompleter(new OptCommand(this, msptManager, entityLimiterManager, lagResponseManager));

        getLogger().info("╔══════════════════════════════════╗");
        getLogger().info("║    KoalasOptimising v" + getDescription().getVersion() + "      ║");
        getLogger().info("║   Server optimisation active!    ║");
        getLogger().info("╚══════════════════════════════════╝");
    }

    @Override
    public void onDisable() {
        if (msptManager != null) msptManager.stop();
        if (lagResponseManager != null) lagResponseManager.stop();
        if (entityLimiterManager != null) entityLimiterManager.stop();
        if (mobAIManager != null) mobAIManager.stop();
        if (mergeManager != null) mergeManager.stop();
        if (chunkThrottleManager != null) chunkThrottleManager.stop();
        if (packetOptManager != null) packetOptManager.stop();
        if (taskSpreadManager != null) taskSpreadManager.stop();

        getLogger().info("KoalasOptimising disabled.");
    }

    public static KoalasOptimising getInstance() { return instance; }

    public MSPTManager getMsptManager() { return msptManager; }
    public EntityLimiterManager getEntityLimiterManager() { return entityLimiterManager; }
    public MobAIManager getMobAIManager() { return mobAIManager; }
    public MergeManager getMergeManager() { return mergeManager; }
    public ChunkThrottleManager getChunkThrottleManager() { return chunkThrottleManager; }
    public LagResponseManager getLagResponseManager() { return lagResponseManager; }
    public PacketOptManager getPacketOptManager() { return packetOptManager; }
    public TaskSpreadManager getTaskSpreadManager() { return taskSpreadManager; }
}
