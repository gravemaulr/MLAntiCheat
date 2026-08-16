package com.wnteam.mlanticheat.ml;

public final class AnomalyDetector {

    private static final double VARIANCE_FLOOR = 2.0E-3;
    private static final double MAX_Z = 6.0;

    private final int dimension;
    private final double[] mean;
    private final double[] m2;
    private long count;
    private long minSamples = 6000;

    public AnomalyDetector(int dimension) {
        this.dimension = dimension;
        this.mean = new double[dimension];
        this.m2 = new double[dimension];
    }

    public void setMinSamples(long value) {
        this.minSamples = value;
    }

    public synchronized void update(double[] features) {
        count++;
        for (int i = 0; i < dimension; i++) {
            double delta = features[i] - mean[i];
            mean[i] += delta / count;
            m2[i] += delta * (features[i] - mean[i]);
        }
    }

    public synchronized double score(double[] features) {
        if (count < minSamples) {
            return 0.0;
        }
        double accumulator = 0.0;
        for (int i = 0; i < dimension; i++) {
            double variance = Math.max(VARIANCE_FLOOR, m2[i] / count);
            double z = Math.abs(features[i] - mean[i]) / Math.sqrt(variance);
            z = Math.min(MAX_Z, z);
            accumulator += z * z;
        }
        double result = Math.sqrt(accumulator / dimension);
        return Double.isFinite(result) ? result : 0.0;
    }

    public synchronized boolean isReady() {
        return count >= minSamples;
    }

    public synchronized long getCount() {
        return count;
    }

    public int dimension() {
        return dimension;
    }

    public synchronized double[] export() {
        double[] out = new double[dimension * 2 + 1];
        out[0] = count;
        System.arraycopy(mean, 0, out, 1, dimension);
        System.arraycopy(m2, 0, out, dimension + 1, dimension);
        return out;
    }

    public synchronized void load(double[] payload) {
        if (payload == null || payload.length != dimension * 2 + 1) {
            return;
        }
        count = (long) payload[0];
        System.arraycopy(payload, 1, mean, 0, dimension);
        System.arraycopy(payload, dimension + 1, m2, 0, dimension);
    }
}
