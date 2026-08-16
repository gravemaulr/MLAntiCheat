package com.wnteam.mlanticheat;

import com.wnteam.mlanticheat.alert.AlertDispatcher;
import com.wnteam.mlanticheat.alert.EvidenceRecorder;
import com.wnteam.mlanticheat.check.CheckManager;
import com.wnteam.mlanticheat.check.TargetScanner;
import com.wnteam.mlanticheat.command.MLACCommand;
import com.wnteam.mlanticheat.config.Settings;
import com.wnteam.mlanticheat.config.TextConfig;
import com.wnteam.mlanticheat.data.NameCache;
import com.wnteam.mlanticheat.data.PlayerDataManager;
import com.wnteam.mlanticheat.data.PlayerStatsStore;
import com.wnteam.mlanticheat.display.TagDisplayManager;
import com.wnteam.mlanticheat.gui.AdminGui;
import com.wnteam.mlanticheat.listener.CombatListener;
import com.wnteam.mlanticheat.listener.ConnectionListener;
import com.wnteam.mlanticheat.listener.PacketRotationListener;
import com.wnteam.mlanticheat.listener.RotationListener;
import com.wnteam.mlanticheat.ml.AnomalyDetector;
import com.wnteam.mlanticheat.ml.AutoTrainingManager;
import com.wnteam.mlanticheat.ml.EnsembleModel;
import com.wnteam.mlanticheat.ml.FeatureExtractor;
import com.wnteam.mlanticheat.ml.ModelStore;
import com.wnteam.mlanticheat.ml.TrainingManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicLong;

public final class MLAntiCheat extends JavaPlugin {
    private final AtomicLong tick = new AtomicLong();
    private Settings settings;
    private PlayerDataManager dataManager;
    private PlayerStatsStore statsStore;
    private NameCache nameCache;
    private EnsembleModel model;
    private AnomalyDetector baseline;
    private ModelStore modelStore;
    private TrainingManager trainingManager;
    private AutoTrainingManager autoTrainingManager;
    private TagDisplayManager tagManager;
    private CheckManager checkManager;
    private TargetScanner targetScanner;
    private PacketRotationListener packetListener;
    private AlertDispatcher alertDispatcher;
    private TextConfig guiConfig;
    private TextConfig messages;
    private AdminGui adminGui;

    @Override
    public void onEnable() {
        migrateLegacyConfig();
        saveDefaultConfig();
        settings = new Settings(getConfig());
        guiConfig = new TextConfig(this, "gui.yml");
        messages = new TextConfig(this, "messages.yml");
        modelStore = new ModelStore(this);
        model = modelStore.loadOrCreate(FeatureExtractor.FEATURE_COUNT, getConfig().getInt("model.hidden-units", 14), settings.learningRate, settings.l2);
        baseline = modelStore.loadOrCreateBaseline(FeatureExtractor.FEATURE_COUNT);
        dataManager = new PlayerDataManager();
        statsStore = new PlayerStatsStore(this);
        nameCache = new NameCache(this);
        trainingManager = new TrainingManager(model, baseline);
        autoTrainingManager = new AutoTrainingManager(this, model, trainingManager);
        EvidenceRecorder evidence = new EvidenceRecorder(this);
        alertDispatcher = new AlertDispatcher(this, evidence, messages);
        checkManager = new CheckManager(this, dataManager, model, trainingManager, alertDispatcher, settings);
        tagManager = new TagDisplayManager(this, dataManager, settings, messages);
        targetScanner = new TargetScanner(this, checkManager, settings);
        applyConfig();

        adminGui = new AdminGui(this, dataManager, statsStore, trainingManager, guiConfig, messages);
        AdminGui gui = adminGui;
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new ConnectionListener(this, dataManager, statsStore, tagManager, trainingManager, nameCache), this);
        pm.registerEvents(new RotationListener(this, dataManager, checkManager), this);
        pm.registerEvents(new CombatListener(this, dataManager, checkManager), this);
        pm.registerEvents(gui, this);
        if (pm.getPlugin("packetevents") != null || pm.getPlugin("PacketEvents") != null) {
            try { packetListener = new PacketRotationListener(this, dataManager, checkManager); packetListener.register(); }
            catch (Throwable error) { getLogger().warning("PacketEvents hook failed: " + error.getMessage()); }
        }

