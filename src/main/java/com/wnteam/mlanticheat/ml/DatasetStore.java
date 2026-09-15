package com.wnteam.mlanticheat.ml;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class DatasetStore {

    private static final int MAGIC = 0x4D4C4144;
    private static final int VERSION = 1;
    private static final int MAX_SAMPLES = 2_000_000;

    private final JavaPlugin plugin;
    private final File file;

    public DatasetStore(JavaPlugin plugin) {
        this.plugin = plugin;
        File folder = new File(plugin.getDataFolder(), "model");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Unable to create dataset directory.");
        }
        this.file = new File(folder, "dataset.bin");
    }

    public synchronized void load(BalancedDataset dataset) {
        if (!file.isFile()) {
            return;
        }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                plugin.getLogger().warning("Unsupported dataset format, stored samples ignored.");
                return;
            }
            int dimension = input.readInt();
            if (dimension != dataset.dimension()) {
                plugin.getLogger().warning("Dataset dimension mismatch, stored samples ignored.");
                return;
            }
            int positiveCursor = input.readInt();
            int negativeCursor = input.readInt();
            List<BalancedDataset.Sample> positives = read(input, dimension);
            List<BalancedDataset.Sample> negatives = read(input, dimension);
            dataset.restore(positives, negatives, positiveCursor, negativeCursor);
            plugin.getLogger().info("Loaded " + positives.size() + " cheat and " + negatives.size()
                    + " legit samples, " + dataset.balanced() + " balanced pairs available.");
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to read training dataset: " + exception.getMessage());
        }
    }

    public synchronized void save(BalancedDataset dataset) {
        List<BalancedDataset.Sample> positives = dataset.snapshot(true);
        List<BalancedDataset.Sample> negatives = dataset.snapshot(false);
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(dataset.dimension());
            output.writeInt(dataset.positiveCursor());
            output.writeInt(dataset.negativeCursor());
            write(output, positives);
            write(output, negatives);
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to write training dataset: " + exception.getMessage());
            return;
        }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to commit training dataset: " + exception.getMessage());
        }
    }

    private List<BalancedDataset.Sample> read(DataInputStream input, int dimension) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_SAMPLES) {
            throw new IOException("Corrupted sample count: " + count);
        }
        List<BalancedDataset.Sample> samples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            float[] features = new float[dimension];
            for (int j = 0; j < dimension; j++) {
                features[j] = input.readFloat();
            }
            samples.add(new BalancedDataset.Sample(features, input.readFloat()));
        }
        return samples;
    }

    private void write(DataOutputStream output, List<BalancedDataset.Sample> samples) throws IOException {
        output.writeInt(samples.size());
        for (BalancedDataset.Sample sample : samples) {
            for (float value : sample.features()) {
                output.writeFloat(value);
            }
            output.writeFloat(sample.weight());
        }
    }
}
