package com.wnteam.mlanticheat.ml;

public final class Standardizer {

    private static final double VARIANCE_FLOOR = 1.0E-4;
    private static final double CLIP = 4.0;

    private final int dimension;
    private final double[] mean;
    private final double[] m2;
    private long count;

    public Standardizer(int dimension) {
        this.dimension = dimension;
        this.mean = new double[dimension];
        this.m2 = new double[dimension];
    }

    public synchronized void update(double[] features) {
        count++;
        for (int i = 0; i < dimension; i++) {
            double delta = features[i] - mean[i];
            mean[i] += delta / count;
            m2[i] += delta * (features[i] - mean[i]);
        }
    }

    public synchronized double[] transform(double[] features) {
        double[] out = new double[dimension];
        if (count < 30) {
            for (int i = 0; i < dimension; i++) {
                out[i] = features[i] * 2.0 - 1.0;
            }
            return out;
        }
        for (int i = 0; i < dimension; i++) {
            double variance = Math.max(VARIANCE_FLOOR, m2[i] / count);
            double z = (features[i] - mean[i]) / Math.sqrt(variance);
            out[i] = Math.max(-CLIP, Math.min(CLIP, z));
        }
        return out;
    }

    public synchronized long getCount() {
        return count;
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
