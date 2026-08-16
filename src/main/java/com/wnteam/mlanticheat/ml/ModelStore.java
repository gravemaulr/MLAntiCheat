package com.wnteam.mlanticheat.ml;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ModelStore {

    private final JavaPlugin plugin;
    private final File modelFile;
    private final File baselineFile;
    private final File modelBackupFile;
    private final File baselineBackupFile;

    public ModelStore(JavaPlugin plugin) {
        this.plugin = plugin;
        File folder = new File(plugin.getDataFolder(), "model");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Unable to create model directory.");
        }
        this.modelFile = new File(folder, "model.yml");
        this.baselineFile = new File(folder, "baseline.yml");
        this.modelBackupFile = new File(folder, "model.yml.backup");
        this.baselineBackupFile = new File(folder, "baseline.yml.backup");
    }

    public EnsembleModel loadOrCreate(int dimension, int hidden, double learningRate, double l2) {
        EnsembleModel model = new EnsembleModel(dimension, hidden, learningRate, l2);
        if (!modelFile.exists()) {
            return model;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(modelFile);
        if (configuration.getInt("dimension", -1) != dimension || configuration.getInt("hidden", -1) != hidden) {
            plugin.getLogger().warning("Stored model shape mismatch, starting from scratch.");
            return model;
        }
        model.getStandardizer().load(toArray(configuration.getDoubleList("standardizer")));
        model.getLogistic().load(toArray(configuration.getDoubleList("logistic")));
        model.getNeural().load(toArray(configuration.getDoubleList("neural")));
        model.restoreCounters(
                configuration.getLong("positive", 0L),
                configuration.getLong("negative", 0L),
                configuration.getDouble("logistic-loss", 0.693),
                configuration.getDouble("neural-loss", 0.693));
        model.restoreMetrics(
                configuration.getLong("true-positives", 0L),
                configuration.getLong("false-positives", 0L),
                configuration.getLong("true-negatives", 0L),
                configuration.getLong("false-negatives", 0L));
        return model;
    }

    public AnomalyDetector loadOrCreateBaseline(int dimension) {
        AnomalyDetector detector = new AnomalyDetector(dimension);
        if (!baselineFile.exists()) {
            return detector;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(baselineFile);
        if (configuration.getInt("dimension", -1) != dimension) {
            plugin.getLogger().warning("Stored baseline shape mismatch, starting from scratch.");
            return detector;
        }
        detector.load(toArray(configuration.getDoubleList("state")));
        return detector;
    }

    public synchronized void save(EnsembleModel model, AnomalyDetector baseline) {
        YamlConfiguration modelConfiguration = new YamlConfiguration();
        modelConfiguration.set("dimension", model.dimension());
        modelConfiguration.set("hidden", model.getNeural().hiddenUnits());
        modelConfiguration.set("positive", model.getPositiveSamples());
        modelConfiguration.set("negative", model.getNegativeSamples());
        modelConfiguration.set("logistic-loss", model.getLogisticLoss());
        modelConfiguration.set("neural-loss", model.getNeuralLoss());
        modelConfiguration.set("true-positives", model.getTruePositives());
        modelConfiguration.set("false-positives", model.getFalsePositives());
        modelConfiguration.set("true-negatives", model.getTrueNegatives());
        modelConfiguration.set("false-negatives", model.getFalseNegatives());
        modelConfiguration.set("standardizer", toList(model.getStandardizer().export()));
        modelConfiguration.set("logistic", toList(model.getLogistic().export()));
        modelConfiguration.set("neural", toList(model.getNeural().export()));

        YamlConfiguration baselineConfiguration = new YamlConfiguration();
        baselineConfiguration.set("dimension", baseline.dimension());
        baselineConfiguration.set("state", toList(baseline.export()));

        try {
            saveWithBackup(modelConfiguration, modelFile, modelBackupFile);
            saveWithBackup(baselineConfiguration, baselineFile, baselineBackupFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to persist model: " + exception.getMessage());
        }
    }


    public synchronized boolean rollback() {
        if (!modelBackupFile.exists() || !baselineBackupFile.exists()) {
            return false;
        }
        File modelCurrentBackup = new File(modelFile.getParentFile(), "model.yml.before-rollback");
        File baselineCurrentBackup = new File(baselineFile.getParentFile(), "baseline.yml.before-rollback");
        try {
            copy(modelFile, modelCurrentBackup);
            copy(baselineFile, baselineCurrentBackup);
            copy(modelBackupFile, modelFile);
            copy(baselineBackupFile, baselineFile);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to rollback model: " + exception.getMessage());
            return false;
        }
    }

    public boolean hasBackup() {
        return modelBackupFile.exists() && baselineBackupFile.exists();
    }

    private void saveWithBackup(YamlConfiguration configuration, File target, File backup) throws IOException {
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        configuration.save(temporary);
        if (target.exists()) {
            copy(target, backup);
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("Unable to replace " + target.getName());
        }
        if (!temporary.renameTo(target)) {
            throw new IOException("Unable to commit " + target.getName());
        }
    }

    private void copy(File source, File target) throws IOException {
        if (!source.exists()) {
            return;
        }
        java.nio.file.Files.copy(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public void reset() {
        if (modelFile.exists() && !modelFile.delete()) {
            plugin.getLogger().warning("Unable to delete stored model.");
        }
        if (baselineFile.exists() && !baselineFile.delete()) {
            plugin.getLogger().warning("Unable to delete stored baseline.");
        }
    }

    private List<Double> toList(double[] values) {
        List<Double> list = new ArrayList<>(values.length);
        for (double value : values) {
            list.add(value);
        }
        return list;
    }

    private double[] toArray(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        double[] out = new double[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }
}
