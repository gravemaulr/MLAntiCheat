package com.wnteam.mlanticheat.ml;

public final class LogisticModel {

    private final int dimension;
    private final double[] weights;
    private final double[] momentum;
    private double bias;
    private double learningRate;
    private double l2;

    public LogisticModel(int dimension, double learningRate, double l2) {
        this.dimension = dimension;
        this.weights = new double[dimension];
        this.momentum = new double[dimension];
        this.learningRate = learningRate;
        this.l2 = l2;
        this.bias = -2.0;
    }

    public synchronized double predict(double[] standardized) {
        double sum = bias;
        for (int i = 0; i < dimension; i++) {
            sum += weights[i] * standardized[i];
        }
        return 1.0 / (1.0 + Math.exp(-Math.max(-30.0, Math.min(30.0, sum))));
    }

    public synchronized void train(double[] standardized, double label, double weight) {
        double prediction = predict(standardized);
        double error = (prediction - label) * weight;
        for (int i = 0; i < dimension; i++) {
            double gradient = error * standardized[i] + l2 * weights[i];
            momentum[i] = momentum[i] * 0.9 + gradient * 0.1;
            weights[i] -= learningRate * momentum[i];
            weights[i] = Math.max(-12.0, Math.min(12.0, weights[i]));
        }
        bias -= learningRate * error * 0.5;
        bias = Math.max(-8.0, Math.min(8.0, bias));
    }

    public synchronized void setLearningRate(double value) {
        this.learningRate = value;
    }

    public synchronized void setL2(double value) {
        this.l2 = value;
    }

    public int dimension() {
        return dimension;
    }

    public synchronized double[] export() {
        double[] out = new double[dimension + 1];
        System.arraycopy(weights, 0, out, 0, dimension);
        out[dimension] = bias;
        return out;
    }

    public synchronized void load(double[] payload) {
        if (payload == null || payload.length != dimension + 1) {
            return;
        }
        System.arraycopy(payload, 0, weights, 0, dimension);
        bias = payload[dimension];
    }

    public synchronized double[] weightSnapshot() {
        return weights.clone();
    }
}
