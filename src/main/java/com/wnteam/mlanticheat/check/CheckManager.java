package com.wnteam.mlanticheat.check;

import com.wnteam.mlanticheat.MLAntiCheat;
import com.wnteam.mlanticheat.alert.AlertDispatcher;
import com.wnteam.mlanticheat.config.Settings;
import com.wnteam.mlanticheat.data.PlayerData;
import com.wnteam.mlanticheat.data.PlayerDataManager;
import com.wnteam.mlanticheat.ml.EnsembleModel;
import com.wnteam.mlanticheat.ml.FeatureExtractor;
import com.wnteam.mlanticheat.ml.MLScores;
import com.wnteam.mlanticheat.ml.TrainingManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.UUID;

public final class CheckManager {
    private final MLAntiCheat plugin;
    private final PlayerDataManager dataManager;
    private final EnsembleModel model;
    private final TrainingManager training;
    private final AlertDispatcher alerts;
    private volatile Settings settings;
    private volatile double tps = 20.0;

    public CheckManager(MLAntiCheat plugin, PlayerDataManager dataManager, EnsembleModel model, TrainingManager training, AlertDispatcher alerts, Settings settings) {
        this.plugin = plugin; this.dataManager = dataManager; this.model = model; this.training = training; this.alerts = alerts; this.settings = settings;
    }
    public void setSettings(Settings settings) { this.settings = settings; }
    public void setCachedTps(double value) { tps = value; }
    public void handleRotation(Player player, float yaw, float pitch, float absolutePitch, long tick) {
        Settings config = settings; if (isExempt(player)) return;
        PlayerData data = dataManager.get(player); data.addRotation(yaw, pitch, tick);
        if (!data.shouldEvaluate(config.evaluationIntervalMs) || data.rotationCount() < config.minWindowSize) return;
        evaluate(player, data, FeatureExtractor.analyze(data, config.combatWindowMs));
    }
    public void handleAttack(Player attacker, LivingEntity target) {
        Settings config = settings; if (isExempt(attacker)) return;
        PlayerData data = dataManager.get(attacker); Location eye = attacker.getEyeLocation();
        Vector direction = target.getEyeLocation().toVector().subtract(eye.toVector());
        double angle = direction.lengthSquared() < 0.0025 ? 0.0 : Math.toDegrees(eye.getDirection().angle(direction.normalize()));
        data.recordAttack(angle, target.getUniqueId(), boxDistance(eye, target.getBoundingBox()), false, target.getName());
        evaluate(attacker, data, FeatureExtractor.analyze(data, config.combatWindowMs));
    }
    public void handleTargetVisible(Player player) { if (!isExempt(player)) dataManager.get(player).markEnemyVisible(); }
    private void evaluate(Player player, PlayerData data, FeatureExtractor.Analysis analysis) {
        if (!analysis.combat()) return;
        Settings config = settings; double[] features = analysis.toFeatures(); MLScores prediction = MLScores.evaluate(model, features);
        double reduction = config.correction(player.getPing(), tps); MLScores corrected = multiply(prediction, 1.0 - reduction);
        MLScores scores = corrected.smooth(data.mlScores(), config.smoothing);
        data.updateScores(scores, prediction.combined(), player.getPing(), tps);
        training.feed(player.getUniqueId(), features);
        plugin.getAutoTrainingManager().observe(player.getUniqueId(), features, scores.combined(), data.getConfirmations(), true);
        alerts.handle(player, data, config);
    }
    private MLScores multiply(MLScores value, double factor) { double[] score = value.values(); return new MLScores(score[0] * factor, score[1] * factor, score[2] * factor, score[3] * factor, score[4] * factor); }
    private double boxDistance(Location eye, BoundingBox box) { double x = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX())); double y = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY())); double z = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ())); return Math.sqrt(Math.pow(eye.getX() - x, 2) + Math.pow(eye.getY() - y, 2) + Math.pow(eye.getZ() - z, 2)); }
    public void forget(UUID uuid) { alerts.forget(uuid); }
    private boolean isExempt(Player player) { GameMode mode = player.getGameMode(); return player.hasPermission("mlac.bypass") || player.isDead() || mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR; }
}
