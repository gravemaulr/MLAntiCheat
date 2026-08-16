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

    private static final int BUFFER_CAPACITY = 6000;
    private static final int RECENT_CAPACITY = 64;

    private final EnsembleModel model;
    private final AnomalyDetector baseline;
    private final Map<UUID, Double> labels = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<double[]>> recent = new ConcurrentHashMap<>();
    private final Deque<double[]> cheatBuffer = new ArrayDeque<>(BUFFER_CAPACITY);
    private final Deque<double[]> cleanBuffer = new ArrayDeque<>(BUFFER_CAPACITY);
    private final Random random = new Random();
    private final AtomicLong sessionSamples = new AtomicLong();
    private final AtomicLong cleanSamples = new AtomicLong();
    private final AtomicLong cheatSamples = new AtomicLong();
    private final AtomicLong feedbackSamples = new AtomicLong();

    private int replayBatch = 24;
    private int replayInterval = 40;
    private long sinceReplay;

    public TrainingManager(EnsembleModel model, AnomalyDetector baseline) {
        this.model = model;
        this.baseline = baseline;
    }

    public void configure(int replayBatch, int replayInterval) {
        this.replayBatch = Math.max(4, replayBatch);
        this.replayInterval = Math.max(8, replayInterval);
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

    public synchronized void trainAutomatic(double[] features, double label) {
        trainAutomatic(features, label, 1.0);
    }

    public synchronized void trainAutomatic(double[] features, double label, double weight) {
        model.train(features, label, weight);
        store(features, label >= 0.5);
        if (label < 0.5) {
            baseline.update(features);
        }
    }

    public void feed(UUID uuid, double[] features) {
        remember(uuid, features);
        Double label = labels.get(uuid);
        if (label == null) {
            return;
        }
        model.train(features, label);
        store(features, label >= 0.5);
        sessionSamples.incrementAndGet();
        if (label >= 0.5) {
            cheatSamples.incrementAndGet();
        } else {
            cleanSamples.incrementAndGet();
            baseline.update(features);
        }
        if (++sinceReplay >= replayInterval) {
            sinceReplay = 0;
            replay();
        }
    }

    public void feedPassiveBaseline(double[] features) {
        baseline.update(features);
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
            model.train(features, label, weight);
            store(features, label >= 0.5);
            if (label < 0.5) {
                baseline.update(features);
            }
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

    private void store(double[] features, boolean cheater) {
        Deque<double[]> buffer = cheater ? cheatBuffer : cleanBuffer;
        synchronized (buffer) {
            buffer.addLast(features.clone());
            while (buffer.size() > BUFFER_CAPACITY) {
                buffer.pollFirst();
            }
        }
    }

    private void replay() {
        double[][] cheats = drain(cheatBuffer);
        double[][] cleans = drain(cleanBuffer);
        if (cheats.length == 0 || cleans.length == 0) {
            return;
        }
        int half = Math.max(1, replayBatch / 2);
        for (int i = 0; i < half; i++) {
            model.train(cheats[random.nextInt(cheats.length)], 1.0);
            model.train(cleans[random.nextInt(cleans.length)], 0.0);
        }
    }

    private double[][] drain(Deque<double[]> buffer) {
        synchronized (buffer) {
            return buffer.toArray(new double[0][]);
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
        synchronized (cheatBuffer) {
            return cheatBuffer.size();
        }
    }

    public int bufferedCleanSamples() {
        synchronized (cleanBuffer) {
            return cleanBuffer.size();
        }
    }
}
