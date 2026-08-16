package com.wnteam.mlanticheat.ml;

import java.util.Random;

public final class NeuralModel {

    private final int dimension;
    private final int hidden;
    private final double[][] w1;
    private final double[] b1;
    private final double[] w2;
    private double b2;
    private double learningRate;
    private double l2;

    public NeuralModel(int dimension, int hidden, double learningRate, double l2) {
        this.dimension = dimension;
        this.hidden = hidden;
        this.w1 = new double[hidden][dimension];
        this.b1 = new double[hidden];
        this.w2 = new double[hidden];
        this.learningRate = learningRate;
        this.l2 = l2;
        Random random = new Random(918273645L);
        double scale = Math.sqrt(2.0 / dimension);
        for (int h = 0; h < hidden; h++) {
            for (int i = 0; i < dimension; i++) {
                w1[h][i] = random.nextGaussian() * scale;
            }
            w2[h] = random.nextGaussian() * Math.sqrt(1.0 / hidden);
        }
        this.b2 = -2.0;
    }

    public synchronized double predict(double[] standardized) {
        double[] activations = new double[hidden];
        return forward(standardized, activations);
    }

    private double forward(double[] input, double[] activations) {
        double sum = b2;
        for (int h = 0; h < hidden; h++) {
            double z = b1[h];
            for (int i = 0; i < dimension; i++) {
                z += w1[h][i] * input[i];
            }
            activations[h] = Math.tanh(z);
            sum += w2[h] * activations[h];
        }
        return 1.0 / (1.0 + Math.exp(-Math.max(-30.0, Math.min(30.0, sum))));
    }

    public synchronized void train(double[] standardized, double label, double weight) {
        double[] activations = new double[hidden];
        double prediction = forward(standardized, activations);
        double outputError = (prediction - label) * weight;
        for (int h = 0; h < hidden; h++) {
            double hiddenError = outputError * w2[h] * (1.0 - activations[h] * activations[h]);
            w2[h] -= learningRate * (outputError * activations[h] + l2 * w2[h]);
            w2[h] = Math.max(-10.0, Math.min(10.0, w2[h]));
            for (int i = 0; i < dimension; i++) {
                w1[h][i] -= learningRate * (hiddenError * standardized[i] + l2 * w1[h][i]);
                w1[h][i] = Math.max(-10.0, Math.min(10.0, w1[h][i]));
            }
            b1[h] -= learningRate * hiddenError;
            b1[h] = Math.max(-6.0, Math.min(6.0, b1[h]));
        }
        b2 -= learningRate * outputError * 0.5;
        b2 = Math.max(-8.0, Math.min(8.0, b2));
    }

    public synchronized void setLearningRate(double value) {
        this.learningRate = value;
    }

    public synchronized void setL2(double value) {
        this.l2 = value;
    }

    public int hiddenUnits() {
        return hidden;
    }

    public synchronized double[] export() {
        double[] out = new double[hidden * dimension + hidden + hidden + 1];
        int cursor = 0;
        for (int h = 0; h < hidden; h++) {
            System.arraycopy(w1[h], 0, out, cursor, dimension);
            cursor += dimension;
        }
        System.arraycopy(b1, 0, out, cursor, hidden);
        cursor += hidden;
        System.arraycopy(w2, 0, out, cursor, hidden);
        cursor += hidden;
        out[cursor] = b2;
        return out;
    }

    public synchronized void load(double[] payload) {
        int expected = hidden * dimension + hidden + hidden + 1;
        if (payload == null || payload.length != expected) {
            return;
        }
        int cursor = 0;
        for (int h = 0; h < hidden; h++) {
            System.arraycopy(payload, cursor, w1[h], 0, dimension);
            cursor += dimension;
        }
        System.arraycopy(payload, cursor, b1, 0, hidden);
        cursor += hidden;
        System.arraycopy(payload, cursor, w2, 0, hidden);
        cursor += hidden;
        b2 = payload[cursor];
    }
}