        MLACCommand handler = new MLACCommand(this, dataManager, statsStore, trainingManager, model, nameCache, gui, tagManager, messages);
        PluginCommand command = getCommand("mlac");
        if (command != null) { command.setExecutor(handler); command.setTabCompleter(handler); }
        tagManager.start();
        targetScanner.start();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            tick.incrementAndGet();
            if (tick.get() % 20 == 0) checkManager.setCachedTps(Bukkit.getTPS()[0]);
        }, 1, 1);
        long autosave = Math.max(1, getConfig().getLong("storage.autosave-minutes", 5)) * 1200L;
        Bukkit.getScheduler().runTaskTimer(this, this::saveAll, autosave, autosave);
        Bukkit.getScheduler().runTaskTimer(this, autoTrainingManager::runDaily, 1200L, 72000L);
        for (Player player : Bukkit.getOnlinePlayers()) { dataManager.get(player); nameCache.remember(player); tagManager.attach(player); }
        getLogger().info("MLAntiCheat enabled in " + (settings.shadowMode ? "shadow" : "active") + " mode");
    }

    private void migrateLegacyConfig() {
        File current = new File(getDataFolder(), "config.yml");
        if (!current.isFile()) return;
        org.bukkit.configuration.file.YamlConfiguration legacy = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(current);
        if (!legacy.contains("checks") && !legacy.contains("ml")) return;
        File backup = new File(getDataFolder(), "config-legacy-" + System.currentTimeMillis() + ".yml");
        try {
            Files.copy(current.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.delete(current.toPath());
            getLogger().info("Legacy configuration backed up as " + backup.getName());
        } catch (IOException exception) {
            getLogger().warning("Could not migrate legacy configuration: " + exception.getMessage());
        }
    }

    public void applyConfig() {
        settings = new Settings(getConfig());
        model.setThresholds(getConfig().getLong("training.minimum-cheat-samples", 400), getConfig().getLong("training.minimum-legit-samples", 400));
        model.setHyperParameters(settings.learningRate, settings.l2);
        model.setHoldoutRatio(settings.holdoutRatio);
        trainingManager.configure(getConfig().getInt("training.replay-batch", 24), getConfig().getInt("training.replay-interval", 40));
        checkManager.setSettings(settings); tagManager.setSettings(settings); targetScanner.setSettings(settings);
    }

    public void reloadRuntime() { reloadConfig(); guiConfig.reload(); messages.reload(); applyConfig(); adminGui.reload(); tagManager.restart(); targetScanner.restart(); }
    private void saveAll() {
        for (Player player : Bukkit.getOnlinePlayers()) statsStore.save(player.getName(), dataManager.get(player));
        statsStore.flush(); modelStore.save(model, baseline); nameCache.save(); autoTrainingManager.save();
    }
    @Override public void onDisable() {
        if (targetScanner != null) targetScanner.shutdown();
        if (packetListener != null) packetListener.shutdown();
        if (tagManager != null) tagManager.shutdown();
        if (statsStore != null) saveAll();
        if (dataManager != null) dataManager.clear();
    }
    public long currentTick() { return tick.get(); }
    public Settings getSettings() { return settings; }
    public AlertDispatcher getAlertDispatcher() { return alertDispatcher; }
    public AutoTrainingManager getAutoTrainingManager() { return autoTrainingManager; }
    public CheckManager getCheckManager() { return checkManager; }
    public PlayerDataManager getDataManager() { return dataManager; }
    public TargetScanner getTargetScanner() { return targetScanner; }
    public PacketRotationListener getPacketListener() { return packetListener; }
}
