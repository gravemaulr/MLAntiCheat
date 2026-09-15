package com.wnteam.mlanticheat.ml;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TrainingManager {

    private static final int RECENT_CAPACITY = 64;

    private final EnsembleModel model;
    private final AnomalyDetector baseline;
    private final BalancedDataset dataset;
    private final Map<UUID, Double> labels = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<double[]>> recent = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final AtomicLong sessionSamples = new AtomicLong();
    private final AtomicLong cleanSamples = new AtomicLong();
    private final AtomicLong cheatSamples = new AtomicLong();
    private final AtomicLong feedbackSamples = new AtomicLong();
    private final AtomicLong pairedSamples = new AtomicLong();
    private final BalancedDataset.PairConsumer trainer = this::train;

    private int replayBatch = 24;
    private int replayInterval = 40;
    private long sinceReplay;

    public TrainingManager(EnsembleModel model, AnomalyDetector baseline, BalancedDataset dataset) {
        this.model = model;
        this.baseline = baseline;
        this.dataset = dataset;
    }

    public void configure(int replayBatch, int replayInterval, int capacity) {
        this.replayBatch = Math.max(4, replayBatch);
        this.replayInterval = Math.max(8, replayInterval);
        dataset.setCapacity(capacity);
    }

    public void setLabel(UUID uuid, double label) {
        labels.put(uuid, label);
    }

    public void clearLabel(UUID uuid) {
        labels.remove(uuid);
    }

    public void forget(UUID uuid) {
        labels.remove(uuid);
        recent.remove(uuid);
    }

    public void clearAll() {
        labels.clear();
    }

    public boolean isLabeled(UUID uuid) {
        return labels.containsKey(uuid);
    }

    public Double labelOf(UUID uuid) {
        return labels.get(uuid);
    }

    public int activeSubjects() {
        return labels.size();
    }

    public void trainAutomatic(double[] features, double label) {
        trainAutomatic(features, label, 1.0);
    }

    public void trainAutomatic(double[] features, double label, double weight) {
        submit(features, label, weight);
    }

    public void feed(UUID uuid, double[] features) {
        remember(uuid, features);
        Double label = labels.get(uuid);
        if (label == null) {
            return;
        }
        submit(features, label, 1.0);
        sessionSamples.incrementAndGet();
        if (label >= 0.5) {
            cheatSamples.incrementAndGet();
        } else {
            cleanSamples.incrementAndGet();
        }
    }

    public void feedPassiveBaseline(double[] features) {
        baseline.update(features);
    }

    public int applyFeedback(UUID uuid, double label, double weight) {
        Deque<double[]> buffer = recent.get(uuid);
        if (buffer == null) {
            return 0;
        }
        List<double[]> snapshot;
        synchronized (buffer) {
            snapshot = new ArrayList<>(buffer);
        }
        for (double[] features : snapshot) {
            submit(features, label, weight);
        }
        feedbackSamples.addAndGet(snapshot.size());
        return snapshot.size();
    }

    public int recentSamples(UUID uuid) {
        Deque<double[]> buffer = recent.get(uuid);
        if (buffer == null) {
            return 0;
        }
        synchronized (buffer) {
            return buffer.size();
        }
    }

    public int rebalance() {
        model.reset();
        int pairs = dataset.replay(trainer);
        pairedSamples.set(pairs);
        sinceReplay = 0;
        return pairs;
    }

    public int balancedPairs() {
        return dataset.balanced();
    }

    public int queuedSamples() {
        return dataset.surplus();
    }

    public long getPairedSamples() {
        return pairedSamples.get();
    }

    private void submit(double[] features, double label, double weight) {
        if (label < 0.5) {
            baseline.update(features);
        }
        dataset.add(features, label >= 0.5, weight);
        int pairs = dataset.consume(trainer);
        if (pairs == 0) {
            return;
        }
        pairedSamples.addAndGet(pairs);
        sinceReplay += pairs;
        if (sinceReplay >= replayInterval) {
            sinceReplay = 0;
            replay();
        }
    }

    private void train(BalancedDataset.Sample positive, BalancedDataset.Sample negative) {
        model.trainPair(BalancedDataset.features(positive), positive.weight(),
                BalancedDataset.features(negative), negative.weight());
    }

    private void replay() {
        int pairs = dataset.balanced();
        if (pairs == 0) {
            return;
        }
        int batch = Math.max(1, replayBatch / 2);
        for (int i = 0; i < batch; i++) {
            int index = random.nextInt(pairs);
            BalancedDataset.Sample positive = dataset.positive(index);
            BalancedDataset.Sample negative = dataset.negative(index);
            if (positive == null || negative == null) {
                return;
            }
            train(positive, negative);
        }
    }

    private void remember(UUID uuid, double[] features) {
        Deque<double[]> buffer = recent.computeIfAbsent(uuid, ignored -> new ArrayDeque<>(RECENT_CAPACITY));
        synchronized (buffer) {
            buffer.addLast(features.clone());
            while (buffer.size() > RECENT_CAPACITY) {
                buffer.pollFirst();
            }
        }
    }

    public long getSessionSamples() {
        return sessionSamples.get();
    }

    public long getCleanSamples() {
        return cleanSamples.get();
    }

    public long getCheatSamples() {
        return cheatSamples.get();
    }

    public long getFeedbackSamples() {
        return feedbackSamples.get();
    }

    public int bufferedCheatSamples() {
        return dataset.positiveCount();
    }

    public int bufferedCleanSamples() {
        return dataset.negativeCount();
    }
}
