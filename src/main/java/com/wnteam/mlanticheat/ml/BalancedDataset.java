package com.wnteam.mlanticheat.ml;

import java.util.ArrayList;
import java.util.List;

public final class BalancedDataset {

    public interface PairConsumer {
        void accept(Sample positive, Sample negative);
    }

    public record Sample(float[] features, float weight) {
    }

    private static final int MINIMUM_CAPACITY = 256;

    private final int dimension;
    private final List<Sample> positives = new ArrayList<>();
    private final List<Sample> negatives = new ArrayList<>();
    private int capacity;
    private int positiveCursor;
    private int negativeCursor;

    public BalancedDataset(int dimension, int capacity) {
        this.dimension = dimension;
        this.capacity = Math.max(MINIMUM_CAPACITY, capacity);
    }

    public int dimension() {
        return dimension;
    }

    public synchronized void setCapacity(int value) {
        capacity = Math.max(MINIMUM_CAPACITY, value);
        trim(positives, true);
        trim(negatives, false);
    }

    public synchronized void add(double[] features, boolean positive, double weight) {
        if (features == null || features.length != dimension) {
            return;
        }
        float[] copy = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            copy[i] = Double.isFinite(features[i]) ? (float) features[i] : 0.0F;
        }
        Sample sample = new Sample(copy, (float) Math.max(0.1, Math.min(8.0, weight)));
        if (positive) {
            positives.add(sample);
            trim(positives, true);
        } else {
            negatives.add(sample);
            trim(negatives, false);
        }
    }

    public synchronized int consume(PairConsumer consumer) {
        int pairs = 0;
        while (positiveCursor < positives.size() && negativeCursor < negatives.size()) {
            consumer.accept(positives.get(positiveCursor++), negatives.get(negativeCursor++));
            pairs++;
        }
        return pairs;
    }

    public synchronized int replay(PairConsumer consumer) {
        int pairs = Math.min(positives.size(), negatives.size());
        for (int i = 0; i < pairs; i++) {
            consumer.accept(positives.get(i), negatives.get(i));
        }
        positiveCursor = pairs;
        negativeCursor = pairs;
        return pairs;
    }

    public synchronized Sample positive(int index) {
        return index >= 0 && index < positives.size() ? positives.get(index) : null;
    }

    public synchronized Sample negative(int index) {
        return index >= 0 && index < negatives.size() ? negatives.get(index) : null;
    }

    public synchronized int balanced() {
        return Math.min(positives.size(), negatives.size());
    }

    public synchronized int positiveCount() {
        return positives.size();
    }

    public synchronized int negativeCount() {
        return negatives.size();
    }

    public synchronized int surplus() {
        return Math.abs(positives.size() - negatives.size());
    }

    public synchronized int positiveCursor() {
        return positiveCursor;
    }

    public synchronized int negativeCursor() {
        return negativeCursor;
    }

    public synchronized void clear() {
        positives.clear();
        negatives.clear();
        positiveCursor = 0;
        negativeCursor = 0;
    }

    public synchronized List<Sample> snapshot(boolean positive) {
        return List.copyOf(positive ? positives : negatives);
    }

    public synchronized void restore(List<Sample> storedPositives, List<Sample> storedNegatives,
                                     int storedPositiveCursor, int storedNegativeCursor) {
        positives.clear();
        negatives.clear();
        for (Sample sample : storedPositives) {
            if (valid(sample)) {
                positives.add(sample);
            }
        }
        for (Sample sample : storedNegatives) {
            if (valid(sample)) {
                negatives.add(sample);
            }
        }
        trim(positives, true);
        trim(negatives, false);
        positiveCursor = Math.max(0, Math.min(storedPositiveCursor, positives.size()));
        negativeCursor = Math.max(0, Math.min(storedNegativeCursor, negatives.size()));
    }

    public static double[] features(Sample sample) {
        float[] source = sample.features();
        double[] out = new double[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    private boolean valid(Sample sample) {
        return sample != null && sample.features() != null && sample.features().length == dimension;
    }

    private void trim(List<Sample> samples, boolean positive) {
        int excess = samples.size() - capacity;
        if (excess <= 0) {
            return;
        }
        int chunk = Math.min(samples.size(), Math.max(excess, capacity / 10));
        samples.subList(0, chunk).clear();
        if (positive) {
            positiveCursor = Math.max(0, positiveCursor - chunk);
        } else {
            negativeCursor = Math.max(0, negativeCursor - chunk);
        }
    }
}
